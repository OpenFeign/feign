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
package feign.jdkjson;

import static java.lang.String.format;

import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.PredicatedDecoder;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import jdk.incubator.json.Json;
import jdk.incubator.json.JsonParseException;
import jdk.incubator.json.JsonValue;

public class JdkJsonDecoder implements Decoder, PredicatedDecoder {

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (!(type instanceof Class<?> target)
        || !(String.class.equals(target) || JsonValue.class.isAssignableFrom(target))) {
      throw new DecodeException(
          response.status(),
          format("%s is not a type supported by this decoder.", type),
          response.request());
    }
    if (response.status() == 404 || response.status() == 204 || response.body() == null) {
      return null;
    }
    String body;
    try (Reader reader = response.body().asReader(response.charset())) {
      body = Util.toString(reader);
    }
    if (body.isEmpty()) {
      return null;
    }
    if (String.class.equals(target)) {
      return body;
    }
    return parse(response, target, body);
  }

  private Object parse(Response response, Class<?> target, String body) {
    JsonValue value;
    try {
      value = Json.parse(body);
    } catch (JsonParseException e) {
      throw new DecodeException(response.status(), e.getMessage(), response.request(), e);
    }
    if (!target.isInstance(value)) {
      throw new DecodeException(
          response.status(),
          format("Expected %s but got %s", target.getSimpleName(), value),
          response.request());
    }
    return value;
  }

  @Override
  public boolean canDecode(Response response, Type type) {
    return Util.isJsonContentType(response);
  }
}
