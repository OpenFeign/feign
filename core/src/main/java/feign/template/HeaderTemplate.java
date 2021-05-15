/**
 * Copyright 2012-2020 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.template;

import feign.Util;
import feign.template.Template.EncodingOptions;
import feign.template.Template.ExpansionOptions;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Template for HTTP Headers. Variables that are unresolved are ignored and Literals are not
 * encoded.
 */
public final class HeaderTemplate {

  private final String name;
  private final List<Template> values = new CopyOnWriteArrayList<>();

  public static HeaderTemplate create(String name, Iterable<String> values) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("name is required.");
    }

    if (values == null) {
      throw new IllegalArgumentException("values are required");
    }

    return new HeaderTemplate(name, values, Util.UTF_8);
  }

  /**
   * Append values to a Header Template.
   *
   * @param headerTemplate to append to.
   * @param values to append.
   * @return a new Header Template with the values added.
   */
  public static HeaderTemplate append(HeaderTemplate headerTemplate, Iterable<String> values) {
    LinkedHashSet<String> headerValues = new LinkedHashSet<>(headerTemplate.getValues());
    headerValues.addAll(StreamSupport.stream(values.spliterator(), false)
        .filter(Util::isNotBlank)
        .collect(Collectors.toCollection(LinkedHashSet::new)));
    return create(headerTemplate.getName(), headerValues);
  }

  /**
   * Create a new Header Template.
   *
   * @param name of the Header.
   * @param values for the Header.
   * @param charset to use when encoding the values.
   */
  private HeaderTemplate(String name, Iterable<String> values, Charset charset) {
    this.name = name;

    for (String value : values) {
      if (value == null || value.isEmpty()) {
        /* skip */
        continue;
      }

      this.values.add(
          new Template(
              value,
              ExpansionOptions.REQUIRED,
              EncodingOptions.NOT_REQUIRED,
              false,
              charset));
    }
  }

  public Collection<String> getValues() {
    return Collections.unmodifiableList(this.values.stream()
        .map(Template::toString)
        .collect(Collectors.toList()));
  }

  public List<String> getVariables() {
    List<String> variables = new ArrayList<>();
    for (Template template : this.values) {
      variables.addAll(template.getVariables());
    }
    return Collections.unmodifiableList(variables);
  }

  public String getName() {
    return this.name;
  }

  public String expand(Map<String, ?> variables) {
    List<String> expanded = new ArrayList<>();
    if (!this.values.isEmpty()) {
      for (Template template : this.values) {
        String result = template.expand(variables);

        if (result == null) {
          /* ignore unresolved values */
          continue;
        }

        expanded.add(result);
      }
    }

    StringBuilder result = new StringBuilder();
    if (!expanded.isEmpty()) {
      result.append(String.join(", ", expanded));
    }

    return result.toString();
  }
}
