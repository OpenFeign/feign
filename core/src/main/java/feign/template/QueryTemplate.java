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
package feign.template;

import feign.CollectionFormat;
import feign.Util;
import feign.template.Template.EncodingOptions;
import feign.template.Template.ExpansionOptions;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Template for a Query String parameter. */
public final class QueryTemplate {

  private static final String UNDEF = "undef";
  private List<Template> values;
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
    return create(name, values, charset, CollectionFormat.EXPLODED, true);
  }

  public static QueryTemplate create(
      String name, Iterable<String> values, Charset charset, CollectionFormat collectionFormat) {
    return create(name, values, charset, collectionFormat, true);
  }

  /**
   * Create a new Query Template.
   *
   * @param name of the query parameter.
   * @param values in the template.
   * @param charset for the template.
   * @param collectionFormat to use.
   * @param decodeSlash if slash characters should be decoded
   * @return a QueryTemplate
   */
  public static QueryTemplate create(
      String name,
      Iterable<String> values,
      Charset charset,
      CollectionFormat collectionFormat,
      boolean decodeSlash) {
    if (Util.isBlank(name)) {
      throw new IllegalArgumentException("name is required.");
    }

    if (values == null) {
      throw new IllegalArgumentException("values are required");
    }

    /* remove all empty values from the array */
    Collection<String> remaining =
        StreamSupport.stream(values.spliterator(), false)
            .filter(Util::isNotBlank)
            .collect(Collectors.toList());

    return new QueryTemplate(name, remaining, charset, collectionFormat, decodeSlash);
  }

  /**
   * Append a value to the Query Template.
   *
   * @param queryTemplate to append to.
   * @param values to append.
   * @return a new QueryTemplate with value appended.
   */
  public static QueryTemplate append(
      QueryTemplate queryTemplate,
      Iterable<String> values,
      CollectionFormat collectionFormat,
      boolean decodeSlash) {
    List<String> queryValues = new ArrayList<>(queryTemplate.getValues());
    queryValues.addAll(
        StreamSupport.stream(values.spliterator(), false)
            .filter(Util::isNotBlank)
            .collect(Collectors.toList()));
    return create(
        queryTemplate.getName(),
        queryValues,
        StandardCharsets.UTF_8,
        collectionFormat,
        decodeSlash);
  }

  /**
   * Create a new Query Template.
   *
   * @param name of the query parameter.
   * @param values for the parameter.
   * @param collectionFormat to use.
   */
  private QueryTemplate(
      String name,
      Iterable<String> values,
      Charset charset,
      CollectionFormat collectionFormat,
      boolean decodeSlash) {
    this.values = new CopyOnWriteArrayList<>();
    this.name =
        new Template(
            name,
            ExpansionOptions.ALLOW_UNRESOLVED,
            EncodingOptions.REQUIRED,
            !decodeSlash,
            charset);
    this.collectionFormat = collectionFormat;

    /* parse each value into a template chunk for resolution later */
    for (String value : values) {
      if (value.isEmpty()) {
        /* skip */
        continue;
      }

      this.values.add(
          new Template(
              value, ExpansionOptions.REQUIRED, EncodingOptions.REQUIRED, !decodeSlash, charset));
    }

    if (this.values.isEmpty()) {
      /* in this case, we have a pure parameter */
      this.pure = true;
    }
  }

  public List<String> getValues() {
    return Collections.unmodifiableList(
        this.values.stream().map(Template::toString).collect(Collectors.toList()));
  }

  public List<String> getVariables() {
    List<String> variables = new ArrayList<>(this.name.getVariables());
    for (Template template : this.values) {
      variables.addAll(template.getVariables());
    }
    return Collections.unmodifiableList(variables);
  }

  public String getName() {
    return name.toString();
  }

  @Override
  public String toString() {
    return this.queryString(this.name.toString(), this.getValues());
  }

  /**
   * Expand this template. Unresolved variables are removed. If all values remain unresolved, the
   * result is an empty string.
   *
   * @param variables containing the values for expansion.
   * @return the expanded template.
   */
  public String expand(Map<String, ?> variables) {
    String name = this.name.expand(variables);

    if (this.pure) {
      return name;
    }

    List<String> expanded = new ArrayList<>();
    for (Template template : this.values) {
      String result = template.expand(variables);
      if (result == null) {
        continue;
      }

      /*
       * a collection (iterable, array or map) is expanded into a single comma separated value and
       * has to be split back into individual values. Literals and scalars are sent as written, so
       * their commas belong to the value and are not separators.
       */
      if (hasCollectionExpression(template, variables)) {
        expanded.addAll(Arrays.asList(result.split(",")));
      } else {
        expanded.add(result);
      }
    }

    return this.queryString(name, Collections.unmodifiableList(expanded));
  }

  /**
   * Determine if the given value template expands a collection variable, which is joined into a
   * single comma separated value.
   *
   * @param template to inspect.
   * @param variables containing the values for expansion.
   * @return true if a variable of the template holds a collection.
   */
  private static boolean hasCollectionExpression(Template template, Map<String, ?> variables) {
    for (TemplateChunk chunk : template.getTemplateChunks()) {
      if (!(chunk instanceof Expression)) {
        continue;
      }

      Object value = variables.get(((Expression) chunk).getName());
      if (value instanceof Optional<?>) {
        value = ((Optional<?>) value).orElse(null);
      }

      if (value instanceof Iterable<?>
          || value instanceof Map<?, ?>
          || (value != null && value.getClass().isArray())) {
        return true;
      }
    }
    return false;
  }

  private String queryString(String name, List<String> values) {
    if (this.pure) {
      return name;
    }

    if (!values.isEmpty()) {
      return this.collectionFormat.join(name, values, StandardCharsets.UTF_8).toString();
    }

    /* nothing to return, all values are unresolved */
    return null;
  }
}
