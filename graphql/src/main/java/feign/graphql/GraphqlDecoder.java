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
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collection;

public class GraphqlDecoder implements Decoder {

  private final ObjectMapper mapper;

  public GraphqlDecoder() {
    this(new ObjectMapper());
  }

  public GraphqlDecoder(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.status() == 404 || response.status() == 204) {
      return Util.emptyValueOf(type);
    }
    if (response.body() == null) {
      return null;
    }

    String operationField = getOperationField(response);
    if (operationField == null) {
      throw new DecodeException(
          response.status(),
          "Missing X-Feign-GraphQL-Operation header; cannot decode GraphQL response",
          response.request());
    }

    Reader reader = response.body().asReader(response.charset());
    if (!reader.markSupported()) {
      reader = new BufferedReader(reader, 1);
    }

    JsonNode root = mapper.readTree(reader);

    JsonNode errorsNode = root.path("errors");
    if (!errorsNode.isMissingNode() && errorsNode.isArray() && !errorsNode.isEmpty()) {
      throw new GraphqlErrorException(
          response.status(), operationField, errorsNode.toString(), response.request());
    }

    JsonNode dataNode = root.path("data").path(operationField);
    if (dataNode.isMissingNode() || dataNode.isNull()) {
      return Util.emptyValueOf(type);
    }

    return mapper.readValue(mapper.treeAsTokens(dataNode), mapper.constructType(type));
  }

  private String getOperationField(Response response) {
    if (response.request() == null) {
      return null;
    }
    Collection<String> values =
        response.request().headers().get(GraphqlContract.HEADER_GRAPHQL_OPERATION);
    if (values == null || values.isEmpty()) {
      return null;
    }
    return values.iterator().next();
  }
}
