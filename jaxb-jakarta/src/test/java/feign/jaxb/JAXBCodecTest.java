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
package feign.jaxb;

import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import jakarta.xml.bind.MarshalException;
import jakarta.xml.bind.UnmarshalException;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("deprecation")
public class JAXBCodecTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void encodesXml() throws Exception {
    MockObject mock = new MockObject();
    mock.value = "Test";

    RequestTemplate template = new RequestTemplate();
    new JAXBEncoder(new JAXBContextFactory.Builder().build())
        .encode(mock, MockObject.class, template);

    assertThat(template)
        .hasBody(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><mockObject><value>Test</value></mockObject>");
  }

  @Test
  public void doesntEncodeParameterizedTypes() throws Exception {
    thrown.expect(UnsupportedOperationException.class);
    thrown.expectMessage(
        "JAXB only supports encoding raw types. Found java.util.Map<java.lang.String, ?>");

    class ParameterizedHolder {

      Map<String, ?> field;
    }
    Type parameterized = ParameterizedHolder.class.getDeclaredField("field").getGenericType();

    RequestTemplate template = new RequestTemplate();
    new JAXBEncoder(new JAXBContextFactory.Builder().build())
        .encode(Collections.emptyMap(), parameterized, template);
  }

  @Test
  public void encodesXmlWithCustomJAXBEncoding() throws Exception {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder().withMarshallerJAXBEncoding("UTF-16").build();

    Encoder encoder = new JAXBEncoder(jaxbContextFactory);

    MockObject mock = new MockObject();
    mock.value = "Test";

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, MockObject.class, template);

