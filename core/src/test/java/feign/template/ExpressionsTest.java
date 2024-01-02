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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ExpressionsTest {

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
