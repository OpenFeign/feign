/*
 * Copyright 2014 Netflix, Inc.
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
package feign.jaxb;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import feign.RequestTemplate;
import feign.Response;
import feign.codec.Encoder;

import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;
import static org.junit.Assert.assertEquals;

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

    assertThat(template).hasBody(
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
                                 "standalone=\"yes\"?><mockObject xsi:schemaLocation=\"http://apihost "
                                 +
                                 "http://apihost/schema.xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                                 +
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

    assertThat(template).hasBody("<?xml version=\"1.0\" encoding=\"UTF-8\" " +
                                 "standalone=\"yes\"?><mockObject xsi:noNamespaceSchemaLocation=\"http://apihost/schema.xsd\" "
                                 +
                                 "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
                                 "<value>Test</value></mockObject>");
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

    String NEWLINE = System.getProperty("line.separator");

    assertThat(template).hasBody(
        new StringBuilder().append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            .append(NEWLINE)
            .append("<mockObject>")
            .append(NEWLINE)
            .append("    <value>Test</value>")
            .append(NEWLINE)
            .append("</mockObject>")
            .append(NEWLINE)
            .toString());
  }

  @Test
  public void decodesXml() throws Exception {
    MockObject mock = new MockObject();
    mock.value = "Test";

    String mockXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><mockObject>"
                     + "<value>Test</value></mockObject>";

    Response
        response =
        Response
            .create(200, "OK", Collections.<String, Collection<String>>emptyMap(), mockXml, UTF_8);

    JAXBDecoder decoder = new JAXBDecoder(new JAXBContextFactory.Builder().build());

    assertEquals(mock, decoder.decode(response, MockObject.class));
  }

  @Test
  public void doesntDecodeParameterizedTypes() throws Exception {
    thrown.expect(UnsupportedOperationException.class);
    thrown.expectMessage(
        "JAXB only supports decoding raw types. Found java.util.Map<java.lang.String, ?>");

    class ParameterizedHolder {

      Map<String, ?> field;
    }
    Type parameterized = ParameterizedHolder.class.getDeclaredField("field").getGenericType();

    Response
        response =
        Response
            .create(200, "OK", Collections.<String, Collection<String>>emptyMap(), "<foo/>", UTF_8);

    new JAXBDecoder(new JAXBContextFactory.Builder().build()).decode(response, parameterized);
  }

  /** Enabled via {@link feign.Feign.Builder#decode404()} */
  @Test
  public void notFoundDecodesToEmpty() throws Exception {
    Response response = Response.create(404, "NOT FOUND",
        Collections.<String, Collection<String>>emptyMap(),
        (byte[]) null);
    assertThat((byte[]) new JAXBDecoder(new JAXBContextFactory.Builder().build())
        .decode(response, byte[].class)).isEmpty();
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
