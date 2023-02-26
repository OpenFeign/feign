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
package feign.soap;

import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;
import static org.junit.Assert.assertEquals;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPMessage;
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

@SuppressWarnings("deprecation")
public class SOAPCodecTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void encodesSoap() {
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
  public void encodesSoapWithCustomJAXBMarshallerEncoding() {
    JAXBContextFactory jaxbContextFactory =
        new JAXBContextFactory.Builder().withMarshallerJAXBEncoding("UTF-16").build();

    Encoder encoder = new SOAPEncoder.Builder()
        // .withWriteXmlDeclaration(true)
        .withJAXBContextFactory(jaxbContextFactory)
        .withCharsetEncoding(StandardCharsets.UTF_16)
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
    byte[] utf16Bytes = soapEnvelop.getBytes(StandardCharsets.UTF_16LE);
    assertThat(template).hasBody(utf16Bytes);
  }


  @Test
  public void encodesSoapWithCustomJAXBSchemaLocation() {
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
  public void encodesSoapWithCustomJAXBNoSchemaLocation() {
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
  public void encodesSoapWithCustomJAXBFormattedOuput() {
    Encoder encoder = new SOAPEncoder.Builder().withFormattedOutput(true)
        .withJAXBContextFactory(new JAXBContextFactory.Builder()
            .build())
        .build();

    GetPrice mock = new GetPrice();
    mock.item = new Item();
    mock.item.value = "Apples";

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, GetPrice.class, template);

    assertThat(template).hasBody(
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" + System.lineSeparator() +
            "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + System.lineSeparator() +
            "    <SOAP-ENV:Header/>" + System.lineSeparator() +
            "    <SOAP-ENV:Body>" + System.lineSeparator() +
            "        <GetPrice>" + System.lineSeparator() +
            "            <Item>Apples</Item>" + System.lineSeparator() +
            "        </GetPrice>" + System.lineSeparator() +
            "    </SOAP-ENV:Body>" + System.lineSeparator() +
            "</SOAP-ENV:Envelope>" + System.lineSeparator() +
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
  public void decodesSoapWithSchemaOnEnvelope() throws Exception {
    GetPrice mock = new GetPrice();
    mock.item = new Item();
    mock.item.value = "Apples";

    String mockSoapEnvelop = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
        + "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" "
        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://apihost/schema.xsd\" "
        + "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">"
        + "<SOAP-ENV:Header/>"
        + "<SOAP-ENV:Body>"
        + "<GetPrice>"
        + "<Item xsi:type=\"xsd:string\">Apples</Item>"
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

    SOAPDecoder decoder = new SOAPDecoder.Builder()
        .withJAXBContextFactory(new JAXBContextFactory.Builder().build())
        .useFirstChild()
        .build();

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
        .headers(Collections.emptyMap())
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
        .headers(Collections.emptyMap())
        .body(template.body())
        .build();

    new SOAPDecoder(new JAXBContextFactory.Builder().build()).decode(response, Box.class);

  }

  /**
   * Enabled via {@link feign.Feign.Builder#dismiss404()}
   */
  @Test
  public void notFoundDecodesToNull() throws Exception {
    Response response = Response.builder()
        .status(404)
        .reason("NOT FOUND")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .build();
    assertThat((byte[]) new SOAPDecoder(new JAXBContextFactory.Builder().build())
        .decode(response, byte[].class)).isEmpty();
  }

  @Test
  public void changeSoapProtocolAndSetHeader() {
    Encoder encoder =
        new ChangedProtocolAndHeaderSOAPEncoder(new JAXBContextFactory.Builder().build());

    GetPrice mock = new GetPrice();
    mock.item = new Item();
    mock.item.value = "Apples";

    RequestTemplate template = new RequestTemplate();
    encoder.encode(mock, GetPrice.class, template);

    String soapEnvelop = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
        "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\">" +
        "<env:Header>" +
        (System.getProperty("java.version").startsWith("1.8")
            ? "<wss:Security xmlns:wss=\"http://schemas.xmlsoap.org/ws/2002/12/secext\">"
            : "<wss:Security xmlns=\"http://schemas.xmlsoap.org/ws/2002/12/secext\" xmlns:wss=\"http://schemas.xmlsoap.org/ws/2002/12/secext\">")
        +
        "<wss:UsernameToken>" +
        "<wss:Username>test</wss:Username>" +
        "<wss:Password>test</wss:Password>" +
        "</wss:UsernameToken>" +
        "</wss:Security>" +
        "</env:Header>" +
        "<env:Body>" +
        "<GetPrice>" +
        "<Item>Apples</Item>" +
        "</GetPrice>" +
        "</env:Body>" +
        "</env:Envelope>";
    assertThat(template).hasBody(soapEnvelop);
  }

  static class ChangedProtocolAndHeaderSOAPEncoder extends SOAPEncoder {

    public ChangedProtocolAndHeaderSOAPEncoder(JAXBContextFactory jaxbContextFactory) {
      super(new SOAPEncoder.Builder()
          .withSOAPProtocol("SOAP 1.2 Protocol")
          .withJAXBContextFactory(jaxbContextFactory));
    }

    @Override
    protected SOAPMessage modifySOAPMessage(SOAPMessage soapMessage) throws SOAPException {
      SOAPFactory soapFactory = SOAPFactory.newInstance();
      String uri = "http://schemas.xmlsoap.org/ws/2002/12/secext";
      String prefix = "wss";
      SOAPElement security = soapFactory.createElement("Security", prefix, uri);
      SOAPElement usernameToken = soapFactory.createElement("UsernameToken", prefix, uri);
      usernameToken.addChildElement("Username", prefix, uri).setValue("test");
      usernameToken.addChildElement("Password", prefix, uri).setValue("test");
      security.addChildElement(usernameToken);
      soapMessage.getSOAPHeader().addChildElement(security);
      return soapMessage;
    }
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
