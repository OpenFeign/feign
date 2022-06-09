/*
 * Copyright 2012-2022 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.okhttp;

import okhttp3.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static feign.Util.enumForName;

abstract class AbstractOkHttpClient {
  final okhttp3.OkHttpClient delegate;

  public AbstractOkHttpClient() {
    this(new okhttp3.OkHttpClient());
  }

  public AbstractOkHttpClient(okhttp3.OkHttpClient delegate) {
    this.delegate = delegate;
  }

  okhttp3.OkHttpClient getClient(feign.Request.Options options) {
    okhttp3.OkHttpClient requestScoped;
    if (delegate.connectTimeoutMillis() != options.connectTimeoutMillis()
        || delegate.readTimeoutMillis() != options.readTimeoutMillis()
        || delegate.followRedirects() != options.isFollowRedirects()) {
      requestScoped = delegate.newBuilder()
          .connectTimeout(options.connectTimeoutMillis(), TimeUnit.MILLISECONDS)
          .readTimeout(options.readTimeoutMillis(), TimeUnit.MILLISECONDS)
          .followRedirects(options.isFollowRedirects())
          .build();
    } else {
      requestScoped = delegate;
    }
    return requestScoped;
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
        requestBuilder.addHeader(field, value);
        if (field.equalsIgnoreCase("Content-Type")) {
          mediaType = MediaType.parse(value);
          if (input.charset() != null) {
            mediaType.charset(input.charset());
          }
        }
      }
    }
    // Some servers choke on the default accept string.
    if (!hasAcceptHeader) {
      requestBuilder.addHeader("Accept", "*/*");
    }

    byte[] inputBody = input.body();
    boolean isMethodWithBody =
        feign.Request.HttpMethod.POST == input.httpMethod()
            || feign.Request.HttpMethod.PUT == input.httpMethod()
            || feign.Request.HttpMethod.PATCH == input.httpMethod();
    if (isMethodWithBody) {
      requestBuilder.removeHeader("Content-Type");
      if (inputBody == null) {
        // write an empty BODY to conform with okhttp 2.4.0+
        // http://johnfeng.github.io/blog/2015/06/30/okhttp-updates-post-wouldnt-be-allowed-to-have-null-body/
        inputBody = new byte[0];
      }
    }

    RequestBody body = inputBody != null ? RequestBody.create(mediaType, inputBody) : null;
    requestBuilder.method(input.httpMethod().name(), body);
    return requestBuilder.build();
  }

  static feign.Response toFeignResponse(Response response, feign.Request request)
      throws IOException {
    return feign.Response.builder()
        .protocolVersion(enumForName(feign.Request.ProtocolVersion.class, response.protocol()))
        .status(response.code())
        .reason(response.message())
        .request(request)
        .headers(toMap(response.headers()))
        .body(toBody(response.body()))
        .build();
  }

  private static Map<String, Collection<String>> toMap(Headers headers) {
    return (Map) headers.toMultimap();
  }

  private static feign.Response.Body toBody(final ResponseBody input) throws IOException {
    if (input == null || input.contentLength() == 0) {
      if (input != null) {
        input.close();
      }
      return null;
    }
    final Integer length = input.contentLength() >= 0 && input.contentLength() <= Integer.MAX_VALUE
        ? (int) input.contentLength()
        : null;

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

      @SuppressWarnings("deprecation")
      @Override
      public Reader asReader() throws IOException {
        return input.charStream();
      }

      @Override
      public Reader asReader(Charset charset) throws IOException {
        return asReader();
      }
    };
  }
}
