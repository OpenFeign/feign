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

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class GraphqlEncoder implements Encoder, RequestInterceptor {

  private final Encoder delegate;

  public GraphqlEncoder(Encoder delegate) {
    this.delegate = delegate;
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    Collection<String> queryHeaders = template.headers().get(GraphqlContract.HEADER_GRAPHQL_QUERY);
    if (queryHeaders == null || queryHeaders.isEmpty()) {
      delegate.encode(object, bodyType, template);
      return;
    }

    String encoded = queryHeaders.iterator().next();
    String query = new String(Base64.getDecoder().decode(encoded));

    Collection<String> variableHeaders =
        template.headers().get(GraphqlContract.HEADER_GRAPHQL_VARIABLE);

    Map<String, Object> graphqlBody = new LinkedHashMap<>();
    graphqlBody.put("query", query);

    if (object != null && variableHeaders != null && !variableHeaders.isEmpty()) {
      String variableName = variableHeaders.iterator().next();
      Map<String, Object> variables = new LinkedHashMap<>();
      variables.put(variableName, object);
      graphqlBody.put("variables", variables);
    }

    template.removeHeader(GraphqlContract.HEADER_GRAPHQL_QUERY);
    template.removeHeader(GraphqlContract.HEADER_GRAPHQL_VARIABLE);

    delegate.encode(graphqlBody, MAP_STRING_WILDCARD, template);
  }

  @Override
  public void apply(RequestTemplate template) {
    Collection<String> queryHeaders = template.headers().get(GraphqlContract.HEADER_GRAPHQL_QUERY);
    if (queryHeaders == null || queryHeaders.isEmpty() || (template.body() != null)) {
      return;
    }

    String encoded = queryHeaders.iterator().next();
    String query = new String(Base64.getDecoder().decode(encoded));

    Map<String, Object> graphqlBody = new LinkedHashMap<>();
    graphqlBody.put("query", query);

    template.removeHeader(GraphqlContract.HEADER_GRAPHQL_QUERY);
    template.removeHeader(GraphqlContract.HEADER_GRAPHQL_VARIABLE);

    delegate.encode(graphqlBody, MAP_STRING_WILDCARD, template);
  }
}
