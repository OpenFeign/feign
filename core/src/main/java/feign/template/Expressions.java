/*
 * Copyright 2012-2023 The Feign Authors
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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Expressions {

  private static final int MAX_EXPRESSION_LENGTH = 10000;

  private static final String PATH_STYLE_OPERATOR = ";";
  /**
   * Literals may be present and preceded the expression.
   *
   * The expression part must start with a '{' and end with a '}'. The contents of the expression
   * may start with an RFC Operator or the operators reserved by the rfc: Level 2 Operators: '+' and
   * '#' Level 3 Operators: '.' and '/' and ';' and '?' and '&' Reserved Operators: '=' and ',' and
   * '!' and '@' and '|'
   *
   * The RFC specifies that '{' or '}' or '(' or ')' or'$' is are illegal characters. Feign does not
   * honor this portion of the RFC Expressions allow '$' characters for Collection expansions, and
   * all other characters are legal as a regular expression may be passed as a Value Modifier in
   * Feign
   *
   * This is not a complete implementation of the rfc
   *
   * <a href="https://www.rfc-editor.org/rfc/rfc6570#section-2.2">RFC 6570 Expressions</a>
   */
  static final Pattern EXPRESSION_PATTERN =
      Pattern.compile("^(\\{([+#./;?&=,!@|]?)(.+)\\})$");

  // Partially From:
  // https://stackoverflow.com/questions/29494608/regex-for-uri-templates-rfc-6570-wanted -- I
  // suspect much of the codebase could be refactored around the example regex there
  /**
   * A pattern for matching possible variable names.
   *
   * This pattern accepts characters allowed in RFC 6570 Section 2.3 It also allows the characters
   * feign has allowed in the past "[]-$"
   *
   * The RFC specifies that a variable name followed by a ':' should be a max-length specification.
   * Feign deviates from the rfc in that the ':' value modifier is used to mark a regular
   * expression.
   *
   */
  private static final Pattern VARIABLE_LIST_PATTERN = Pattern.compile(
      "(([\\w-\\[\\]$]|%[0-9A-Fa-f]{2})(\\.?([\\w-\\[\\]$]|%[0-9A-Fa-f]{2}))*(:.*|\\*)?)(,(([\\w-\\[\\]$]|%[0-9A-Fa-f]{2})(\\.?([\\w-\\[\\]$]|%[0-9A-Fa-f]{2}))*(:.*|\\*)?))*");

  public static Expression create(final String value) {

    /* remove the start and end braces */
    final String expression = stripBraces(value);
    if (expression == null || expression.isEmpty()) {
      throw new IllegalArgumentException("an expression is required.");
    }

    /* Check if the expression is too long */
    if (expression.length() > MAX_EXPRESSION_LENGTH) {
      throw new IllegalArgumentException(
          "expression is too long. Max length: " + MAX_EXPRESSION_LENGTH);
    }

    /* create a new regular expression matcher for the expression */
    String variableName = null;
    String variablePattern = null;
    String operator = null;
    Matcher matcher = EXPRESSION_PATTERN.matcher(value);
    if (matcher.matches()) {
      /* grab the operator */
      operator = matcher.group(2).trim();

      /* we have a valid variable expression, extract the name from the first group */
      variableName = matcher.group(3).trim();
      if (variableName.contains(":")) {
        /* split on the colon and ensure the size of parts array must be 2 */
        String[] parts = variableName.split(":", 2);
        variableName = parts[0];
        variablePattern = parts[1];
      }

      /* look for nested expressions */
      if (variableName.contains("{")) {
        /* nested, literal */
        return null;
      }
    }

    /* check for an operator */
    if (PATH_STYLE_OPERATOR.equalsIgnoreCase(operator)) {
      return new PathStyleExpression(variableName, variablePattern);
    }

    /* default to simple */
    return SimpleExpression.isSimpleExpression(value)
        ? new SimpleExpression(variableName, variablePattern)
        : null; // Return null if it can't be validated as a Simple Expression -- Probably a Literal
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
      } else if (Optional.class.isAssignableFrom(variable.getClass())) {
        Optional<?> optional = (Optional) variable;
        if (optional.isPresent()) {
          expanded.append(this.expand(optional.get(), encode));
        } else {
          if (!this.nameRequired) {
            return null;
          }
          expanded.append(this.encode(this.getName()))
              .append("=");
        }
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

    protected static boolean isSimpleExpression(String expressionCandidate) {
      final Matcher matcher = EXPRESSION_PATTERN.matcher(expressionCandidate);
      return matcher.matches()
          && matcher.group(2).isEmpty() // Simple Expressions do not support any special operators
          && VARIABLE_LIST_PATTERN.matcher(matcher.group(3)).matches();
    }
  }

  public static class PathStyleExpression extends SimpleExpression implements Expander {

    public PathStyleExpression(String name, String pattern) {
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

    @Override
    public String getValue() {
      if (this.getPattern() != null) {
        return "{" + this.separator + this.getName() + ":" + this.getName() + "}";
      }
      return "{" + this.separator + this.getName() + "}";
    }
  }
}
