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
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
public final class HeaderTemplate extends Template {

  /* cache a copy of the variables for lookup later */
  private LinkedHashSet<String> values;
  private String name;

  public static HeaderTemplate from(String name, List<TemplateChunk> chunks) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("name is required.");
    }

    if (chunks == null) {
      throw new IllegalArgumentException("chunks are required.");
    }

    return new HeaderTemplate(name, Util.UTF_8, chunks);
  }

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
        template.append(",");
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
    LinkedHashSet<String> headerValues = new LinkedHashSet<>(headerTemplate.getValues());
    headerValues.addAll(StreamSupport.stream(values.spliterator(), false)
        .filter(Util::isNotBlank)
        .collect(Collectors.toCollection(LinkedHashSet::new)));
    return create(headerTemplate.getName(), headerValues);
  }

  /**
   * Append {@link TemplateChunk} to a Header Template.
   * 
   * @param headerTemplate to append to.
   * @param chunks to append.
   * @return a new HeaderTemplate with the values added.
   */
  public static HeaderTemplate appendFrom(HeaderTemplate headerTemplate,
                                          List<TemplateChunk> chunks) {
    List<TemplateChunk> existing = new CopyOnWriteArrayList<>(headerTemplate.getTemplateChunks());
    existing.addAll(chunks);
    return from(headerTemplate.getName(), existing);
  }

  /**
   * Creates a new Header Template.
   *
   * @param template to parse.
   */
  private HeaderTemplate(String template, String name, Iterable<String> values, Charset charset) {
    super(template, ExpansionOptions.REQUIRED, EncodingOptions.NOT_REQUIRED, false, charset);
    this.values = StreamSupport.stream(values.spliterator(), false)
        .filter(Util::isNotBlank)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    this.name = name;
  }

  /**
   * Creates a new Header Template from a set of TemplateChunks.
   *
   * @param name of the header.
   * @param charset to encode the expanded values in.
   * @param chunks for the template.
   */
  private HeaderTemplate(String name, Charset charset, List<TemplateChunk> chunks) {
    super(ExpansionOptions.REQUIRED, EncodingOptions.NOT_REQUIRED, false, charset,
        chunks);
    this.values = chunks.stream()
        .map(TemplateChunk::getValue)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    this.name = name;
  }

  public Collection<String> getValues() {
    return Collections.unmodifiableCollection(values);
  }

  public String getName() {
    return name;
  }

  @Override
  public String expand(Map<String, ?> variables) {
    String result = super.expand(variables);

    /* remove any trailing commas */
    while (result.endsWith(",")) {
      result = result.replaceAll(",$", "");
    }

    /* space all the commas now */
    result = result.replaceAll(",", ", ");
    return result;
  }
}
