/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.webflux

import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Minimal raw-socket HTTP server for SSE semantics that MockWebServer cannot express: writing
 * events one at a time while the response is still open, and observing when the client cancels.
 *
 * Responses are close-delimited (no Content-Length, no chunked encoding): the body ends when the
 * server closes the connection, which is the simplest valid SSE framing for a test server.
 */
class SseTestServer : AutoCloseable {

  private val serverSocket = ServerSocket(0, 64, InetAddress.getLoopbackAddress())
  private val connections = LinkedBlockingQueue<SseTestConnection>()

  @Volatile private var closed = false

  val port: Int
    get() = serverSocket.localPort

  val baseUrl: String
    get() = "http://127.0.0.1:$port"

  init {
    thread(name = "sse-test-server-accept", isDaemon = true) {
      while (!closed) {
        try {
          connections.put(SseTestConnection(serverSocket.accept()))
        } catch (_: Exception) {
          return@thread
        }
      }
    }
  }

  /** Blocks until a client connects. */
  fun awaitConnection(timeoutMillis: Long = 5_000): SseTestConnection =
      connections.poll(timeoutMillis, TimeUnit.MILLISECONDS)
          ?: error("No client connected within ${timeoutMillis}ms")

  override fun close() {
    closed = true
    serverSocket.close()
    while (connections.isNotEmpty()) connections.poll()?.close()
  }
}

/** One accepted client connection with write/EOF observation helpers. */
class SseTestConnection(private val socket: Socket) : AutoCloseable {

  private val output: OutputStream = socket.getOutputStream()
  private val disconnected = CompletableFuture<Boolean>()
  private val requestReceived = CompletableFuture<Boolean>()

  init {
    thread(name = "sse-test-server-connection", isDaemon = true) {
      try {
        val input = socket.getInputStream()
        val buffer = ByteArray(1024)
        var received = StringBuilder()
        // drain request bytes; EOF or a socket error means the client closed the connection
        while (true) {
          val read = input.read(buffer)
          if (read == -1) break
          received.append(String(buffer, 0, read, Charsets.UTF_8))
          if (!requestReceived.isDone && received.contains("\r\n\r\n")) {
            // end of request headers seen; it is now safe to write the response
            requestReceived.complete(true)
          }
        }
        disconnected.complete(true)
      } catch (_: Exception) {
        requestReceived.complete(true)
        disconnected.complete(true)
      }
    }
  }

  /** Blocks until the client's request headers have been fully received. */
  fun awaitRequestReceived(timeoutMillis: Long = 5_000): Boolean =
      try {
        requestReceived.get(timeoutMillis, TimeUnit.MILLISECONDS)
      } catch (_: Exception) {
        false
      }

  /** Writes response headers with no framing header, so the body is close-delimited. */
  fun writeHead(
      statusLine: String = "HTTP/1.1 200 OK",
      headers: Map<String, String> =
          mapOf("Content-Type" to "text/event-stream", "Connection" to "close")
  ) {
    val head =
        buildString {
          append(statusLine).append("\r\n")
          headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
          append("\r\n")
        }
    writeRaw(head)
  }

  fun writeRaw(text: String) {
    output.write(text.toByteArray(Charsets.UTF_8))
    output.flush()
  }

  /** Resolves once the client closed the connection (read EOF or socket error). */
  fun awaitClientDisconnect(timeoutMillis: Long = 5_000): Boolean =
      try {
        disconnected.get(timeoutMillis, TimeUnit.MILLISECONDS)
      } catch (_: Exception) {
        false
      }

  /**
   * Finishes the response gracefully: shuts down the output half (clean FIN, no RST) and waits
   * until the client side has fully closed before releasing the socket.
   */
  override fun close() {
    try {
      socket.shutdownOutput()
    } catch (_: Exception) {
      // already closed
    }
    awaitClientDisconnect(2_000)
    try {
      socket.close()
    } catch (_: Exception) {
      // already closed
    }
  }
}
