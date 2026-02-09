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
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Iterator;

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

    Reader reader = response.body().asReader(response.charset());
    if (!reader.markSupported()) {
      reader = new BufferedReader(reader, 1);
    }

    JsonNode root = mapper.readTree(reader);

    JsonNode errorsNode = root.path("errors");
    if (!errorsNode.isMissingNode() && errorsNode.isArray() && !errorsNode.isEmpty()) {
      String operationField = resolveOperationField(root, response);
      throw new GraphqlErrorException(
          response.status(), operationField, errorsNode.toString(), response.request());
    }

    JsonNode dataNode = root.path("data");
    if (dataNode.isMissingNode() || dataNode.isNull() || !dataNode.isObject()) {
      return Util.emptyValueOf(type);
    }

    Iterator<String> fieldNames = dataNode.fieldNames();
    if (!fieldNames.hasNext()) {
      return Util.emptyValueOf(type);
    }

    String firstField = fieldNames.next();
    JsonNode operationData = dataNode.get(firstField);
    if (operationData == null || operationData.isNull()) {
      return Util.emptyValueOf(type);
    }

    if (delegate != null) {
      byte[] dataBytes = mapper.writeValueAsBytes(operationData);
      Response dataResponse =
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
    JsonNode dataNode = root.path("data");
    if (!dataNode.isMissingNode() && dataNode.isObject()) {
      Iterator<String> names = dataNode.fieldNames();
      if (names.hasNext()) {
        return names.next();
      }
    }

    if (response.request() != null && response.request().body() != null) {
      try {
        JsonNode requestBody = mapper.readTree(response.request().body());
        String query = requestBody.path("query").asText(null);
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
