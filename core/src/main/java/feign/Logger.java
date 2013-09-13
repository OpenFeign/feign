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
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
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

    @Override protected void log(String configKey, String format, Object... args) {
      System.err.printf(methodTag(configKey) + format + "%n", args);
    }
  }

  /**
   * logs to the category {@link Logger} at {@link java.util.logging.Level#FINE}, if loggable.
   */
  public static class JavaLogger extends Logger {
    final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Logger.class.getName());

    @Override void logRequest(String configKey, Level logLevel, Request request) {
      if (logger.isLoggable(java.util.logging.Level.FINE)) {
        super.logRequest(configKey, logLevel, request);
      }
    }

    @Override
    Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
      if (logger.isLoggable(java.util.logging.Level.FINE)) {
        return super.logAndRebufferResponse(configKey, logLevel, response, elapsedTime);
      }
      return response;
    }

    @Override protected void log(String configKey, String format, Object... args) {
      logger.fine(String.format(methodTag(configKey) + format, args));
    }

    /**
     * helper that configures jul to sanely log messages at FINE level without additional formatting.
     */
    public JavaLogger appendToFile(String logfile) {
      logger.setLevel(java.util.logging.Level.FINE);
      try {
        FileHandler handler = new FileHandler(logfile, true);
        handler.setFormatter(new SimpleFormatter() {
          @Override
          public String format(LogRecord record) {
            return String.format("%s%n", record.getMessage()); // NOPMD
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
    @Override void logRequest(String configKey, Level logLevel, Request request) {
    }

    @Override
    Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
      return response;
    }

    @Override
    protected void log(String configKey, String format, Object... args) {
    }
  }

  /**
   * Override to log requests and responses using your own implementation.
   * Messages will be http request and response text.
   *
   * @param configKey value of {@link Feign#configKey(java.lang.reflect.Method)}
   * @param format    {@link java.util.Formatter format string}
   * @param args      arguments applied to {@code format}
   */
  protected abstract void log(String configKey, String format, Object... args);

  void logRequest(String configKey, Level logLevel, Request request) {
    log(configKey, "---> %s %s HTTP/1.1", request.method(), request.url());
    if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {

      for (String field : request.headers().keySet()) {
        for (String value : valuesOrEmpty(request.headers(), field)) {
          log(configKey, "%s: %s", field, value);
        }
      }

      int bytes = 0;
      if (request.body() != null) {
        bytes = request.body().getBytes(UTF_8).length;
        if (logLevel.ordinal() >= Level.FULL.ordinal()) {
          log(configKey, ""); // CRLF
          log(configKey, "%s", request.body());
        }
      }
      log(configKey, "---> END HTTP (%s-byte body)", bytes);
    }
  }

  void logRetry(String configKey, Level logLevel) {
    log(configKey, "---> RETRYING");
  }

  Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
    log(configKey, "<--- HTTP/1.1 %s %s (%sms)", response.status(), response.reason(), elapsedTime);
    if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {

      for (String field : response.headers().keySet()) {
        for (String value : valuesOrEmpty(response.headers(), field)) {
          log(configKey, "%s: %s", field, value);
        }
      }

      if (response.body() != null) {
        if (logLevel.ordinal() >= Level.FULL.ordinal()) {
          log(configKey, ""); // CRLF
        }

        Reader body = response.body().asReader();
        try {
          StringBuilder buffered = new StringBuilder();
          BufferedReader reader = new BufferedReader(body);
          String line;
          while ((line = reader.readLine()) != null) {
            buffered.append(line);
            if (logLevel.ordinal() >= Level.FULL.ordinal()) {
              log(configKey, "%s", line);
            }
          }
          String bodyAsString = buffered.toString();
          log(configKey, "<--- END HTTP (%s-byte body)", bodyAsString.getBytes(UTF_8).length);
          return Response.create(response.status(), response.reason(), response.headers(), bodyAsString);
        } finally {
          ensureClosed(body);
        }
      }
    }
    return response;
  }

  IOException logIOException(String configKey, Level logLevel, IOException ioe, long elapsedTime) {
    log(configKey, "<--- ERROR %s: %s (%sms)", ioe.getClass().getSimpleName(), ioe.getMessage(), elapsedTime);
    if (logLevel.ordinal() >= Level.FULL.ordinal()) {
      StringWriter sw = new StringWriter();
      ioe.printStackTrace(new PrintWriter(sw));
      log(configKey, sw.toString());
      log(configKey, "<--- END ERROR");
    }
    return ioe;
  }

  static String methodTag(String configKey) {
    return new StringBuilder().append('[').append(configKey.substring(0, configKey.indexOf('('))).append("] ").toString();
  }
}
