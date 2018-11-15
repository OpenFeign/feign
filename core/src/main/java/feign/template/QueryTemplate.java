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

import feign.CollectionFormat;
import feign.Util;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Template for a Query String parameter.
 */
public final class QueryTemplate extends Template {

  /* cache a copy of the variables for lookup later */
  private List<String> values;
  private final Template name;
  private final CollectionFormat collectionFormat;
  private boolean pure = false;

  /**
   * Create a new Query Template.
   *
   * @param name of the query parameter.
   * @param values in the template.
   * @param charset for the template.
   * @return a QueryTemplate.
   */
  public static QueryTemplate create(String name, Iterable<String> values, Charset charset) {
    return create(name, values, charset, CollectionFormat.EXPLODED);
  }

  /**
   * Create a new Query Template.
   *
   * @param name of the query parameter.
   * @param values in the template.
   * @param charset for the template.
   * @param collectionFormat to use.
   * @return a QueryTemplate
   */
  public static QueryTemplate create(String name,
                                     Iterable<String> values,
                                     Charset charset,
                                     CollectionFormat collectionFormat) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("name is required.");
    }

    if (values == null) {
      throw new IllegalArgumentException("values are required");
    }

    /* remove all empty values from the array */
    Collection<String> remaining = StreamSupport.stream(values.spliterator(), false)
        .filter(Util::isNotBlank)
        .collect(Collectors.toList());

    StringBuilder template = new StringBuilder();
    Iterator<String> iterator = remaining.iterator();
    while (iterator.hasNext()) {
      template.append(iterator.next());
      if (iterator.hasNext()) {
        template.append(",");
      }
    }

    return new QueryTemplate(template.toString(), name, remaining, charset, collectionFormat);
  }

  /**
   * Append a value to the Query Template.
   *
   * @param queryTemplate to append to.
   * @param values to append.
   * @return a new QueryTemplate with value appended.
   */
  public static QueryTemplate append(QueryTemplate queryTemplate,
                                     Iterable<String> values,
                                     CollectionFormat collectionFormat) {
    List<String> queryValues = new ArrayList<>(queryTemplate.getValues());
    queryValues.addAll(StreamSupport.stream(values.spliterator(), false)
        .filter(Util::isNotBlank)
        .collect(Collectors.toList()));
    return create(queryTemplate.getName(), queryValues, queryTemplate.getCharset(),
        collectionFormat);
  }

  /**
   * Create a new Query Template.
   *
   * @param template for the Query String.
   * @param name of the query parameter.
   * @param values for the parameter.
   * @param collectionFormat to use.
   */
  private QueryTemplate(
      String template,
      String name,
      Iterable<String> values,
      Charset charset,
      CollectionFormat collectionFormat) {
    super(template, ExpansionOptions.REQUIRED, EncodingOptions.REQUIRED, true, charset);
    this.name = new Template(name, ExpansionOptions.ALLOW_UNRESOLVED, EncodingOptions.REQUIRED,
        false, charset);
    this.collectionFormat = collectionFormat;
    this.values = StreamSupport.stream(values.spliterator(), false)
        .filter(Util::isNotBlank)
        .collect(Collectors.toList());
    if (this.values.isEmpty()) {
      /* in this case, we have a pure parameter */
      this.pure = true;

    }
  }

  public List<String> getValues() {
    return values;
  }

  public String getName() {
    return name.toString();
  }

  @Override
  public String toString() {
    return this.queryString(this.name.toString(), super.toString());
  }

  /**
   * Expand this template. Unresolved variables are removed. If all values remain unresolved, the
   * result is an empty string.
   *
   * @param variables containing the values for expansion.
   * @return the expanded template.
   */
  @Override
  public String expand(Map<String, ?> variables) {
    String name = this.name.expand(variables);
    return this.queryString(name, super.expand(variables));
  }

  private String queryString(String name, String values) {
    if (this.pure) {
      return name;
    }

    /* covert the comma separated values into a value query string */
    List<String> resolved = Arrays.stream(values.split(","))
        .filter(Util::isNotBlank)
        .collect(Collectors.toList());

    if (!resolved.isEmpty()) {
      return this.collectionFormat.join(name, resolved, this.getCharset()).toString();
    }

    /* nothing to return, all values are unresolved */
    return null;
  }

}
