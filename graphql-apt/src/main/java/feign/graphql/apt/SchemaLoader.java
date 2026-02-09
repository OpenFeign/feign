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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

public class SchemaLoader {

  private final Filer filer;
  private final Messager messager;

  public SchemaLoader(Filer filer, Messager messager) {
    this.filer = filer;
    this.messager = messager;
  }

  public String load(String path, Element element) {
    StandardLocation[] locations = {
      StandardLocation.CLASS_PATH, StandardLocation.SOURCE_PATH, StandardLocation.CLASS_OUTPUT
    };

    for (var location : locations) {
      try {
        var resource = filer.getResource(location, "", path);
        try (var is = resource.openInputStream();
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
          var sb = new StringBuilder();
          var buf = new char[4096];
          int read;
          while ((read = reader.read(buf)) != -1) {
            sb.append(buf, 0, read);
          }
          return sb.toString();
        }
      } catch (IOException e) {
        // try next location
      }
    }

    messager.printMessage(Diagnostic.Kind.ERROR, "GraphQL schema not found: " + path, element);
    return null;
  }
}
