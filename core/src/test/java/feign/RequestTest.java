package feign;

import feign.Request.HttpMethod;
import java.util.Collections;
import org.junit.Test;
import static feign.assertj.FeignAssertions.assertThat;

public class RequestTest {

  @Test
  public void testNullBodyShouldBeReplacedByEmptyConstant() {
    Request request = Request.create(HttpMethod.GET,
        "https://github.com/OpenFeign/feign",
        Collections.emptyMap(),
        (Request.Body) null,
        (RequestTemplate) null);
    assertThat(request.body()).isEqualTo(Request.Body.EMPTY.asBytes());
  }

}
