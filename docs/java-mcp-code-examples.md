Got it. I’ll find code examples and templates that demonstrate how to build an MCP (ModelContext Protocol) server using the official Java SDK, with a focus on Scala interoperability. I’ll prioritize examples that include tool registration, message handling, and server setup, and highlight anything reusable or extensible in a Scala-based environment.

I’ll share the findings with you shortly.

# Building a Model Context Protocol (MCP) Server with the Java SDK (for Scala Developers)

The **Model Context Protocol (MCP)** is an open standard (spearheaded by Anthropic) that lets AI models interact with external tools and data in a uniform way ([Introducing the Model Context Protocol Java SDK](https://spring.io/blog/2025/02/14/mcp-java-sdk-released-2#:~:text=The%20Model%20Context%20Protocol%20,that%20makes%20these%20integrations%20seamless)) ([Introducing the Model Context Protocol Java SDK](https://spring.io/blog/2025/02/14/mcp-java-sdk-released-2#:~:text=The%20MCP%20Java%20SDK%20provides,features%20of%20the%20SDK%20include)). The official **Java SDK** for MCP provides everything needed to implement an MCP **server** – a process that exposes *tools*, *resources*, or *prompts* for AI clients to discover and invoke. As a Scala developer on the JVM, you can leverage this Java SDK directly in your Scala applications. Below we outline how to set up and code an MCP server, with examples of tool registration, request handling, and transport configuration. We also highlight interoperability tips and real-world projects demonstrating MCP servers in action.

## Setup: Adding the MCP Java SDK to a Scala Project

Include the MCP SDK as a dependency in your Scala build (SBT or Maven/Gradle). The core library is published under group ID **`io.modelcontextprotocol.sdk`** and artifact **`mcp`**. For example, in **SBT** you can add:

```scala
libraryDependencies += "io.modelcontextprotocol.sdk" % "mcp" % "<latest-version>"
```

*(Replace `<latest-version>` with the latest SDK version, e.g. `0.8.0`.)* The SDK also provides optional modules for specific transport implementations (discussed below), such as `mcp-spring-webflux` and `mcp-spring-webmvc` for HTTP Server-Sent Events (SSE) transports ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=%3C%21,webflux%3C%2FartifactId%3E%20%3C%2Fdependency)). You may want to import the Bill of Materials (BOM) to ensure version alignment of all MCP artifacts ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=%3CdependencyManagement%3E%20%3Cdependencies%3E%20%3Cdependency%3E%20%3CgroupId%3Eio.modelcontextprotocol.sdk%3C%2FgroupId%3E%20%3CartifactId%3Emcp,dependency)) ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=The%20following%20dependencies%20are%20available,and%20managed%20by%20the%20BOM)), though in SBT you can manage versions manually. Scala 2.12+ and Scala 3 can interoperate with this Java library without issues – Scala will treat Java classes normally, and you can use Scala lambdas to implement Java’s functional interfaces (thanks to SAM conversion). For example, a Java lambda `(exchange, args) -> {...}` can be written as a Scala anonymous function `(exchange, args) => {...}` when calling the Java APIs.

## Initializing an MCP Server (Sync and Async APIs)

The MCP Java SDK supports both synchronous and asynchronous server APIs ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=The%20server%20supports%20both%20synchronous,integration%20in%20different%20application%20contexts)). In most cases, the **synchronous API** (blocking handlers) is simpler to start with. You create an `McpSyncServer` using the `McpServer.sync(...)` builder, configure server metadata and capabilities, then register your tools/resources, and start listening on a chosen transport. For example, the code below creates a basic server (version 1.0.0) that supports tools, resources, prompts, and logging, and uses a STDIO transport (explained later):

```java
// Choose a transport for client-server communication (here, STDIO pipes)
StdioServerTransportProvider transportProvider = 
    new StdioServerTransportProvider(new ObjectMapper());

// Build an MCP server with desired capabilities
McpSyncServer server = McpServer.sync(transportProvider)
    .serverInfo("my-server", "1.0.0")
    .capabilities(ServerCapabilities.builder()
        .resources(true)     // enable Resource support
        .tools(true)         // enable Tool support
        .prompts(true)       // enable Prompt support
        .logging()           // enable Logging (defaults to INFO level)
        .build())
    .build();

// Register tools, resources, and prompts with the server
server.addTool(syncToolSpecification);
server.addResource(syncResourceSpecification);
server.addPrompt(syncPromptSpecification);
``` ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=%2F%2F%20Create%20a%20server%20with,build)) ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=%2F%2F%20Register%20tools%2C%20resources%2C%20and,addPrompt%28syncPromptSpecification))

