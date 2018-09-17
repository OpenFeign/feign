/**
 * Copyright 2012-2018 The Feign Authors
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
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Template for HTTP Headers. Variables that are unresolved are ignored and Literals are not
 * encoded.
 */
public final class HeaderTemplate extends Template {

  /* cache a copy of the variables for lookup later */
  private Set<String> values;
  private String name;

  public static HeaderTemplate create(String name, Iterable<String> values) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("name is required.");
    }

    if (values == null) {
      throw new IllegalArgumentException("values are required");
    }

    /* construct a uri template from the name and values */
    StringBuilder template = new StringBuilder();
    template.append(name)
        .append(" ");

    /* create a comma separated template for the header values */
    Iterator<String> iterator = values.iterator();
    while (iterator.hasNext()) {
      template.append(iterator.next());
      if (iterator.hasNext()) {
        template.append(", ");
      }
    }
    return new HeaderTemplate(template.toString(), name, values, Util.UTF_8);
  }

  /**
   * Append values to a Header Template.
   *
   * @param headerTemplate to append to.
   * @param values to append.
   * @return a new Header Template with the values added.
   */
  public static HeaderTemplate append(HeaderTemplate headerTemplate, Iterable<String> values) {
    Set<String> headerValues = new LinkedHashSet<>(headerTemplate.getValues());
    headerValues.addAll(StreamSupport.stream(values.spliterator(), false)
        .filter(Util::isNotBlank)
        .collect(Collectors.toSet()));
    return create(headerTemplate.getName(), headerValues);
  }

  /**
   * Creates a new Header Template.
   *
   * @param template to parse.
   */
  private HeaderTemplate(String template, String name, Iterable<String> values, Charset charset) {
    super(template, false, false, false, charset);
    this.values = StreamSupport.stream(values.spliterator(), false)
        .filter(Util::isNotBlank)
        .collect(Collectors.toSet());
    this.name = name;
  }

  public Collection<String> getValues() {
    return Collections.unmodifiableCollection(values);
  }

  public String getName() {
    return name;
  }
}
