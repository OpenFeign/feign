/*
 * Copyright 2024 the original author or authors.
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

package feign.form.utils;

import java.net.InetSocketAddress;

import io.appulse.utils.SocketUtils;
import io.undertow.Undertow;
import io.undertow.io.Receiver.FullBytesCallback;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.Headers;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.val;

public final class UndertowServer implements AutoCloseable {

  private final Undertow undertow;

  @Builder(buildMethodName = "start")
  private UndertowServer (FullBytesCallback callback) {
    val port = SocketUtils.findFreePort()
      .orElseThrow(() -> new IllegalStateException("no available port to start server"));

    undertow = Undertow.builder()
      .addHttpListener(port, "localhost")
      .setHandler(
        new BlockingHandler(
          new ReadAllBytesHandler(callback)
        )
      )
      .build();

    undertow.start();
  }

  /**
   * Returns server connect URL.
   *
   * @return listining server's url.
   */
  public String getConnectUrl () {
    val listenerInfo = undertow.getListenerInfo()
      .iterator()
      .next();

    val address = (InetSocketAddress) listenerInfo.getAddress();

    return String.format(
      "%s://%s:%d",
      listenerInfo.getProtcol(),
      address.getHostString(),
      address.getPort()
    );
  }

  @Override
  public void close () {
    undertow.stop();
  }

  @RequiredArgsConstructor
  private static final class ReadAllBytesHandler implements HttpHandler {

    private final FullBytesCallback callback;

    @Override
    public void handleRequest (HttpServerExchange exchange) throws Exception {
      exchange.getRequestReceiver()
        .receiveFullBytes(this::handleBytes);
    }

    private void handleBytes (HttpServerExchange exchange, byte[] message) {
      try {
        callback.handle(exchange, message);
      } catch (Throwable ex) {
        exchange.setStatusCode(500);
        exchange.getResponseHeaders()
          .put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender()
          .send(ex.getMessage());
      }
    }
  }
}