In Scala, this same code is fully accessible. For example, you could write: 

```scala
val transport = new StdioServerTransportProvider(new ObjectMapper())
val server = McpServer.sync(transport)
  .serverInfo("my-server", "1.0.0")
  .capabilities(
    ServerCapabilities.builder()
      .resources(true)
      .tools(true)
      .prompts(true)
      .logging()
      .build()
  )
  .build()
// then register tools, resources, prompts similarly...
```

Once built, the server will negotiate protocol version and capabilities with any connecting client, then wait for incoming JSON-RPC requests over the chosen transport ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=,subscription%20system%2C%20and%20content%20retrieval)). **Asynchronous API** usage is similar except you would call `McpServer.async(...)` to get an `McpAsyncServer` and provide async handlers (which return futures or completions rather than direct results). The SDK uses a session-based architecture in recent versions (≥0.8.0) to manage state and async message flow ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=Breaking%20Changes%20in%200)).

## Registering Tools and Handling Requests

An MCP *Tool* is essentially a function that the AI agent can invoke. You need to **register each tool** with the server, providing a description and a handler implementation. Tools are defined with a **name**, a human-friendly **description**, and a JSON **schema** for their parameters (so the client/LLM knows what inputs to provide) ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=%2F%2F%20Sync%20tool%20specification%20var,)) ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=The%20Tool%20specification%20includes%20a,a%20map%20of%20tool%20arguments)). The Java SDK offers a `SyncToolSpecification` class (and a corresponding async variant) to encapsulate a tool definition and its handler logic.

For example, here’s a simple **calculator tool** that the AI can call to add or subtract two numbers:

```java
// Define the JSON schema for tool parameters (for illustration; usually more detailed)
String schema = "{ \"type\":\"object\", \"properties\":{ "
              + "\"operation\": {\"type\":\"string\"}, "
              + "\"a\": {\"type\":\"number\"}, "
              + "\"b\": {\"type\":\"number\"} } }";

// Create a synchronous Tool specification with name, description, and handler
var calcTool = new McpServerFeatures.SyncToolSpecification(
    new Tool("calculator", "Basic calculator operations", schema),
    (exchange, arguments) -> {
        // Handler: parse inputs and perform the operation
        String op = (String) arguments.get("operation");
        double a = ((Number) arguments.get("a")).doubleValue();
        double b = ((Number) arguments.get("b")).doubleValue();
        double result = "add".equalsIgnoreCase(op) ? a + b 
                      : "subtract".equalsIgnoreCase(op) ? a - b 
                      : Double.NaN;
        return new CallToolResult(result, /* stream? */ false);
    }
);

// Register the tool with the server
server.addTool(calcTool);
```

In the above, the handler is a Java lambda (which Scala can also supply as an anonymous function). It receives an `exchange` object (for interacting with the client/session if needed) and an `arguments` map containing the input parameters. It returns a `CallToolResult` (here with the computed number). The SDK takes care of serializing the result back to the client. This pattern is confirmed in the official docs: the tool specification combines a `Tool` descriptor (name, description, schema) with a handler function that executes the tool’s logic ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=var%20syncToolSpecification%20%3D%20new%20McpServerFeatures,)). In Scala, you might prefer to handle types more safely (e.g. pattern match on operation strings), but the idea is the same – retrieve inputs from the map and produce a result.

**Tool discovery:** Once your server is running, connected clients can query the list of available tools. The MCP protocol automatically handles discovery and notification of tool availability. By setting `.tools(true)` in `ServerCapabilities` (as shown above), the server indicates it supports tools, and it can send **tool list change notifications** if tools are added/removed at runtime ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=,Sampling%20support%20for%20AI%20model)). Typically though, you register all needed tools at startup.

