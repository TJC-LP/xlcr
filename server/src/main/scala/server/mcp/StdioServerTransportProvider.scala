package com.tjclp.xlcr.server.mcp

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.spec.{McpSchema, McpServerSession, McpServerTransportProvider}
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import java.util.{Map => JMap}
import java.util.concurrent.Executors
import scala.util.control.Breaks._

/**
 * Implementation of a server transport provider that uses standard input/output for communication
 * This is useful for command-line integration with LLMs.
 */
class StdioServerTransportProvider(private val objectMapper: ObjectMapper) extends McpServerTransportProvider {

  private val logger = LoggerFactory.getLogger(getClass)
  private val executor = Executors.newSingleThreadExecutor()
  
  // The transport is used for connecting to the client
  def createTransport(): io.modelcontextprotocol.spec.ServerMcpTransport = {
    logger.info("Creating STDIO transport for MCP server")
    
    // Create a server transport interface implementation
    new io.modelcontextprotocol.spec.ServerMcpTransport {
      private val reader = new BufferedReader(new InputStreamReader(System.in))
      private val writer = new PrintWriter(System.out, true) // auto-flush
      private var messageConsumer: java.util.function.Consumer[String] = _
      
      // Thread for reading from stdin
      private val readerThread = new Thread(() => {
        try {
          logger.info("STDIO transport reader thread started")
          breakable {
            while (true) {
              val line = reader.readLine()
              
              if (line == null) {
                // End of stream reached, exit
                logger.info("STDIO input stream closed, exiting reader thread")
                break()
              }
              
              try {
                // Deliver to consumer if registered
                if (messageConsumer != null) {
                  messageConsumer.accept(line)
                } else {
                  logger.warn("Received message but no consumer registered")
                }
              } catch {
                case e: Exception =>
                  logger.error(s"Error processing input: ${e.getMessage}", e)
              }
            }
          }
        } catch {
          case e: Exception =>
            logger.error(s"Error in STDIO reader thread: ${e.getMessage}", e)
        }
      })
      
      // Start the reader thread in daemon mode
      readerThread.setDaemon(true)
      readerThread.start()
      
      // Implementation of the mandatory ServerMcpTransport methods
      override def sendMessage(message: McpSchema.JSONRPCMessage): Mono[Void] = {
        try {
          val jsonStr = objectMapper.writeValueAsString(message)
          writer.println(jsonStr)
          Mono.empty()
        } catch {
          case e: Exception =>
            logger.error(s"Error sending message: ${e.getMessage}", e)
            Mono.error(e)
        }
      }
      
      override def unmarshalFrom[T](value: Object, typeRef: TypeReference[T]): T = {
        objectMapper.convertValue(value, typeRef)
      }
      
      override def close(): Unit = {
        logger.info("Closing STDIO transport")
        // Nothing special to close for STDIO
      }
      
      override def closeGracefully(): Mono[Void] = {
        logger.info("Gracefully closing STDIO transport")
        // Nothing special to close for STDIO
        Mono.empty()
      }
    }
  }
  
  // Required implementation of McpServerTransportProvider methods
  override def closeGracefully(): Mono[Void] = {
    logger.info("Gracefully closing StdioServerTransportProvider")
    Mono.empty()
  }
  
  override def notifyClients(method: String, params: JMap[String, Object]): Mono[Void] = {
    // No-op for STDIO provider since we have a single client
    Mono.empty()
  }
  
  override def setSessionFactory(sessionFactory: McpServerSession.Factory): Unit = {
    // No-op for this implementation
  }
}