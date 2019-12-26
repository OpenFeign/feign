package feign.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.junit.Test;

public class BodyTemplateTest {

  @Test
  public void bodyTemplatesSupportJsonOnlyWhenEncoded() {
    String bodyTemplate = "%7B\"resize\": %7B\"method\": \"fit\",\"width\": {size},\"height\": {size}%7D%7D";
    BodyTemplate template = BodyTemplate.create(bodyTemplate);
    String expanded = template.expand(Collections.singletonMap("size", "100"));
    assertThat(expanded)
        .isEqualToIgnoringCase(
            "{\"resize\": {\"method\": \"fit\",\"width\": 100,\"height\": 100}}");
  }
}