**Other capabilities:** Similarly, you can register **resources** and **prompts** if your server provides them. A *Resource* is read-only data identified by a URI (for example, file contents or database entries) that the client can fetch via MCP. A *Prompt* is a templated prompt or instruction set that the client can request (useful for standardizing interactions). The SDK provides `SyncResourceSpecification` and `SyncPromptSpecification` to define these. For instance, a resource might have a handler that reads a file and returns a `ReadResourceResult` ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=%2F%2F%20Sync%20resource%20specification%20var,contents%29%3B)), and a prompt handler returns a `GetPromptResult` with templated messages ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=%2F%2F%20Sync%20prompt%20specification%20var,description%2C%20messages%29%3B)). You add them with `server.addResource(...)` or `server.addPrompt(...)` just like tools. If you don’t need resources or prompts, you can disable those capabilities.

**Logging:** The server can also emit structured log messages to the client (e.g. to inform the AI agent of events). Logging is enabled with `.logging()` in capabilities. You can send log notifications like:

```java
server.loggingNotification(LoggingMessageNotification.builder()
    .level(LoggingLevel.INFO)
    .logger("my.app")
    .data("Server initialized and ready")
    .build());
``` 

This will send an INFO-level log message to any connected client that has requested logs ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=The%20server%20provides%20structured%20logging,clients%20with%20different%20severity%20levels)). (Clients can filter by log level as needed.)

## Transport Layers: STDIO, SSE, WebSocket, etc.

MCP defines a **transport layer** for how the JSON-RPC messages flow between client and server ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=,HttpClient%2C%20Spring%20WebFlux%2C%20Spring%20WebMVC)). The Java SDK includes several built-in transports:

- **STDIO (Standard I/O)** – ideal for running the MCP server as a subprocess. This uses the process’s stdin/stdout streams to send/receive JSON messages. It’s very useful for integration with desktop chat apps or IDE plugins that spawn your server. In code, use `StdioServerTransportProvider` (as shown earlier) to create this transport ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=Create%20in)). Once you call `McpServer.sync(transportProvider).build()`, the server will begin reading from stdin and writing to stdout under the hood. (Be sure your application doesn’t prematurely exit – keep it running to serve requests.)

- **HTTP SSE (Server-Sent Events)** – suited for a network service. The SDK supports SSE streaming over HTTP, with implementations for Java’s HTTP client and for Spring’s WebFlux/WebMVC frameworks ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=%2A%20Default%20transports%3A%20%2A%20Stdio,Synchronous%20and%20Asynchronous%20programming%20paradigms)) ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=,HttpClient%2C%20Spring%20WebFlux%2C%20Spring%20WebMVC)). In practice, SSE means the server holds an HTTP connection open to stream JSON events. For example, using Spring Boot you can add the `mcp-spring-webmvc` or `mcp-spring-webflux` dependency and easily expose an SSE endpoint. The Spring integration will handle the low-level details (it provides auto-configuration for an MCP **server** endpoint out-of-the-box ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=Spring%20AI%20MCP%20extends%20the,applications%20using%20the%20Spring%20Initializer))). If you prefer not to use Spring, you could still embed an SSE transport: the core SDK provides a Servlet-based SSE server transport you can attach to a servlet container ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=%2A%20Java%20HttpClient,Synchronous%20and%20Asynchronous%20programming%20paradigms)). In either case, SSE is a good option when your Scala app runs as a web service and you want an AI client (like Claude or LangChain) to connect to `http://yourapp/mcp` (for example) and communicate.

