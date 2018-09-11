/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.soap;

import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import feign.codec.Encoder;
import feign.jaxb.JAXBContextFactory;
import feign.jaxb.JAXBDecoder;

public class SOAPCodecTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void encodesSoap() throws Exception {
    Encoder encoder = new SOAPEncoder.Builder()
        .withJAXBContextFactory(new JAXBContextFactory.Builder().build())
        .build();

    GetPrice mock = new GetPrice();
    mock.item = new Item();
    mock.item.value = "Apples";

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, GetPrice.class, template);

    String soapEnvelop = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
        "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
        "<SOAP-ENV:Header/>" +
        "<SOAP-ENV:Body>" +
        "<GetPrice>" +
        "<Item>Apples</Item>" +
        "</GetPrice>" +
        "</SOAP-ENV:Body>" +
        "</SOAP-ENV:Envelope>";
    assertThat(template).hasBody(soapEnvelop);
  }

  @Test
  public void doesntEncodeParameterizedTypes() throws Exception {
    thrown.expect(UnsupportedOperationException.class);
    thrown.expectMessage(
        "SOAP only supports encoding raw types. Found java.util.Map<java.lang.String, ?>");

    class ParameterizedHolder {

      @SuppressWarnings("unused")
      Map<String, ?> field;
    }
    Type parameterized = ParameterizedHolder.class.getDeclaredField("field").getGenericType();

    RequestTemplate template = new RequestTemplate();
    new SOAPEncoder(new JAXBContextFactory.Builder().build())
        .encode(Collections.emptyMap(), parameterized, template);
  }


  @Test
  public void encodesSoapWithCustomJAXBMarshallerEncoding() throws Exception {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder().withMarshallerJAXBEncoding("UTF-16").build();

    Encoder encoder = new SOAPEncoder.Builder()
        // .withWriteXmlDeclaration(true)
        .withJAXBContextFactory(jaxbContextFactory)
        .withCharsetEncoding(Charset.forName("UTF-16"))
        .build();

    GetPrice mock = new GetPrice();
    mock.item = new Item();
    mock.item.value = "Apples";

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, GetPrice.class, template);

    String soapEnvelop = "<?xml version=\"1.0\" encoding=\"UTF-16\" ?>" +
        "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
        "<SOAP-ENV:Header/>" +
        "<SOAP-ENV:Body>" +
        "<GetPrice>" +
        "<Item>Apples</Item>" +
        "</GetPrice>" +
        "</SOAP-ENV:Body>" +
        "</SOAP-ENV:Envelope>";
    byte[] utf16Bytes = soapEnvelop.getBytes("UTF-16LE");
    assertThat(template).hasBody(utf16Bytes);
  }


  @Test
  public void encodesSoapWithCustomJAXBSchemaLocation() throws Exception {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder()
            .withMarshallerSchemaLocation("http://apihost http://apihost/schema.xsd")
            .build();

    Encoder encoder = new SOAPEncoder(jaxbContextFactory);

    GetPrice mock = new GetPrice();
    mock.item = new Item();
    mock.item.value = "Apples";

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, GetPrice.class, template);

    assertThat(template).hasBody("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
        + "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<SOAP-ENV:Header/>"
        + "<SOAP-ENV:Body>"
        + "<GetPrice xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://apihost http://apihost/schema.xsd\">"
        + "<Item>Apples</Item>"
        + "</GetPrice>"
        + "</SOAP-ENV:Body>"
        + "</SOAP-ENV:Envelope>");
  }


  @Test
  public void encodesSoapWithCustomJAXBNoSchemaLocation() throws Exception {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder()
            .withMarshallerNoNamespaceSchemaLocation("http://apihost/schema.xsd")
            .build();

    Encoder encoder = new SOAPEncoder(jaxbContextFactory);

    GetPrice mock = new GetPrice();
    mock.item = new Item();
    mock.item.value = "Apples";

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, GetPrice.class, template);

    assertThat(template).hasBody("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
        + "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<SOAP-ENV:Header/>"
        + "<SOAP-ENV:Body>"
        + "<GetPrice xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://apihost/schema.xsd\">"
        + "<Item>Apples</Item>"
        + "</GetPrice>"
        + "</SOAP-ENV:Body>"
        + "</SOAP-ENV:Envelope>");
  }

  @Test
  public void encodesSoapWithCustomJAXBFormattedOuput() throws Exception {
    Encoder encoder = new SOAPEncoder.Builder().withFormattedOutput(true)
        .withJAXBContextFactory(new JAXBContextFactory.Builder()
            .build())
        .build();

    GetPrice mock = new GetPrice();
    mock.item = new Item();
    mock.item.value = "Apples";

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, GetPrice.class, template);

    assertThat(template).hasBody("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
        "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
        "    <SOAP-ENV:Header/>\n" +
        "    <SOAP-ENV:Body>\n" +
        "        <GetPrice>\n" +
        "            <Item>Apples</Item>\n" +
        "        </GetPrice>\n" +
        "    </SOAP-ENV:Body>\n" +
        "</SOAP-ENV:Envelope>\n" +
        "");
  }

  @Test
  public void decodesSoap() throws Exception {
    GetPrice mock = new GetPrice();
    mock.item = new Item();
    mock.item.value = "Apples";

    String mockSoapEnvelop = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
        + "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<SOAP-ENV:Header/>"
        + "<SOAP-ENV:Body>"
        + "<GetPrice xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://apihost/schema.xsd\">"
        + "<Item>Apples</Item>"
        + "</GetPrice>"
        + "</SOAP-ENV:Body>"
        + "</SOAP-ENV:Envelope>";

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(mockSoapEnvelop, UTF_8)
        .build();

    SOAPDecoder decoder = new SOAPDecoder(new JAXBContextFactory.Builder().build());

    assertEquals(mock, decoder.decode(response, GetPrice.class));
  }

  @Test
  public void decodesSoap1_2Protocol() throws Exception {
    GetPrice mock = new GetPrice();
    mock.item = new Item();
    mock.item.value = "Apples";

    String mockSoapEnvelop = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
        + "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<SOAP-ENV:Header/>"
        + "<SOAP-ENV:Body>"
        + "<GetPrice xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://apihost/schema.xsd\">"
        + "<Item>Apples</Item>"
        + "</GetPrice>"
        + "</SOAP-ENV:Body>"
        + "</SOAP-ENV:Envelope>";

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(mockSoapEnvelop, UTF_8)
        .build();

    SOAPDecoder decoder = new SOAPDecoder(new JAXBContextFactory.Builder().build());

    assertEquals(mock, decoder.decode(response, GetPrice.class));
  }


  @Test
  public void doesntDecodeParameterizedTypes() throws Exception {
    thrown.expect(feign.codec.DecodeException.class);
    thrown.expectMessage(
        "java.util.Map is an interface, and JAXB can't handle interfaces.\n"
            + "\tthis problem is related to the following location:\n"
            + "\t\tat java.util.Map");

    class ParameterizedHolder {

      @SuppressWarnings("unused")
      Map<String, ?> field;
    }
    Type parameterized = ParameterizedHolder.class.getDeclaredField("field").getGenericType();

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.<String, Collection<String>>emptyMap())
        .body("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
            + "<Envelope xmlns=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<Header/>"
            + "<Body>"
            + "<GetPrice>"
            + "<Item>Apples</Item>"
            + "</GetPrice>"
            + "</Body>"
            + "</Envelope>", UTF_8)
        .build();

    new SOAPDecoder(new JAXBContextFactory.Builder().build()).decode(response, parameterized);
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

    Encoder encoder = new SOAPEncoder(jaxbContextFactory);

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

    new SOAPDecoder(new JAXBContextFactory.Builder().build()).decode(response, Box.class);

  }

  /**
   * Enabled via {@link feign.Feign.Builder#decode404()}
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


  @XmlRootElement(name = "GetPrice")
  @XmlAccessorType(XmlAccessType.FIELD)
  static class GetPrice {

    @XmlElement(name = "Item")
    private Item item;

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof GetPrice) {
        GetPrice getPrice = (GetPrice) obj;
        return item.value.equals(getPrice.item.value);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return item.value != null ? item.value.hashCode() : 0;
    }
  }

  @XmlRootElement(name = "Item")
  @XmlAccessorType(XmlAccessType.FIELD)
  static class Item {

    @XmlValue
    private String value;

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Item) {
        Item item = (Item) obj;
        return value.equals(item.value);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return value != null ? value.hashCode() : 0;
    }
  }

}
