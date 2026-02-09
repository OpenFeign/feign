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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;

public class GraphqlDecoder implements Decoder {

  private final ObjectMapper mapper;
  private final Decoder delegate;

  public GraphqlDecoder() {
    this(new ObjectMapper(), null);
  }

  public GraphqlDecoder(ObjectMapper mapper) {
    this(mapper, null);
  }

  public GraphqlDecoder(Decoder delegate) {
    this(new ObjectMapper(), delegate);
  }

  public GraphqlDecoder(ObjectMapper mapper, Decoder delegate) {
    this.mapper = mapper;
    this.delegate = delegate;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.status() == 404 || response.status() == 204) {
      return Util.emptyValueOf(type);
    }
    if (response.body() == null) {
      return null;
    }

    var reader = response.body().asReader(response.charset());
    if (!reader.markSupported()) {
      reader = new BufferedReader(reader, 1);
    }

    var root = mapper.readTree(reader);

    var errorsNode = root.path("errors");
    if (!errorsNode.isMissingNode() && errorsNode.isArray() && !errorsNode.isEmpty()) {
      var operationField = resolveOperationField(root, response);
      throw new GraphqlErrorException(
          response.status(), operationField, errorsNode.toString(), response.request());
    }

    var dataNode = root.path("data");
    if (dataNode.isMissingNode() || dataNode.isNull() || !dataNode.isObject()) {
      return Util.emptyValueOf(type);
    }

    var fieldNames = dataNode.fieldNames();
    if (!fieldNames.hasNext()) {
      return Util.emptyValueOf(type);
    }

    var firstField = fieldNames.next();
    var operationData = dataNode.get(firstField);
    if (operationData == null || operationData.isNull()) {
      return Util.emptyValueOf(type);
    }

    if (delegate != null) {
      var dataBytes = mapper.writeValueAsBytes(operationData);
      var dataResponse =
          Response.builder()
              .status(response.status())
              .reason(response.reason())
              .headers(response.headers())
              .request(response.request())
              .body(dataBytes)
              .build();
      return delegate.decode(dataResponse, type);
    }

    return mapper.readValue(mapper.treeAsTokens(operationData), mapper.constructType(type));
  }

  private String resolveOperationField(JsonNode root, Response response) {
    var dataNode = root.path("data");
    if (!dataNode.isMissingNode() && dataNode.isObject()) {
      var names = dataNode.fieldNames();
      if (names.hasNext()) {
        return names.next();
      }
    }

    if (response.request() != null && response.request().body() != null) {
      try {
        var requestBody = mapper.readTree(response.request().body());
        var query = requestBody.path("query").asText(null);
        if (query != null) {
          return GraphqlContract.extractOperationField(query);
        }
      } catch (Exception e) {
        // ignore parsing errors
      }
    }

    return "unknown";
  }
}
