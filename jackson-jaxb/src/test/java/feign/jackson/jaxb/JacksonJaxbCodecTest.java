package feign.jackson.jaxb;

import org.junit.Test;

import java.util.Collection;
import java.util.Collections;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import feign.RequestTemplate;
import feign.Response;

import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;

public class JacksonJaxbCodecTest {

  @Test
  public void encodeTest() {
    JacksonJaxbJsonEncoder encoder = new JacksonJaxbJsonEncoder();
    RequestTemplate template = new RequestTemplate();

    encoder.encode(new MockObject("Test"), MockObject.class, template);

    assertThat(template).hasBody("{\"value\":\"Test\"}");
  }

  @Test
  public void decodeTest() throws Exception {
    Response response =
        Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), "{\"value\":\"Test\"}", UTF_8);
    JacksonJaxbJsonDecoder decoder = new JacksonJaxbJsonDecoder();

    assertThat(decoder.decode(response, MockObject.class))
        .isEqualTo(new MockObject("Test"));
  }

  /** Enabled via {@link feign.Feign.Builder#decode404()} */
  @Test
  public void notFoundDecodesToEmpty() throws Exception {
    Response response = Response.create(404, "NOT FOUND",
        Collections.<String, Collection<String>>emptyMap(),
        (byte[]) null);
    assertThat((byte[]) new JacksonJaxbJsonDecoder().decode(response, byte[].class)).isEmpty();
  }

  @XmlRootElement
  @XmlAccessorType(XmlAccessType.FIELD)
  static class MockObject {

    @XmlElement
    private String value;

    MockObject() {
    }

    MockObject(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof MockObject) {
        MockObject other = (MockObject) obj;
        return value.equals(other.value);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return value != null ? value.hashCode() : 0;
    }
  }
}
