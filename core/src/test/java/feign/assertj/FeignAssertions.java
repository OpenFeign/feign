package feign.assertj;

import feign.RequestTemplate;
import org.assertj.core.api.Assertions;

public class FeignAssertions extends Assertions {
  public static RequestTemplateAssert assertThat(RequestTemplate actual) {
    return new RequestTemplateAssert(actual);
  }
}
