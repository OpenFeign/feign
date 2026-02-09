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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class GraphqlContract extends Contract.Default {

  private static final Pattern OPERATION_FIELD_PATTERN =
      Pattern.compile("\\{\\s*(\\w+)\\s*[({]", Pattern.DOTALL);

  private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\s*(\\w+)\\s*:");

  private final Map<String, QueryMetadata> metadata = new ConcurrentHashMap<>();

  public GraphqlContract() {
    super.registerMethodAnnotation(
        GraphqlQuery.class,
        (annotation, data) -> {
          var query = annotation.value();

          if (data.template().method() == null) {
            data.template().method(HttpMethod.POST);
            data.template().uri("/");
          }

          var variableName = extractFirstVariable(query);
          metadata.put(data.configKey(), new QueryMetadata(query, variableName));
        });
  }

  Map<String, QueryMetadata> queryMetadata() {
    return metadata;
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
          var prefix = query.substring(0, i).trim();
          var m = OPERATION_FIELD_PATTERN.matcher(prefix + "{");
          if (m.find()) {
            return m.group(1);
          }
        }
      } else if (c == '}') {
        braceCount--;
      }
    }

    var m = OPERATION_FIELD_PATTERN.matcher(query);
    if (m.find()) {
      return m.group(1);
    }
    return null;
  }

  static String extractFirstVariable(String query) {
    var m = VARIABLE_PATTERN.matcher(query);
    if (m.find()) {
      return m.group(1);
    }
    return null;
  }

  static class QueryMetadata {
    final String query;
    final String variableName;

    QueryMetadata(String query, String variableName) {
      this.query = query;
      this.variableName = variableName;
    }
  }
}
