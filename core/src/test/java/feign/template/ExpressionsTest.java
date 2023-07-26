package feign.template;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ExpressionsTest {

  @Test
  public void simpleExpression() {
    Expression expression = Expressions.create("{foo}");
    assertThat(expression).isNotNull();
    String expanded = expression.expand(Collections.singletonMap("foo", "bar"), false);
    assertThat(expanded).isEqualToIgnoringCase("foo=bar");
  }

  @Test
  public void androidCompatibility() {
    // To match close brace on Android, it must be escaped due to the simpler ICU regex engine
    String pattern = Expressions.EXPRESSION_PATTERN.pattern();
    assertThat(pattern.contains("}")).isEqualTo(pattern.contains("\\}"));
  }

}
