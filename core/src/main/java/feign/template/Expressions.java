/*
 * Copyright 2012-2022 The Feign Authors
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

import feign.Param.Expander;
import feign.Util;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Expressions {

  private static final String PATH_STYLE_MODIFIER = ";";
  private static final Pattern EXPRESSION_PATTERN = Pattern.compile("^([+#./;?&]?)(.*)$");

  public static Expression create(final String value) {

    /* remove the start and end braces */
    final String expression = stripBraces(value);
    if (expression == null || expression.isEmpty()) {
      throw new IllegalArgumentException("an expression is required.");
    }

    /* create a new regular expression matcher for the expression */
    String variableName = null;
    String variablePattern = null;
    String modifier = null;
    Matcher matcher = EXPRESSION_PATTERN.matcher(expression);
    if (matcher.matches()) {
      /* grab the modifier */
      modifier = matcher.group(1).trim();

      /* we have a valid variable expression, extract the name from the first group */
      variableName = matcher.group(2).trim();
      if (variableName.contains(":")) {
        /* split on the colon */
        String[] parts = variableName.split(":");
        variableName = parts[0];
        variablePattern = parts[1];
      }

      /* look for nested expressions */
      if (variableName.contains("{")) {
        /* nested, literal */
        return null;
      }
    }

    /* check for a modifier */
    if (PATH_STYLE_MODIFIER.equalsIgnoreCase(modifier)) {
      return new PathStyleExpression(variableName, variablePattern);
    }

    /* default to simple */
    return new SimpleExpression(variableName, variablePattern);
  }

  private static String stripBraces(String expression) {
    if (expression == null) {
      return null;
    }
    if (expression.startsWith("{") && expression.endsWith("}")) {
      return expression.substring(1, expression.length() - 1);
    }
    return expression;
  }

  /**
   * Expression that adheres to Simple String Expansion as outlined in <a
   * href="https://tools.ietf.org/html/rfc6570#section-3.2.2>Simple String Expansion (Level 1)</a>
   */
  static class SimpleExpression extends Expression {

    private static final String DEFAULT_SEPARATOR = ",";
    protected String separator = DEFAULT_SEPARATOR;
    private boolean nameRequired = false;

    SimpleExpression(String name, String pattern) {
      super(name, pattern);
    }

    SimpleExpression(String name, String pattern, String separator, boolean nameRequired) {
      this(name, pattern);
      this.separator = separator;
      this.nameRequired = nameRequired;
    }

    protected String encode(Object value) {
      return UriUtils.encode(value.toString(), Util.UTF_8);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String expand(Object variable, boolean encode) {
      StringBuilder expanded = new StringBuilder();
      if (Iterable.class.isAssignableFrom(variable.getClass())) {
        expanded.append(this.expandIterable((Iterable<?>) variable));
      } else if (Map.class.isAssignableFrom(variable.getClass())) {
        expanded.append(this.expandMap((Map<String, ?>) variable));
      } else {
        if (this.nameRequired) {
          expanded.append(this.encode(this.getName()))
              .append("=");
        }
        expanded.append((encode) ? encode(variable) : variable);
      }

      /* return the string value of the variable */
      String result = expanded.toString();
      if (!this.matches(result)) {
        throw new IllegalArgumentException("Value " + expanded
            + " does not match the expression pattern: " + this.getPattern());
      }
      return result;
    }

    protected String expandIterable(Iterable<?> values) {
      StringBuilder result = new StringBuilder();
      for (Object value : values) {
        if (value == null) {
          /* skip */
          continue;
        }

        /* expand the value */
        String expanded = this.encode(value);
        if (expanded.isEmpty()) {
          /* always append the separator */
          result.append(this.separator);
        } else {
          if (result.length() != 0) {
            if (!result.toString().equalsIgnoreCase(this.separator)) {
              result.append(this.separator);
            }
          }
          if (this.nameRequired) {
            result.append(this.encode(this.getName()))
                .append("=");
          }
          result.append(expanded);
        }
      }

      /* return the expanded value */
      return result.toString();
    }

    protected String expandMap(Map<String, ?> values) {
      StringBuilder result = new StringBuilder();

      for (Entry<String, ?> entry : values.entrySet()) {
        StringBuilder expanded = new StringBuilder();
        String name = this.encode(entry.getKey());
        String value = this.encode(entry.getValue().toString());

        expanded.append(name)
            .append("=");
        if (!value.isEmpty()) {
          expanded.append(value);
        }

        if (result.length() != 0) {
          result.append(this.separator);
        }

        result.append(expanded);
      }
      return result.toString();
    }
  }

  public static class PathStyleExpression extends SimpleExpression implements Expander {

    PathStyleExpression(String name, String pattern) {
      super(name, pattern, ";", true);
    }

    @Override
    protected String expand(Object variable, boolean encode) {
      return this.separator + super.expand(variable, encode);
    }

    @Override
    public String expand(Object value) {
      return this.expand(value, true);
    }
  }
}
