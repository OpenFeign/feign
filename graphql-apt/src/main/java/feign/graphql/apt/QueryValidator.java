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
package feign.graphql.apt;

import graphql.GraphQLError;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import graphql.validation.Validator;
import java.util.Locale;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

public class QueryValidator {

  private final Messager messager;

  public QueryValidator(Messager messager) {
    this.messager = messager;
  }

  public boolean validate(GraphQLSchema schema, Document document, Element methodElement) {
    var validator = new Validator();
    var errors = validator.validateDocument(schema, document, Locale.ENGLISH);

    if (errors.isEmpty()) {
      return true;
    }

    for (GraphQLError error : errors) {
      var locations = error.getLocations();
      if (locations != null && !locations.isEmpty()) {
        var loc = locations.get(0);
        messager.printMessage(
            Diagnostic.Kind.ERROR,
            "GraphQL validation error at line %d, column %d: %s"
                .formatted(loc.getLine(), loc.getColumn(), error.getMessage()),
            methodElement);
      } else {
        messager.printMessage(
            Diagnostic.Kind.ERROR,
            "GraphQL validation error: " + error.getMessage(),
            methodElement);
      }
    }
    return false;
  }
}
