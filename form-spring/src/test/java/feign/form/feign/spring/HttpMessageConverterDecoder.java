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
package feign.form.feign.spring;

import feign.Response;
import feign.codec.Decoder;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.AbstractHttpMessageConverter;

final class HttpMessageConverterDecoder<T> implements Decoder {

  private final AbstractHttpMessageConverter<T> converter;
  private final Class<T> type;

  HttpMessageConverterDecoder(AbstractHttpMessageConverter<T> converter, Class<T> type) {
    this.converter = converter;
    this.type = type;
  }

  @Override
  public Object decode(Response response, Type ignored) throws IOException {
    final var headers = new HttpHeaders();
    response.headers().forEach((name, values) -> headers.addAll(name, List.copyOf(values)));
    return converter.read(
        type,
        new HttpInputMessage() {
          @Override
          public InputStream getBody() throws IOException {
            return response.body().asInputStream();
          }

          @Override
          public HttpHeaders getHeaders() {
            return headers;
          }
        });
  }
}