- **WebSocket** – WebSocket is not provided as a built-in transport in the Java SDK as of now, but it’s conceptually similar to SSE (both are persistent, bidirectional channels). Some MCP implementations in other languages use WebSockets (e.g. the Unity MCP integration uses a WebSocket to exchange JSON messages ([Unity MCP Integration | MCP Servers](https://mcp.so/server/UnityMCPIntegration/Shahzad?tab=content#:~:text=MCP%20Server%20%28TypeScript%2FNode,them%20happens%20via%20WebSocket%2C))). If you need WebSocket in a Scala/Java context, you would have to implement a custom `McpTransport` that reads/writes via a WebSocket session. For instance, you might use Java’s WebSocket API or an Akka HTTP websocket stream to pass JSON text to `McpSession`. This is more advanced, so unless WebSocket is specifically required, using **stdio or SSE** is recommended (most clients support those).

**Choosing a transport:** For local integrations or CLI tools, **STDIO** is straightforward. If you’re building a long-running service or want remote access, **HTTP SSE** (perhaps via Spring Boot or Quarkus) is convenient. Notably, many open-source MCP servers use the stdio approach so they can be launched on-demand (e.g. via a CLI like `jbang` or `npm exec`) and controlled by a client application ([Model Context Protocol :: Quarkiverse Documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/mcp.html#:~:text=service)) ([Model Context Protocol :: Quarkiverse Documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/mcp.html#:~:text=quarkus.langchain4j.mcp.github.transport)).

## Scala Integration Tips

Because the SDK is pure Java, you can call it directly from Scala as shown in the examples. A few tips for idiomatic usage:

- **Functional Handling:** The SDK’s design is OO/imperative (e.g. register tools, then handle calls via side-effecting lambdas). In Scala, you might wrap these in your application’s logic monads (for example, if using ZIO or Cats Effect, you could call `server.addTool` inside a fiber or effect). But even without a wrapper, it’s fine to invoke the Java API in an `App` or a background thread. The handlers themselves can use Scala constructs internally. For example, inside the tool lambda, you could use pattern matching or call out to other Scala code to get results. Just ensure to return the expected Java result types (`CallToolResult`, etc.).

- **Concurrency:** If using the async server API (`McpAsyncServer`), you’ll be dealing with Java `CompletableFuture` or similar constructs for results. You can convert between Scala `Future` and Java `CompletableFuture` easily (e.g. via `CompletableFuture.supplyAsync` or `Future.toCompletableFuture` with appropriate execution context). This allows you to implement non-blocking tools in a more Scala-native way if needed. For simple cases, the sync API might be sufficient – the server will internally handle each request in a separate thread so your tool calls don’t block each other unnecessarily ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=%2A%20Implementing%20server,Providing%20structured%20logging%20and%20notifications)) ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=The%20server%20supports%20both%20synchronous,integration%20in%20different%20application%20contexts)).

- **Data Types:** The MCP schemas and results are JSON-based. The SDK uses Jackson (`ObjectMapper`) for JSON, and it will give you arguments as `java.util.Map` and values as primitive wrappers (Double, Integer, String, etc.). You may want to convert these to Scala types (e.g. use `arguments.get("foo").asInstanceOf[String]` or better, Jackson can bind JSON to a case class if you integrate it). Consider using the JSON schema to guide your parsing. For output, you typically return SDK result objects (like `CallToolResult` which can hold a number, boolean, string, or complex JSON). If you want to return a Scala collection or case class, you might need to convert it to a Java type or Jackson `JsonNode`. Keep an eye on the SDK’s documentation for how to structure complex results.

- **Server lifecycle:** Manage the server’s lifecycle from Scala as you would any server. For example, you might want to call `server.close()` on shutdown ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=%2F%2F%20Close%20the%20server%20when,close)). If using stdio and running as a child process, the lifecycle might be tied to the process lifecycle. If using SSE in a web app, it will tie into the web server’s lifecycle. The Java SDK also provides hooks for error handling and session management if needed.

## Examples and Further Resources

To accelerate adoption, it helps to study existing MCP server implementations:

