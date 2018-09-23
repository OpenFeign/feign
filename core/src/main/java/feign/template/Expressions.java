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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Expressions {
  private static Map<Pattern, Class<? extends Expression>> expressions;

  static {
    expressions = new LinkedHashMap<>();
    expressions.put(Pattern.compile("\\+(\\w[-\\w.]*[ ]*)(:(.+))?"), ReservedExpression.class);
    expressions.put(Pattern.compile("(\\w[-\\w.]*[ ]*)(:(.+))?"), SimpleExpression.class);
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
    Class<? extends Expression> expressionType = matchedExpression.getValue();

    /* create a new regular expression matcher for the expression */
    String variableName = null;
    String variablePattern = null;
    Matcher matcher = expressionPattern.matcher(expression);
    if (matcher.matches()) {
      /* we have a valid variable expression, extract the name from the first group */
      variableName = matcher.group(1).trim();
      if (matcher.group(2) != null && matcher.group(3) != null) {
        /* this variable contains an optional pattern */
        variablePattern = matcher.group(3);
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
   * Expression that does not encode reserved characters. This expression adheres to RFC 6570
   * <a href="https://tools.ietf.org/html/rfc6570#section-3.2.3">Reserved Expansion (Level 2)</a>
   * specification.
   */
  public static class ReservedExpression extends SimpleExpression {
    private final String RESERVED_CHARACTERS = ":/?#[]@!$&\'()*+,;=";

    ReservedExpression(String expression, String pattern) {
      super(expression, pattern);
    }

    @Override
    String encode(Object value) {
      return UriUtils.encodeReserved(value.toString(), RESERVED_CHARACTERS, Util.UTF_8);
    }
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
        List<String> items = new ArrayList<>();
        for (Object item : ((Iterable) variable)) {
          items.add((encode) ? encode(item) : item.toString());
        }
        expanded.append(String.join(",", items));
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
  }
}