    assertThat(template).hasBody("<?xml version=\"1.0\" encoding=\"UTF-16\" "
        + "standalone=\"yes\"?><mockObject><value>Test</value></mockObject>");
  }

  @Test
  public void encodesXmlWithCustomJAXBSchemaLocation() throws Exception {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder()
            .withMarshallerSchemaLocation("http://apihost http://apihost/schema.xsd")
            .build();

    Encoder encoder = new JAXBEncoder(jaxbContextFactory);

    MockObject mock = new MockObject();
    mock.value = "Test";

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, MockObject.class, template);

    assertThat(template).hasBody("<?xml version=\"1.0\" encoding=\"UTF-8\" " +
        "standalone=\"yes\"?><mockObject xsi:schemaLocation=\"http://apihost " +
        "http://apihost/schema.xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
        "<value>Test</value></mockObject>");
  }

  @Test
  public void encodesXmlWithCustomJAXBNoNamespaceSchemaLocation() throws Exception {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder()
            .withMarshallerNoNamespaceSchemaLocation("http://apihost/schema.xsd").build();

    Encoder encoder = new JAXBEncoder(jaxbContextFactory);

    MockObject mock = new MockObject();
    mock.value = "Test";

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, MockObject.class, template);

    assertThat(template)
        .hasBody(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" "
                + "standalone=\"yes\"?><mockObject xsi:noNamespaceSchemaLocation=\"http://apihost/schema.xsd\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<value>Test</value></mockObject>");
  }

  @Test
  public void encodesXmlWithCustomJAXBFormattedOutput() {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder().withMarshallerFormattedOutput(true).build();

    Encoder encoder = new JAXBEncoder(jaxbContextFactory);

    MockObject mock = new MockObject();
    mock.value = "Test";

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, MockObject.class, template);

    // RequestTemplate always expects a UNIX style newline.
    assertThat(template).hasBody(
        new StringBuilder().append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            .append("\n")
            .append("<mockObject>")
            .append("\n")
            .append("    <value>Test</value>")
            .append("\n")
            .append("</mockObject>")
            .append("\n")
            .toString());
  }

  @Test
  public void decodesXml() throws Exception {
    MockObject mock = new MockObject();
    mock.value = "Test";

    String mockXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><mockObject>"
        + "<value>Test</value></mockObject>";

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(mockXml, UTF_8)
        .build();

    JAXBDecoder decoder = new JAXBDecoder(new JAXBContextFactory.Builder().build());

    assertEquals(mock, decoder.decode(response, MockObject.class));
  }

  @Test
  public void doesntDecodeParameterizedTypes() throws Exception {
    thrown.expect(feign.codec.DecodeException.class);
    thrown.expectMessage(
        "java.util.Map is an interface, and JAXB can't handle interfaces.\n"
            + "\tthis problem is related to the following location:\n"
            + "\t\tat java.util.Map");

    class ParameterizedHolder {

      Map<String, ?> field;
    }
    Type parameterized = ParameterizedHolder.class.getDeclaredField("field").getGenericType();

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.<String, Collection<String>>emptyMap())
        .body("<foo/>", UTF_8)
        .build();

    new JAXBDecoder(new JAXBContextFactory.Builder().build()).decode(response, parameterized);
  }

  @XmlRootElement
  static class Box<T> {

    @XmlElement
    private T t;

    public void set(T t) {
      this.t = t;
    }

  }

  @Test
  public void decodeAnnotatedParameterizedTypes() throws Exception {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder().withMarshallerFormattedOutput(true).build();

    Encoder encoder = new JAXBEncoder(jaxbContextFactory);

    Box<String> boxStr = new Box<>();
    boxStr.set("hello");
    Box<Box<String>> boxBoxStr = new Box<>();
    boxBoxStr.set(boxStr);
    RequestTemplate template = new RequestTemplate();
    encoder.encode(boxBoxStr, Box.class, template);

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.<String, Collection<String>>emptyMap())
        .body(template.body())
        .build();

    new JAXBDecoder(new JAXBContextFactory.Builder().build()).decode(response, Box.class);

  }

  /**
   * Enabled via {@link feign.Feign.Builder#dismiss404()}
   */
  @Test
  public void notFoundDecodesToEmpty() throws Exception {
    Response response = Response.builder()
        .status(404)
        .reason("NOT FOUND")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.<String, Collection<String>>emptyMap())
        .build();
    assertThat((byte[]) new JAXBDecoder(new JAXBContextFactory.Builder().build())
        .decode(response, byte[].class)).isEmpty();
  }

  @Test
  public void decodeThrowsExceptionWhenUnmarshallingFailsWithSetSchema() throws Exception {
    thrown.expect(DecodeException.class);
    thrown.expectCause(CoreMatchers.instanceOf(UnmarshalException.class));
    thrown.expectMessage("'Test' is not a valid value for 'integer'.");

    String mockXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><mockIntObject>"
        + "<value>Test</value></mockIntObject>";

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(mockXml, UTF_8)
        .build();

    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withUnmarshallerSchema(getMockIntObjSchema()).build();
    new JAXBDecoder(factory).decode(response, MockIntObject.class);
  }

  @Test
  public void decodesIgnoringErrorsWithEventHandler() throws Exception {
    String mockXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><mockIntObject>"
        + "<value>Test</value></mockIntObject>";

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(mockXml, UTF_8)
        .build();

    JAXBContextFactory factory =
        new JAXBContextFactory.Builder()
            .withUnmarshallerSchema(getMockIntObjSchema())
            .withUnmarshallerEventHandler(event -> true)
            .build();
    assertEquals(new MockIntObject(),
        new JAXBDecoder(factory).decode(response, MockIntObject.class));
  }

  @Test
  public void encodeThrowsExceptionWhenMarshallingFailsWithSetSchema() throws Exception {
    thrown.expect(EncodeException.class);
    thrown.expectCause(CoreMatchers.instanceOf(MarshalException.class));
    thrown.expectMessage("The content of element 'mockIntObject' is not complete.");

    JAXBContextFactory jaxbContextFactory = new JAXBContextFactory.Builder()
        .withMarshallerSchema(getMockIntObjSchema())
        .build();

    Encoder encoder = new JAXBEncoder(jaxbContextFactory);

    RequestTemplate template = new RequestTemplate();
    encoder.encode(new MockIntObject(), MockIntObject.class, template);
  }

  @Test
  public void encodesIgnoringErrorsWithEventHandler() throws Exception {
    JAXBContextFactory jaxbContextFactory = new JAXBContextFactory.Builder()
        .withMarshallerSchema(getMockIntObjSchema())
        .withMarshallerEventHandler(event -> true)
        .build();

    Encoder encoder = new JAXBEncoder(jaxbContextFactory);

    RequestTemplate template = new RequestTemplate();
    encoder.encode(new MockIntObject(), MockIntObject.class, template);
    assertThat(template).hasBody("<?xml version=\"1.0\" encoding=\"UTF-8\"" +
        " standalone=\"yes\"?><mockIntObject/>");
  }

  @XmlRootElement
  @XmlAccessorType(XmlAccessType.FIELD)
  static class MockIntObject {

    @XmlElement(required = true)
    private Integer value;

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      MockIntObject that = (MockIntObject) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

  }

  private static Schema getMockIntObjSchema() throws Exception {
    String schema = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
        + "<xs:schema version=\"1.0\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        + "<xs:element name=\"mockIntObject\" type=\"mockIntObject\"/><xs:complexType name=\"mockIntObject\">"
        + "<xs:sequence><xs:element name=\"value\" type=\"xs:int\"/></xs:sequence></xs:complexType>"
        + "</xs:schema>";
    SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    return schemaFactory.newSchema(new StreamSource(new StringReader(schema)));
  }

  @XmlRootElement
  @XmlAccessorType(XmlAccessType.FIELD)
  static class MockObject {

    @XmlElement
    private String value;

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
