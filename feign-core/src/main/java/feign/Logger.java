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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import static feign.Util.UTF_8;
import static feign.Util.ensureClosed;
import static feign.Util.valuesOrEmpty;

/**
 * Simple logging abstraction for debug messages.  Adapted from {@code retrofit.RestAdapter.Log}.
 */
public abstract class Logger {

  /**
   * Controls the level of logging.
   */
  public enum Level {
    /**
     * No logging.
     */
    NONE,
    /**
     * Log only the request method and URL and the response status code and execution time.
     */
    BASIC,
    /**
     * Log the basic information along with request and response headers.
     */
    HEADERS,
    /**
     * Log the headers, body, and metadata for both requests and responses.
     */
    FULL
  }

  /**
   * logs to the category {@link Logger} at {@link java.util.logging.Level#FINE}.
   */
  public static class ErrorLogger extends Logger {
    final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Logger.class.getName());

    @Override protected void log(Target<?> target, String format, Object... args) {
      System.err.printf(format + "%n", args);
    }
  }

  /**
   * logs to the category {@link Logger} at {@link java.util.logging.Level#FINE}, if loggable.
   */
  public static class JavaLogger extends Logger {
    final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Logger.class.getName());

    @Override void logRequest(Target<?> target, Level logLevel, Request request) {
      if (logger.isLoggable(java.util.logging.Level.FINE)) {
        super.logRequest(target, logLevel, request);
      }
    }

    @Override
    Response logAndRebufferResponse(Target<?> target, Level logLevel, Response response, long elapsedTime) throws IOException {
      if (logger.isLoggable(java.util.logging.Level.FINE)) {
        return super.logAndRebufferResponse(target, logLevel, response, elapsedTime);
      }
      return response;
    }

    @Override protected void log(Target<?> target, String format, Object... args) {
      logger.fine(String.format(format, args));
    }

    /**
     * helper that configures jul to sanely log messages.
     */
    public JavaLogger appendToFile(String logfile) {
      final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
      logger.setLevel(java.util.logging.Level.FINE);
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

  public static class NoOpLogger extends Logger {
    @Override void logRequest(Target<?> target, Level logLevel, Request request) {
    }

    @Override
    Response logAndRebufferResponse(Target<?> target, Level logLevel, Response response, long elapsedTime) throws IOException {
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

  void logRequest(Target<?> target, Level logLevel, Request request) {
    log(target, "---> %s %s HTTP/1.1", request.method(), request.url());
    if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {

      for (String field : request.headers().keySet()) {
        for (String value : valuesOrEmpty(request.headers(), field)) {
          log(target, "%s: %s", field, value);
        }
      }

      int bytes = 0;
      if (request.body() != null) {
        bytes = request.body().getBytes(UTF_8).length;
        if (logLevel.ordinal() >= Level.FULL.ordinal()) {
          log(target, ""); // CRLF
          log(target, "%s", request.body());
        }
      }
      log(target, "---> END HTTP (%s-byte body)", bytes);
    }
  }

  Response logAndRebufferResponse(Target<?> target, Level logLevel, Response response, long elapsedTime) throws IOException {
    log(target, "<--- HTTP/1.1 %s %s (%sms)", response.status(), response.reason(), elapsedTime);
    if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {

      for (String field : response.headers().keySet()) {
        for (String value : valuesOrEmpty(response.headers(), field)) {
          log(target, "%s: %s", field, value);
        }
      }

      if (response.body() != null) {
        if (logLevel.ordinal() >= Level.FULL.ordinal()) {
          log(target, ""); // CRLF
        }

        Reader body = response.body().asReader();
        try {
          StringBuilder buffered = new StringBuilder();
          BufferedReader reader = new BufferedReader(body);
          String line;
          while ((line = reader.readLine()) != null) {
            buffered.append(line);
            if (logLevel.ordinal() >= Level.FULL.ordinal()) {
              log(target, "%s", line);
            }
          }
          String bodyAsString = buffered.toString();
          log(target, "<--- END HTTP (%s-byte body)", bodyAsString.getBytes(UTF_8).length);
          return Response.create(response.status(), response.reason(), response.headers(), bodyAsString);
        } finally {
          ensureClosed(response.body());
        }
      }
    }
    return response;
  }
}
