package com.tjclp.xlcr.server.mcp.protocol

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

// JSON-RPC 2.0 protocol models

/**
 * JSON-RPC request model
 */
case class JsonRpcRequest(
  jsonrpc: String = "2.0",
  id: Option[String],
  method: String,
  params: Option[Json]
)

object JsonRpcRequest {
  implicit val encoder: Encoder[JsonRpcRequest] = deriveEncoder
  implicit val decoder: Decoder[JsonRpcRequest] = deriveDecoder
}

/**
 * JSON-RPC response model
 */
case class JsonRpcResponse[T](
  jsonrpc: String = "2.0",
  id: Option[String],
  result: Option[T] = None,
  error: Option[JsonRpcError] = None
)

object JsonRpcResponse {
  implicit def encoder[T: Encoder]: Encoder[JsonRpcResponse[T]] = deriveEncoder
  implicit def decoder[T: Decoder]: Decoder[JsonRpcResponse[T]] = deriveDecoder
  
  def success[T](id: Option[String], result: T)(implicit encoder: Encoder[T]): JsonRpcResponse[Json] =
    JsonRpcResponse(id = id, result = Some(encoder(result)))
    
  def error(id: Option[String], error: JsonRpcError): JsonRpcResponse[Json] =
    JsonRpcResponse(id = id, error = Some(error))
}

/**
 * JSON-RPC error model
 */
case class JsonRpcError(
  code: Int,
  message: String,
  data: Option[Json] = None
)

object JsonRpcError {
  implicit val encoder: Encoder[JsonRpcError] = deriveEncoder
  implicit val decoder: Decoder[JsonRpcError] = deriveDecoder
  
  // Standard error codes
  val ParseError: JsonRpcError = JsonRpcError(-32700, "Parse error")
  val InvalidRequest: JsonRpcError = JsonRpcError(-32600, "Invalid request")
  val MethodNotFound: JsonRpcError = JsonRpcError(-32601, "Method not found")
  val InvalidParams: JsonRpcError = JsonRpcError(-32602, "Invalid params")
  val InternalError: JsonRpcError = JsonRpcError(-32603, "Internal error")
  
  // Custom error codes
  def sessionNotFound(sessionId: String): JsonRpcError = 
    JsonRpcError(-32000, s"Session not found: $sessionId")
  def fileNotFound(path: String): JsonRpcError =
    JsonRpcError(-32001, s"File not found: $path")
  def invalidOperation(message: String): JsonRpcError =
    JsonRpcError(-32002, s"Invalid operation: $message")
}