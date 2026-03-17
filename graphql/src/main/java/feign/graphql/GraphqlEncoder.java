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
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;

@Experimental
public class GraphqlEncoder implements Encoder {

  private final Encoder delegate;
  private final GraphqlContract contract;

  public GraphqlEncoder(Encoder delegate, GraphqlContract contract) {
    this.delegate = delegate;
    this.contract = contract;
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    var meta = contract.lookupMetadata(template);
    if (meta == null) {
      delegate.encode(object, bodyType, template);
      return;
    }

    var graphqlBody = new LinkedHashMap<String, Object>();
    graphqlBody.put("query", meta.query);

    if (object != null && meta.variableName != null) {
      var variables = new LinkedHashMap<String, Object>();
      variables.put(meta.variableName, object);
      graphqlBody.put("variables", variables);
    }

    delegate.encode(graphqlBody, MAP_STRING_WILDCARD, template);
  }
}
