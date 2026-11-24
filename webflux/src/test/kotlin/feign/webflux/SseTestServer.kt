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
 * A minimal raw-socket HTTP server for SSE testing. It does things MockWebServer cannot express: it
 * writes events one at a time while the response is still open, and it observes when the client
 * cancels.
 *
 * Responses are close-delimited (no Content-Length, no chunked encoding). The body ends when the
 * server closes the connection.
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
  private val clientDisconnect = CompletableFuture<Boolean>()
  private val requestHeadersArrival = CompletableFuture<Boolean>()

  init {
    thread(name = "sse-test-server-connection", isDaemon = true) {
      try {
        val input = socket.getInputStream()
        val buffer = ByteArray(1024)
        val receivedRequest = StringBuilder()
        // drain request bytes; EOF or a socket error means the client closed the connection
        while (true) {
          val bytesRead = input.read(buffer)
          if (bytesRead == -1) break
          receivedRequest.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
          if (!requestHeadersArrival.isDone && receivedRequest.contains("\r\n\r\n")) {
            requestHeadersArrival.complete(true)
          }
        }
        clientDisconnect.complete(true)
      } catch (_: Exception) {
        requestHeadersArrival.complete(true)
        clientDisconnect.complete(true)
      }
    }
  }

  /** Blocks until the client's request headers have been fully received. */
  fun awaitRequestHeaders(timeoutMillis: Long = 5_000) {
    requestHeadersArrival.get(timeoutMillis, TimeUnit.MILLISECONDS)
  }

  /** Writes response headers with no framing header, so the body is close-delimited. */
  fun writeResponseHeaders(
      statusLine: String = "HTTP/1.1 200 OK",
      headers: Map<String, String> =
          mapOf("Content-Type" to "text/event-stream", "Connection" to "close")
  ) {
    val responseHeaders =
        buildString {
          append(statusLine).append("\r\n")
          headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
          append("\r\n")
        }
    writeRaw(responseHeaders)
  }

  fun writeRaw(text: String) {
    output.write(text.toByteArray(Charsets.UTF_8))
    output.flush()
  }

  /** Resolves once the client closed the connection (read EOF or socket error). */
  fun awaitClientDisconnect(timeoutMillis: Long = 5_000): Boolean =
      try {
        clientDisconnect.get(timeoutMillis, TimeUnit.MILLISECONDS)
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
