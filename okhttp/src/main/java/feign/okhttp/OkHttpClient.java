/*
 * Copyright 2015 Netflix, Inc.
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
package feign.okhttp;

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import feign.Client;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This module directs Feign's http requests to <a href="http://square.github.io/okhttp/">OkHttp</a>,
 * which enables SPDY and better network control. Ex.
 * <pre>
 * GitHub github = Feign.builder().client(new OkHttpClient()).target(GitHub.class,
 * "https://api.github.com");
 */
public final class OkHttpClient implements Client {

  private final com.squareup.okhttp.OkHttpClient delegate;

  public OkHttpClient() {
    this(new com.squareup.okhttp.OkHttpClient());
  }

  public OkHttpClient(com.squareup.okhttp.OkHttpClient delegate) {
    this.delegate = delegate;
  }

  static Request toOkHttpRequest(feign.Request input) {
    Request.Builder requestBuilder = new Request.Builder();
    requestBuilder.url(input.url());

    MediaType mediaType = null;
    boolean hasAcceptHeader = false;
    for (String field : input.headers().keySet()) {
      if (field.equalsIgnoreCase("Accept")) {
        hasAcceptHeader = true;
      }

      for (String value : input.headers().get(field)) {
        if (field.equalsIgnoreCase("Content-Type")) {
          mediaType = MediaType.parse(value);
          if (input.charset() != null) {
            mediaType.charset(input.charset());
          }
        } else {
          requestBuilder.addHeader(field, value);
        }
      }
    }
    // Some servers choke on the default accept string.
    if (!hasAcceptHeader) {
      requestBuilder.addHeader("Accept", "*/*");
    }

    RequestBody body = input.body() != null ? RequestBody.create(mediaType, input.body()) : null;
    requestBuilder.method(input.method(), body);
    return requestBuilder.build();
  }

  private static feign.Response toFeignResponse(Response input) {
    return feign.Response.create(
        input.code(), input.message(), toMap(input.headers()), toBody(input.body()));
  }

  private static Map<String, Collection<String>> toMap(Headers headers) {
    Map<String, Collection<String>> result =
        new LinkedHashMap<String, Collection<String>>(headers.size());
    for (String name : headers.names()) {
      // TODO: this is very inefficient as headers.values iterate case insensitively.
      result.put(name, headers.values(name));
    }
    return result;
  }

  private static feign.Response.Body toBody(final ResponseBody input) {
    if (input == null || input.contentLength() == 0) {
      return null;
    }
    if (input.contentLength() > Integer.MAX_VALUE) {
      throw new UnsupportedOperationException("Length too long " + input.contentLength());
    }
    final Integer length = input.contentLength() != -1 ? (int) input.contentLength() : null;

    return new feign.Response.Body() {

      @Override
      public void close() throws IOException {
        input.close();
      }

      @Override
      public Integer length() {
        return length;
      }

      @Override
      public boolean isRepeatable() {
        return false;
      }

      @Override
      public InputStream asInputStream() throws IOException {
        return input.byteStream();
      }

      @Override
      public Reader asReader() throws IOException {
        return input.charStream();
      }
    };
  }

  @Override
  public feign.Response execute(feign.Request input, feign.Request.Options options)
      throws IOException {
    com.squareup.okhttp.OkHttpClient requestScoped;
    if (delegate.getConnectTimeout() != options.connectTimeoutMillis()
        || delegate.getReadTimeout() != options.readTimeoutMillis()) {
      requestScoped = delegate.clone();
      requestScoped.setConnectTimeout(options.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
      requestScoped.setReadTimeout(options.readTimeoutMillis(), TimeUnit.MILLISECONDS);
    } else {
      requestScoped = delegate;
    }
    Request request = toOkHttpRequest(input);
    Response response = requestScoped.newCall(request).execute();
    return toFeignResponse(response);
  }
}
