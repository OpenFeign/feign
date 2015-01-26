package feign.assertj;

import com.squareup.okhttp.mockwebserver.RecordedRequest;
import org.assertj.core.api.Assertions;

public class MockWebServerAssertions extends Assertions {
  public static RecordedRequestAssert assertThat(RecordedRequest actual) {
    return new RecordedRequestAssert(actual);
  }
}
