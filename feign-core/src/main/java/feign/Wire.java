/*
 * Copyright 2013 Netflix, Inc.
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
package feign;

import com.google.common.io.Closer;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Map.Entry;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/*  Writes http headers and body. Plumb to your favorite log impl. */
public abstract class Wire {
  /*  logs to the category {@link Wire} at {@link Level#FINE}. */
  public static class ErrorWire extends Wire {
    final Logger logger = Logger.getLogger(Wire.class.getName());

    @Override protected void log(Target<?> target, String format, Object... args) {
      System.err.printf(format + "%n", args);
    }
  }

  /* logs to the category {@link Wire} at {@link Level#FINE}, if loggable. */
  public static class LoggingWire extends Wire {
    final Logger logger = Logger.getLogger(Wire.class.getName());

    @Override void wireRequest(Target<?> target, Request request) {
      if (logger.isLoggable(Level.FINE)) {
        super.wireRequest(target, request);
      }
    }

    @Override Response wireAndRebufferResponse(Target<?> target, Response response) throws IOException {
      if (logger.isLoggable(Level.FINE)) {
        return super.wireAndRebufferResponse(target, response);
      }
      return response;
    }

    @Override protected void log(Target<?> target, String format, Object... args) {
      logger.fine(String.format(format, args));
    }

    /* helper that configures jul to sanely log messages. */
    public LoggingWire appendToFile(String logfile) {
      final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
      logger.setLevel(Level.FINE);
      try {
        FileHandler handler = new FileHandler(logfile, true);
        handler.setFormatter(new SimpleFormatter() {
          @Override
          public String format(LogRecord record) {
            String timestamp = sdf.format(new java.util.Date(record.getMillis())); // NOPMD
            return String.format("%s %s%n", timestamp, record.getMessage()); // NOPMD
          }
        });
        logger.addHandler(handler);
      } catch (IOException e) {
        throw new IllegalStateException("Could not add file handler.", e);
      }
      return this;
    }
  }

  public static class NoOpWire extends Wire {
    @Override void wireRequest(Target<?> target, Request request) {
    }

    @Override Response wireAndRebufferResponse(Target<?> target, Response response) throws IOException {
      return response;
    }

    @Override
    protected void log(Target<?> target, String format, Object... args) {
    }
  }

  /**
   * Override to log requests and responses using your own implementation.
   * Messages will be http request and response text.
   *
   * @param target useful if using MDC (Mapped Diagnostic Context) loggers
   * @param format {@link java.util.Formatter format string}
   * @param args   arguments applied to {@code format}
   */
  protected abstract void log(Target<?> target, String format, Object... args);

  void wireRequest(Target<?> target, Request request) {
    log(target, ">> %s %s HTTP/1.1", request.method(), request.url());

    for (Entry<String, String> header : request.headers().entries()) {
      log(target, ">> %s: %s", header.getKey(), header.getValue());
    }

    if (request.body().isPresent()) {
      log(target, ">> "); // CRLF
      log(target, ">> %s", request.body().get());
    }
  }

  Response wireAndRebufferResponse(Target<?> target, Response response) throws IOException {
    log(target, "<< HTTP/1.1 %s %s", response.status(), response.reason());

    for (Entry<String, String> header : response.headers().entries()) {
      log(target, "<< %s: %s", header.getKey(), header.getValue());
    }

    if (response.body().isPresent()) {
      log(target, "<< "); // CRLF
      Closer closer = Closer.create();
      try {
        StringBuilder body = new StringBuilder();
        BufferedReader reader = new BufferedReader(closer.register(response.body().get().asReader()));
        String line;
        while ((line = reader.readLine()) != null) {
          body.append(line);
          log(target, "<< %s", line);
        }
        return Response.create(response.status(), response.reason(), response.headers(), body.toString());
      } catch (Throwable e) {
        throw closer.rethrow(e);
      } finally {
        closer.close();
      }
    }
    return response;
  }
}
