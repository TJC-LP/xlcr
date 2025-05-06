package com.tjclp.xlcr
package pipeline
import scala.collection.concurrent.TrieMap

import pipeline.steps._
import splitters.SplitStrategy._
import types.MimeType

/**
 * A *very* small registry that lets the CLI (or other dynamic layers) look up a `PipelineStep`
 * implementation by name.
 *
 * The API is intentionally un‑typed – callers are expected to cast the step to the appropriate
 * `PipelineStep[A, B]` once they know the concrete types involved (which is usually inferred when
 * steps are chained statically).
 *
 * More sophisticated type‑safe discovery mechanisms (e.g. using dependent types or shapeless) can
 * be built on top later, but for now a simple Map keeps the implementation lightweight.
 */
object StepRegistry {

  private val steps = TrieMap.empty[String, PipelineStep[_, _]]

  def register(name: String, step: PipelineStep[_, _]): Unit =
    steps.update(name, step)

  def get(name: String): Option[PipelineStep[_, _]] = steps.get(name)

  /* -------------------------------------------------------------------- */
  /* Built‑ins                                                            */
  /* -------------------------------------------------------------------- */

  // We eagerly register a few common helpers so the CLI DSL can just say
  //   "split|toPdf|toPng" without needing extra boot‑strapping code.

  register("split", SplitStep())
  register("splitRecursive", SplitStep(recursive = true))
  register("splitByPage", SplitStep(Some(Page)))
  register("splitBySheet", SplitStep(Some(Sheet)))
  register("splitBySlide", SplitStep(Some(Slide)))

  register("toPdf", ConvertStep(MimeType.ApplicationPdf))
  register("toPng", ConvertStep(MimeType.ImagePng))
  register("toJpeg", ConvertStep(MimeType.ImageJpeg))
  register("toSvg", ConvertStep(MimeType.ImageSvgXml))

  // Tika extraction shortcuts
  register("extractText", ExtractTextStep)
  register("extractXml", ExtractXmlStep)
}
