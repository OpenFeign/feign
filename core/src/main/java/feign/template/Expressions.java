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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import feign.Util;

public final class Expressions {
  private static Map<Pattern, Class<? extends Expression>> expressions;

  static {
    expressions = new LinkedHashMap<>();

    /*
     * basic pattern for variable names. this is compliant with RFC 6570 Simple Expressions ONLY
     * with the following additional values allowed without required pct-encoding:
     *
     * - brackets - dashes
     *
     * see https://tools.ietf.org/html/rfc6570#section-2.3 for more information.
     */

    expressions.put(Pattern.compile("^([+#./;?&]?)(.*)$"),
        SimpleExpression.class);
  }

  public static Expression create(final String value) {

    /* remove the start and end braces */
    final String expression = stripBraces(value);
    if (expression == null || expression.isEmpty()) {
      throw new IllegalArgumentException("an expression is required.");
    }

    Optional<Entry<Pattern, Class<? extends Expression>>> matchedExpressionEntry =
        expressions.entrySet()
            .stream()
            .filter(entry -> entry.getKey().matcher(expression).matches())
            .findFirst();

    if (!matchedExpressionEntry.isPresent()) {
      /* not a valid expression */
      return null;
    }

    Entry<Pattern, Class<? extends Expression>> matchedExpression = matchedExpressionEntry.get();
    Pattern expressionPattern = matchedExpression.getKey();

    /* create a new regular expression matcher for the expression */
    String variableName = null;
    String variablePattern = null;
    Matcher matcher = expressionPattern.matcher(expression);
    if (matcher.matches()) {
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

    SimpleExpression(String expression, String pattern) {
      super(expression, pattern);
    }

    String encode(Object value) {
      return UriUtils.encode(value.toString(), Util.UTF_8);
    }

    @Override
    String expand(Object variable, boolean encode) {
      StringBuilder expanded = new StringBuilder();
      if (Iterable.class.isAssignableFrom(variable.getClass())) {
        expanded.append(this.expandIterable((Iterable<?>) variable));
      } else {
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


    private String expandIterable(Iterable<?> values) {
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
          result.append(",");
        } else {
          if (result.length() != 0) {
            if (!result.toString().equalsIgnoreCase(",")) {
              result.append(",");
            }
          }
          result.append(expanded);
        }
      }

      /* return the expanded value */
      return result.toString();
    }
  }
}
