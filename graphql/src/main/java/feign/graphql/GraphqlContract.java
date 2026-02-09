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

import feign.Contract;
import feign.Request.HttpMethod;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GraphqlContract extends Contract.Default {

  static final String HEADER_GRAPHQL_QUERY = "X-Feign-GraphQL-Query";
  static final String HEADER_GRAPHQL_OPERATION = "X-Feign-GraphQL-Operation";
  static final String HEADER_GRAPHQL_VARIABLE = "X-Feign-GraphQL-Variable";

  private static final Pattern OPERATION_FIELD_PATTERN =
      Pattern.compile("\\{\\s*(\\w+)\\s*[({]", Pattern.DOTALL);

  private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\s*(\\w+)\\s*:");

  public GraphqlContract() {
    super.registerMethodAnnotation(
        GraphqlQuery.class,
        (annotation, metadata) -> {
          String query = annotation.value();

          if (metadata.template().method() == null) {
            metadata.template().method(HttpMethod.POST);
            metadata.template().uri("/");
          }

          String encoded = Base64.getEncoder().encodeToString(query.getBytes());
          metadata.template().header(HEADER_GRAPHQL_QUERY, encoded);

          String operationField = extractOperationField(query);
          if (operationField != null) {
            metadata.template().header(HEADER_GRAPHQL_OPERATION, operationField);
          }

          String variableName = extractFirstVariable(query);
          if (variableName != null) {
            metadata.template().header(HEADER_GRAPHQL_VARIABLE, variableName);
          }
        });
  }

  static String extractOperationField(String query) {
    int braceCount = 0;
    boolean inOperation = false;

    for (int i = 0; i < query.length(); i++) {
      char c = query.charAt(i);
      if (c == '{') {
        braceCount++;
        if (braceCount == 1) {
          inOperation = true;
        } else if (braceCount == 2 && inOperation) {
          String prefix = query.substring(0, i).trim();
          Matcher m = OPERATION_FIELD_PATTERN.matcher(prefix + "{");
          if (m.find()) {
            return m.group(1);
          }
        }
      } else if (c == '}') {
        braceCount--;
      }
    }

    Matcher m = OPERATION_FIELD_PATTERN.matcher(query);
    if (m.find()) {
      return m.group(1);
    }
    return null;
  }

  static String extractFirstVariable(String query) {
    Matcher m = VARIABLE_PATTERN.matcher(query);
    if (m.find()) {
      return m.group(1);
    }
    return null;
  }
}
