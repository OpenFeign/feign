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

import feign.Experimental;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.codec.Encoder;
import java.util.LinkedHashMap;

@Experimental
public class GraphqlRequestInterceptor implements RequestInterceptor {

  private final Encoder delegate;
  private final GraphqlContract contract;

  public GraphqlRequestInterceptor(Encoder delegate, GraphqlContract contract) {
    this.delegate = delegate;
    this.contract = contract;
  }

  @Override
  public void apply(RequestTemplate template) {
    if (template.body() != null) {
      return;
    }

    var meta = contract.lookupMetadata(template);
    if (meta == null) {
      return;
    }

    var graphqlBody = new LinkedHashMap<String, Object>();
    graphqlBody.put("query", meta.query);

    delegate.encode(graphqlBody, Encoder.MAP_STRING_WILDCARD, template);
  }
}
