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

import static feign.Util.UTF_8;
import static org.junit.Assert.assertEquals;

import com.google.common.reflect.TypeToken;
import dagger.Module;
import dagger.ObjectGraph;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.Encoder;
import java.util.Collection;
import java.util.Collections;
import javax.inject.Inject;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.junit.Test;

public class JAXBModuleTest {
  @Module(includes = JAXBModule.class, injects = EncoderAndDecoderBindings.class)
  static class EncoderAndDecoderBindings {
    @Inject Encoder encoder;

    @Inject Decoder decoder;
  }

  @Module(includes = JAXBModule.class, injects = EncoderBindings.class)
  static class EncoderBindings {
    @Inject Encoder encoder;
  }

  @Module(includes = JAXBModule.class, injects = DecoderBindings.class)
  static class DecoderBindings {
    @Inject Decoder decoder;
  }

  @Test
  public void providesEncoderDecoder() throws Exception {
    EncoderAndDecoderBindings bindings = new EncoderAndDecoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    assertEquals(JAXBEncoder.class, bindings.encoder.getClass());
    assertEquals(JAXBDecoder.class, bindings.decoder.getClass());
  }

  @XmlRootElement
  @XmlAccessorType(XmlAccessType.FIELD)
  static class MockObject {

    @XmlElement private String value;

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MockObject that = (MockObject) o;

      if (value != null ? !value.equals(that.value) : that.value != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return value != null ? value.hashCode() : 0;
    }
  }

  @Test
  public void encodesXml() throws Exception {
    EncoderBindings bindings = new EncoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    MockObject mock = new MockObject();
    mock.setValue("Test");

    RequestTemplate template = new RequestTemplate();
    bindings.encoder.encode(mock, template);

    assertEquals(
        "<?xml version=\"1.0\" encoding=\"UTF-8\""
            + " standalone=\"yes\"?><mockObject><value>Test</value></mockObject>",
        new String(template.body(), UTF_8));
  }

  @Test
  public void encodesXmlWithCustomJAXBEncoding() throws Exception {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder().withMarshallerJAXBEncoding("UTF-16").build();

    JAXBModule jaxbModule = new JAXBModule(jaxbContextFactory);
    Encoder encoder = jaxbModule.encoder(new JAXBEncoder(jaxbContextFactory));

    MockObject mock = new MockObject();
    mock.setValue("Test");

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, template);

    assertEquals(
        "<?xml version=\"1.0\" encoding=\"UTF-16\" "
            + "standalone=\"yes\"?><mockObject><value>Test</value></mockObject>",
        new String(template.body(), UTF_8));
  }

  @Test
  public void encodesXmlWithCustomJAXBSchemaLocation() throws Exception {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder()
            .withMarshallerSchemaLocation("http://apihost http://apihost/schema.xsd")
            .build();

    JAXBModule jaxbModule = new JAXBModule(jaxbContextFactory);
    Encoder encoder = jaxbModule.encoder(new JAXBEncoder(jaxbContextFactory));

    MockObject mock = new MockObject();
    mock.setValue("Test");

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, template);

    assertEquals(
        "<?xml version=\"1.0\" encoding=\"UTF-8\" "
            + "standalone=\"yes\"?><mockObject xsi:schemaLocation=\"http://apihost "
            + "http://apihost/schema.xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
            + "<value>Test</value></mockObject>",
        new String(template.body(), UTF_8));
  }

  @Test
  public void encodesXmlWithCustomJAXBNoNamespaceSchemaLocation() throws Exception {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder()
            .withMarshallerNoNamespaceSchemaLocation("http://apihost/schema.xsd")
            .build();

    JAXBModule jaxbModule = new JAXBModule(jaxbContextFactory);
    Encoder encoder = jaxbModule.encoder(new JAXBEncoder(jaxbContextFactory));

    MockObject mock = new MockObject();
    mock.setValue("Test");

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, template);

    assertEquals(
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><mockObject"
            + " xsi:noNamespaceSchemaLocation=\"http://apihost/schema.xsd\" "
            + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
            + "<value>Test</value></mockObject>",
        new String(template.body(), UTF_8));
  }

  @Test
  public void encodesXmlWithCustomJAXBFormattedOutput() {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder().withMarshallerFormattedOutput(true).build();

    JAXBModule jaxbModule = new JAXBModule(jaxbContextFactory);
    Encoder encoder = jaxbModule.encoder(new JAXBEncoder(jaxbContextFactory));

    MockObject mock = new MockObject();
    mock.setValue("Test");

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, template);

    String NEWLINE = System.getProperty("line.separator");

    StringBuilder expectedXml = new StringBuilder();
    expectedXml
        .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        .append(NEWLINE)
        .append("<mockObject>")
        .append(NEWLINE)
        .append("    <value>Test</value>")
        .append(NEWLINE)
        .append("</mockObject>")
        .append(NEWLINE);

    assertEquals(expectedXml.toString(), new String(template.body(), UTF_8));
  }

  @Test
  public void decodesXml() throws Exception {
    DecoderBindings bindings = new DecoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    MockObject mock = new MockObject();
    mock.setValue("Test");

    String mockXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><mockObject>"
            + "<value>Test</value></mockObject>";

    Response response =
        Response.create(
            200, "OK", Collections.<String, Collection<String>>emptyMap(), mockXml, UTF_8);

    assertEquals(mock, bindings.decoder.decode(response, new TypeToken<MockObject>() {}.getType()));
  }
}
