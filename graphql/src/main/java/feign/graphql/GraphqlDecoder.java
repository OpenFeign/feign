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
package feign.graphql;

import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import feign.codec.JsonDecoder;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GraphqlDecoder implements Decoder {

  private final JsonDecoder jsonDecoder;

  public GraphqlDecoder(JsonDecoder jsonDecoder) {
    this.jsonDecoder = jsonDecoder;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    Type targetType = type;
    boolean optional = isOptionalType(type);
    if (optional) {
      targetType = extractOptionalInnerType(type);
    }

    var result = doDecode(response, targetType);
    return optional ? Optional.ofNullable(result) : result;
  }

  @SuppressWarnings("unchecked")
  private Object doDecode(Response response, Type type) throws IOException {
    if (response.status() == 404 || response.status() == 204) {
      return Util.emptyValueOf(type);
    }
    if (response.body() == null) {
      return null;
    }

    var root = (Map<String, Object>) jsonDecoder.decode(response, Map.class);
    if (root == null) {
      return Util.emptyValueOf(type);
    }

    var errors = root.get("errors");
    if (errors instanceof List<?> errorList && !errorList.isEmpty()) {
      var operationField = resolveOperationField(root, response);
      throw new GraphqlErrorException(
          response.status(), operationField, errors.toString(), response.request());
    }

    var data = root.get("data");
    if (!(data instanceof Map)) {
      return Util.emptyValueOf(type);
    }

    var dataMap = (Map<String, Object>) data;
    var fieldNames = dataMap.keySet().iterator();
    if (!fieldNames.hasNext()) {
      return Util.emptyValueOf(type);
    }

    var firstField = fieldNames.next();
    var operationData = dataMap.get(firstField);
    if (operationData == null) {
      return Util.emptyValueOf(type);
    }

    if (operationData instanceof List<?> list && !isCollectionOrArrayType(type)) {
      if (list.isEmpty()) {
        return Util.emptyValueOf(type);
      }
      operationData = list.get(0);
    }

    return jsonDecoder.convert(operationData, type);
  }

  @SuppressWarnings("unchecked")
  private String resolveOperationField(Map<String, Object> root, Response response) {
    var data = root.get("data");
    if (data instanceof Map) {
      var dataMap = (Map<String, Object>) data;
      var names = dataMap.keySet().iterator();
      if (names.hasNext()) {
        return names.next();
      }
    }

    if (response.request() != null && response.request().body() != null) {
      try {
        var fakeResponse =
            Response.builder()
                .status(200)
                .headers(Collections.emptyMap())
                .request(response.request())
                .body(response.request().body())
                .build();
        var requestBody = (Map<String, Object>) jsonDecoder.decode(fakeResponse, Map.class);
        if (requestBody != null) {
          var query = requestBody.get("query");
          if (query instanceof String queryStr) {
            return GraphqlContract.extractOperationField(queryStr);
          }
        }
      } catch (Exception e) {
        // ignore parsing errors
      }
    }

    return "unknown";
  }

  private boolean isOptionalType(Type type) {
    if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> cls) {
      return cls == Optional.class;
    }
    if (type instanceof Class<?> cls) {
      return cls == Optional.class;
    }
    return false;
  }

  private Type extractOptionalInnerType(Type type) {
    if (type instanceof ParameterizedType pt) {
      return pt.getActualTypeArguments()[0];
    }
    return Object.class;
  }

  private boolean isCollectionOrArrayType(Type type) {
    if (type instanceof Class<?> cls) {
      return cls.isArray() || Iterable.class.isAssignableFrom(cls);
    }
    if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> cls) {
      return Iterable.class.isAssignableFrom(cls);
    }
    return false;
  }
}
