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
import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonBoolean;
import jdk.incubator.json.JsonNull;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonParseException;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;

public class JdkJsonDecoder implements Decoder, PredicatedDecoder {

  @Override
  public Object decode(Response response, Type type) throws IOException {
    Class<?> expectedClass = supportedTarget(response, type);
    String bodyText = readBody(response);
    if (bodyText == null || expectedClass == String.class) {
      return bodyText;
    }
    return parseJsonValueAs(expectedClass, bodyText, response);
  }

  private static Class<?> supportedTarget(Response response, Type type) {
    if (type instanceof Class<?> expectedClass
        && (expectedClass == String.class || JsonValue.class.isAssignableFrom(expectedClass))) {
      return expectedClass;
    }
    throw new DecodeException(
        response.status(),
        format("%s is not a type supported by this decoder.", type),
        response.request());
  }

  private static String readBody(Response response) throws IOException {
    if (response.status() == 404 || response.status() == 204 || response.body() == null) {
      return null;
    }
    try (Reader reader = response.body().asReader(response.charset())) {
      String bodyText = Util.toString(reader);
      return bodyText.isEmpty() ? null : bodyText;
    }
  }

  private static Object parseJsonValueAs(
      Class<?> expectedClass, String bodyText, Response response) {
    JsonValue parsedJson;
    try {
      parsedJson = Json.parse(bodyText);
    } catch (JsonParseException e) {
      throw new DecodeException(response.status(), e.getMessage(), response.request(), e);
    }
    if (!expectedClass.isInstance(parsedJson)) {
      throw new DecodeException(
          response.status(),
          format("Expected %s but got a JSON %s", expectedClass.getSimpleName(), kind(parsedJson)),
          response.request());
    }
    return parsedJson;
  }

  private static String kind(JsonValue parsedJson) {
    return switch (parsedJson) {
      case JsonObject _ -> "object";
      case JsonArray _ -> "array";
      case JsonString _ -> "string";
      case JsonNumber _ -> "number";
      case JsonBoolean _ -> "boolean";
      case JsonNull _ -> "null";
    };
  }

  @Override
  public boolean canDecode(Response response, Type type) {
    return Util.isJsonContentType(response);
  }
}
