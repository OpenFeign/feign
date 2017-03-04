package feign.codec;

import feign.Response;
import feign.Util;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class InterceptorDecoderTest {

  @Test
  public void testMapFunctionIsAppliedBeforeDelegate() throws IOException {
    InterceptorDecoder decoder = new InterceptorDecoder(new StringDecoder(), upperCaseResponseMapper());
    String output = (String) decoder.decode(responseWithText("response"), String.class);

    assertThat(output).isEqualTo("RESPONSE");
  }

  private InterceptorDecoder.ResponseMapper upperCaseResponseMapper() {
    return new InterceptorDecoder.ResponseMapper() {
      @Override
      public Response map(Response response, Type type) {
        try {
          return response
              .toBuilder()
              .body(Util.toString(response.body().asReader()).toUpperCase().getBytes())
              .build();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  private Response responseWithText(String text) {
    return Response.builder()
        .body(text, Util.UTF_8)
        .status(200)
        .headers(new HashMap<String, Collection<String>>())
        .build();
  }
}
