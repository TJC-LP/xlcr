package com.tjclp.xlcr.server.mcp

import org.slf4j.LoggerFactory

/**
 * Main entry point for the XLCR MCP Server
 */
object Main {
  private val logger = LoggerFactory.getLogger(getClass)
  
  def main(args: Array[String]): Unit = {
    // Parse command line arguments
    val mode = args.headOption.getOrElse("--stdio")
    val port = args.drop(1).headOption.map(_.toInt).getOrElse(3000)
    
    logger.info(s"Starting XLCR MCP Server in mode: $mode")
    
    mode match {
      case "--stdio" => runStdioServer()
      case "--sse" => runSseServer(port)
      case _ => 
        logger.error(s"Unknown mode: $mode")
        System.exit(1)
    }
  }
  
  /**
   * Run the server in STDIO mode (for direct integration with LLMs)
   */
  private def runStdioServer(): Unit = {
    logger.info("Starting XLCR MCP Server in STDIO mode")
    
    try {
      val server = XlcrMcpServer.createServer()
      logger.info("Server running, waiting for STDIO messages")
      
      // Keep server running until process is terminated
      // Avoid using thread.sleep so we respond promptly to client messages
      val lock = new Object
      lock.synchronized {
        lock.wait()
      }
    } catch {
      case e: Exception =>
        logger.error(s"Server error: ${e.getMessage}", e)
        System.exit(1)
    }
  }
  
  /**
   * Run the server in SSE mode (for web-based integration)
   */
  private def runSseServer(port: Int): Unit = {
    logger.info(s"Starting XLCR MCP Server in SSE mode on port $port")
    logger.warn("SSE server implementation not yet completed")
    
    // Placeholder for HTTP SSE server 
    // For now, we'll just keep the process alive
    val lock = new Object
    lock.synchronized {
      lock.wait()
    }
  }
}