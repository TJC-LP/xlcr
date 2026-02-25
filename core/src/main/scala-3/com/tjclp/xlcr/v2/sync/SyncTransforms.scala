package com.tjclp.xlcr.v2.sync

import java.lang.reflect.Method

import scala.util.Try

import zio.{ Chunk, Runtime, Unsafe, ZIO }

import com.tjclp.xlcr.v2.core.XlcrTransforms
import com.tjclp.xlcr.v2.transform.{ ResourceError, TransformError, UnsupportedConversion }
import com.tjclp.xlcr.v2.types.{ Content, ConvertOptions, DynamicFragment, Mime }

/** Synchronous facade for JVM-only consumers (for example Spark UDFs). */
object SyncTransforms {

  private val runtime = Runtime.default

  private final case class ReflectBackend(
    name: String,
    moduleClassName: String
  ) {
    lazy val moduleOpt: Option[AnyRef] =
      Try(Class.forName(moduleClassName).getField("MODULE$").get(null).asInstanceOf[AnyRef]).toOption

    private def method(
      methodName: String,
      argTypes: Class[?]*
    ): Option[Method] =
      moduleOpt.flatMap(module => Try(module.getClass.getMethod(methodName, argTypes*)).toOption)

    private val contentClass = classOf[Content[?]]
    private val mimeClass    = classOf[String]
    private val optionsClass = classOf[ConvertOptions]

    lazy val canConvertMethod: Option[Method] = method("canConvert", mimeClass, mimeClass)
    lazy val canSplitMethod: Option[Method]   = method("canSplit", mimeClass)
    lazy val convertMethod: Option[Method] =
      method("convert", contentClass, mimeClass, optionsClass)
    lazy val splitMethod: Option[Method] =
      method("split", contentClass, optionsClass).orElse(method("split", contentClass))

    def canConvert(from: Mime, to: Mime): Boolean =
      invokeBoolean(canConvertMethod, from, to)

    def canSplit(mime: Mime): Boolean =
      invokeBoolean(canSplitMethod, mime)

    def convert(input: Content[Mime], to: Mime): Option[ZIO[Any, TransformError, Content[Mime]]] =
      invokeEffect[Content[Mime]](convertMethod, input, to, ConvertOptions())

    def split(input: Content[Mime]): Option[ZIO[Any, TransformError, Chunk[DynamicFragment]]] =
      splitMethod match {
        case Some(m) if m.getParameterCount == 2 =>
          invokeEffect[Chunk[DynamicFragment]](Some(m), input, ConvertOptions())
        case Some(m) =>
          invokeEffect[Chunk[DynamicFragment]](Some(m), input)
        case None =>
          None
      }

    private def invokeBoolean(methodOpt: Option[Method], args: AnyRef*): Boolean =
      methodOpt.exists { m =>
        moduleOpt.exists { module =>
          Try(m.invoke(module, args*).asInstanceOf[Boolean]).getOrElse(false)
        }
      }

    private def invokeEffect[A](
      methodOpt: Option[Method],
      args: AnyRef*
    ): Option[ZIO[Any, TransformError, A]] =
      for {
        m      <- methodOpt
        module <- moduleOpt
        effect <- Try(m.invoke(module, args*).asInstanceOf[ZIO[Any, TransformError, A]]).toOption
      } yield effect
  }

  // Optional backends (available only when their modules are on the classpath)
  private val asposeBackend = ReflectBackend(
    name = "AsposeTransforms",
    moduleClassName = "com.tjclp.xlcr.v2.aspose.AsposeTransforms$"
  )

  private val libreofficeBackend = ReflectBackend(
    name = "LibreOfficeTransforms",
    moduleClassName = "com.tjclp.xlcr.v2.libreoffice.LibreOfficeTransforms$"
  )

  private def normalizeMime(raw: String): Mime =
    Mime(raw.toLowerCase(java.util.Locale.ROOT).takeWhile(_ != ';').trim)

  private def unsafeRun[A](effect: ZIO[Any, TransformError, A]): A =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(effect).getOrThrowFiberFailure()
    }

  /** Convert document bytes from one MIME type to another. */
  def convert(input: Array[Byte], inputMime: String, outputMime: String): Array[Byte] = {
    val from    = normalizeMime(inputMime)
    val to      = normalizeMime(outputMime)
    val content = Content(input, from)

    if (from == to) input
    else {
      val asposeEffect =
        asposeBackend.convert(content, to).getOrElse(ZIO.fail(UnsupportedConversion(from, to)))

      val libreofficeEffect = libreofficeBackend
        .convert(content, to)
        .getOrElse(ZIO.fail(UnsupportedConversion(from, to)))

      val effect =
        asposeEffect.catchSome {
          case _: UnsupportedConversion | _: ResourceError =>
            libreofficeEffect
        }.catchSome {
          case _: UnsupportedConversion =>
            XlcrTransforms.convert(content, to)
        }

      unsafeRun(effect).toArray
    }
  }

  /** Extract text-like output from document bytes. */
  def extract(input: Array[Byte], inputMime: String, outputMime: String): String =
    new String(convert(input, inputMime, outputMime), java.nio.charset.StandardCharsets.UTF_8)

  /**
   * Split document bytes into fragments as tuples: (bytes, mime, index, label, total).
   */
  def split(input: Array[Byte], inputMime: String): Seq[(Array[Byte], String, Int, String, Int)] = {
    val mime    = normalizeMime(inputMime)
    val content = Content(input, mime)

    val asposeEffect = asposeBackend
      .split(content)
      .getOrElse(ZIO.fail(UnsupportedConversion(mime, mime)))

    val libreofficeEffect = libreofficeBackend
      .split(content)
      .getOrElse(ZIO.fail(UnsupportedConversion(mime, mime)))

    val effect =
      asposeEffect.catchSome {
        case _: UnsupportedConversion | _: ResourceError =>
          libreofficeEffect
      }.catchSome {
        case _: UnsupportedConversion =>
          XlcrTransforms.split(content)
      }

    val fragments    = unsafeRun(effect)
    val defaultTotal = fragments.length

    fragments.map { fragment =>
      val total = fragment.content.metadata
        .get(Content.MetadataKeys.Total)
        .flatMap(v => Try(v.toInt).toOption)
        .getOrElse(defaultTotal)
      val label = fragment.name
        .orElse(fragment.content.filename)
        .getOrElse(s"part-${fragment.index + 1}")

      (
        fragment.data.toArray,
        fragment.mime.mimeType,
        fragment.index,
        label,
        total
      )
    }
  }

  /** Find the effective implementation name for conversion lineage tracking. */
  def findConversionImpl(inputMime: String, outputMime: String): Option[String] = {
    val from = normalizeMime(inputMime)
    val to   = normalizeMime(outputMime)
    if (from == to) Some("IdentityTransform")
    else if (asposeBackend.canConvert(from, to)) Some(asposeBackend.name)
    else if (libreofficeBackend.canConvert(from, to)) Some(libreofficeBackend.name)
    else if (XlcrTransforms.canConvert(from, to)) Some("XlcrTransforms")
    else None
  }

  /** Find the effective implementation name for splitter lineage tracking. */
  def findSplitterImpl(inputMime: String): Option[String] = {
    val mime = normalizeMime(inputMime)
    if (asposeBackend.canSplit(mime)) Some(asposeBackend.name)
    else if (libreofficeBackend.canSplit(mime)) Some(libreofficeBackend.name)
    else if (XlcrTransforms.canSplit(mime)) Some("XlcrTransforms")
    else None
  }
}