- **Official Documentation & Examples:** The Model Context Protocol’s official site has a Java SDK guide and reference code. In particular, see the **MCP Server** section for code snippets on server setup, tool/resource/prompt definitions, and transport usage ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=%2F%2F%20Create%20a%20server%20with,build)) ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=var%20syncToolSpecification%20%3D%20new%20McpServerFeatures,)). The site also lists several **example servers** (for various use cases like file systems, databases, web APIs) – many of these are implemented in other languages, but they illustrate what an MCP server can do ([Example Servers - Model Context Protocol](https://modelcontextprotocol.io/examples#:~:text=Data%20and%20file%20systems)) ([Example Servers - Model Context Protocol](https://modelcontextprotocol.io/examples#:~:text=%2A%20Git%20,io)). The Java SDK usage remains consistent across domains.

- **Spring Boot Integration (Spring AI):** Spring’s experimental *Spring AI MCP* project builds on the Java SDK to simplify configuration in Spring Boot apps. It provides annotations (like `@Tool`) and auto-registration of beans so you can define tools as Spring components ([Introducing the Model Context Protocol Java SDK](https://spring.io/blog/2025/02/14/mcp-java-sdk-released-2#:~:text=When%20the%20client%20application%20starts%2C,and%20manage%20the%20server%20lifecycle)). If you’re already using Spring, this can save you boilerplate. Piotr Minkowski’s blog has a detailed tutorial on using MCP with Spring AI – including how to serve tools and prompts in a Spring Boot server and consume them in a client ([Using Model Context Protocol (MCP) with Spring AI - Piotr's TechBlog](https://piotrminkowski.com/2025/03/17/using-model-context-protocol-mcp-with-spring-ai/#:~:text=This%20article%20will%20show%20how,managing%20connections%20with%20MCP%20servers)) ([Using Model Context Protocol (MCP) with Spring AI - Piotr's TechBlog](https://piotrminkowski.com/2025/03/17/using-model-context-protocol-mcp-with-spring-ai/#:~:text=public%20List,Person)). This is a great real-world example of integrating the MCP server into a larger application context.

- **Quarkus MCP Servers:** The Quarkus community has created a suite of MCP servers in Java (as a Quarkus extension), demonstrating real implementations for various tools. Notably, **quarkus-mcp-servers** contains a JDBC database explorer server, a filesystem server, and a JavaFX drawing server, all written in Java/Quarkus on top of the MCP SDK ([Introducing Model Context Protocol servers project - Quarkus](https://quarkus.io/blog/introducing-mcp-servers/#:~:text=At%20time%20of%20writing%20there,are%20three%20servers%20implemented)) ([Introducing Model Context Protocol servers project - Quarkus](https://quarkus.io/blog/introducing-mcp-servers/#:~:text=JavaFX)). You can browse the source code in the GitHub repo to see how they register multiple tools and handle requests in a robust way. For example, the *mcp-server-jdbc* module exposes database query tools and uses the SDK’s JSON-RPC handling to return query results. These servers are packaged such that they can be launched via [JBang](https://www.jbang.dev/) (e.g. `jbang jdbc@quarkiverse/quarkus-mcp-servers <JDBC_URL>`) to run the server with STDIO transport ([Introducing Model Context Protocol servers project - Quarkus](https://quarkus.io/blog/introducing-mcp-servers/#:~:text=How%20to%20use%20the%20servers)). Reviewing this project is helpful to see how a production-grade MCP server is structured in Java.

- **Community Scala Projects:** There is at least one community effort to make a more Scala-idiomatic interface for MCP. The **Sakura MCP** project (GitHub: `mullerhai/sakura-mcp`) is a Scala library that **wraps the official Java SDK** (“shade from modelcontextprotocol.io”) ([GitHub - punkpeye/awesome-mcp-servers: A collection of MCP servers.](https://github.com/punkpeye/awesome-mcp-servers#:~:text=match%20at%20L1055%20%2A%20mullerhai%2Fsakura,io)). It provides a fluent API for constructing requests, and likely could be extended for servers. While not widely adopted yet, it indicates how one might adapt the SDK to a Scala style. If you prefer a purely Scala API, you could draw inspiration from there or even contribute to it. However, given the Java SDK is fully usable from Scala, you might find it sufficient to call it directly as shown above.

- **Other Resources:** For a broader context, the Awesome MCP list ([GitHub - punkpeye/awesome-mcp-servers: A collection of MCP servers.](https://github.com/punkpeye/awesome-mcp-servers#:~:text=What%20is%20MCP%3F)) and the MCP Servers Hub catalog enumerate many existing MCP servers (in various languages) and tools. Even if they aren’t in Scala/Java, they can spark ideas for what you can build. The official MCP specification ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=Introduction%20to%20the%20Model%20Context,MCP%29%20Java%20SDK)) is also a useful reference if you need to understand the protocol messages in depth (though the SDK abstracts most of that away). And if you’re connecting your Scala MCP server to a particular AI client (Claude, ChatGPT plugins, LangChain, etc.), be sure to check their docs for any MCP specifics (e.g. how to configure the client to launch or connect to your server via stdio or HTTP).

By following these examples and using the patterns above, a Scala developer can quickly stand up a functional MCP server using the Java SDK – all while writing in a Scala-friendly style. The combination of the official SDK’s robust features (tool execution, resource management, prompt templates, logging, etc.) ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=,Sampling%20support%20for%20AI%20model)) ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=Server%20Capabilities)) and Scala’s expressive code can lead to powerful integrations where AI models safely invoke your application logic. Good luck, and happy coding with MCP!

**Sources:**

- Official MCP Java SDK Documentation – *MCP Server Overview and Examples* ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=%2F%2F%20Create%20a%20server%20with,build)) ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=var%20syncToolSpecification%20%3D%20new%20McpServerFeatures,)) ([MCP Server - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-server#:~:text=Create%20in))
- Model Context Protocol Spec & Website – *Architecture, Transports, and SDK Features* ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=,HttpClient%2C%20Spring%20WebFlux%2C%20Spring%20WebMVC)) ([Overview - Model Context Protocol](https://modelcontextprotocol.io/sdk/java/mcp-overview#:~:text=,Sampling%20support%20for%20AI%20model))
- Spring Blog – *Introducing the MCP Java SDK* (Spring AI team) ([Introducing the Model Context Protocol Java SDK](https://spring.io/blog/2025/02/14/mcp-java-sdk-released-2#:~:text=The%20MCP%20Java%20SDK%20provides,features%20of%20the%20SDK%20include)) ([Introducing the Model Context Protocol Java SDK](https://spring.io/blog/2025/02/14/mcp-java-sdk-released-2#:~:text=Multiple%20Transport%20Implementations))
- Quarkus Blog – *Implementing a MCP Server in Quarkus* (Max R. Andersen) ([Introducing Model Context Protocol servers project - Quarkus](https://quarkus.io/blog/introducing-mcp-servers/#:~:text=At%20time%20of%20writing%20there,are%20three%20servers%20implemented)) ([Introducing Model Context Protocol servers project - Quarkus](https://quarkus.io/blog/introducing-mcp-servers/#:~:text=JavaFX))
- Piotr Minkowski’s TechBlog – *Using MCP with Spring AI (tool calling)* ([Using Model Context Protocol (MCP) with Spring AI - Piotr's TechBlog](https://piotrminkowski.com/2025/03/17/using-model-context-protocol-mcp-with-spring-ai/#:~:text=This%20article%20will%20show%20how,managing%20connections%20with%20MCP%20servers)) ([Using Model Context Protocol (MCP) with Spring AI - Piotr's TechBlog](https://piotrminkowski.com/2025/03/17/using-model-context-protocol-mcp-with-spring-ai/#:~:text=public%20List,Person))
- GitHub – *Quarkus MCP Servers (JDBC, Filesystem, JavaFX)* ([Introducing Model Context Protocol servers project - Quarkus](https://quarkus.io/blog/introducing-mcp-servers/#:~:text=JDBC)) ([Introducing Model Context Protocol servers project - Quarkus](https://quarkus.io/blog/introducing-mcp-servers/#:~:text=JavaFX)) and *Spring AI MCP (Java SDK + Spring integration)* ([GitHub - punkpeye/awesome-mcp-servers: A collection of MCP servers.](https://github.com/punkpeye/awesome-mcp-servers#:~:text=match%20at%20L1046%20%2A%20spring,CodeMirror%20extension%20that%20implements%20the))
- GitHub – *Sakura MCP (Scala MCP wrapper)* ([GitHub - punkpeye/awesome-mcp-servers: A collection of MCP servers.](https://github.com/punkpeye/awesome-mcp-servers#:~:text=match%20at%20L1055%20%2A%20mullerhai%2Fsakura,io)) and *Awesome MCP Servers list* ([GitHub - punkpeye/awesome-mcp-servers: A collection of MCP servers.](https://github.com/punkpeye/awesome-mcp-servers#:~:text=%2A%20spring,CodeMirror%20extension%20that%20implements%20the))