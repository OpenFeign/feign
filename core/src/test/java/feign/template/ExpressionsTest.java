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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatObject;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExpressionsTest {

  @AfterEach
  void clearMaxExpressionLengthProperty() {
    System.clearProperty(Expressions.MAX_EXPRESSION_LENGTH_PROPERTY);
  }

  @Test
  void tooLongExpressionFailsWithDefaultLimit() {
    String tooLong = "{" + "a".repeat(10001) + "}";
    assertThatThrownBy(() -> Expressions.create(tooLong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expression is too long");
  }

  @Test
  void maxExpressionLengthIsConfigurable() {
    System.setProperty(Expressions.MAX_EXPRESSION_LENGTH_PROPERTY, "5");
    assertThatThrownBy(() -> Expressions.create("{foobar}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Max length: 5");
  }

  @Test
  void lengthCheckCanBeDisabled() {
    // An expression well beyond the default 10000 limit, expressed as a name plus a regular
    // expression value modifier so the disabled length check is exercised in isolation.
    String longExpression = "{name:" + "a".repeat(15000) + "}";
    assertThatThrownBy(() -> Expressions.create(longExpression))
        .as("guarded by default limit")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expression is too long");

    System.setProperty(Expressions.MAX_EXPRESSION_LENGTH_PROPERTY, "0");
    assertThatNoException()
        .as("length check disabled")
        .isThrownBy(() -> Expressions.create(longExpression));
  }

  @Test
  void simpleExpression() {
    Expression expression = Expressions.create("{foo}");
    assertThat(expression).isNotNull();
    String expanded = expression.expand(Collections.singletonMap("foo", "bar"), false);
    assertThat(expanded).isEqualToIgnoringCase("foo=bar");
  }

  @Test
  void malformedExpression() {
    String[] malformedStrings = {"{:}", "{str1:}", "{str1:{:}", "{str1:{str2:}"};

    for (String malformed : malformedStrings) {
      try {
        Expressions.create(malformed);
      } catch (Exception e) {
        assertThatObject(e).isNotInstanceOf(ArrayIndexOutOfBoundsException.class);
      }
    }
  }

  @Test
  void invalidValueModifierIsTreatedAsLiteral() {
    // The text after ':' is compiled as a regex; an invalid one must not escape as a
    // PatternSyntaxException, the chunk is a literal instead (Expressions.create returns null).
    assertThatNoException().isThrownBy(() -> Expressions.create("{range:[1:10}"));
    assertThat(Expressions.create("{a:[}")).isNull();
    assertThat(Expressions.create("{a:(}")).isNull();

    // a valid value modifier still produces an expression
    assertThat(Expressions.create("{id:[0-9]+}")).isNotNull();
  }

  @Test
  void malformedBodyTemplate() {
    String bodyTemplate = "{" + "a".repeat(65536) + "}";

    try {
      BodyTemplate template = BodyTemplate.create(bodyTemplate);
    } catch (Throwable e) {
      assertThatObject(e).isNotInstanceOf(StackOverflowError.class);
    }
  }

  @Test
  void androidCompatibility() {
    // To match close brace on Android, it must be escaped due to the simpler ICU regex engine
    String pattern = Expressions.EXPRESSION_PATTERN.pattern();
    assertThat(pattern.contains("}")).isEqualTo(pattern.contains("\\}"));
  }
}
