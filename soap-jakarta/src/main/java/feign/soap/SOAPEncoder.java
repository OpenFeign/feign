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

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.jaxb.JAXBContextFactory;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import org.w3c.dom.Document;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Encodes requests using SOAPMessage and JAXB for the body part. <br>
 *
 * <p>
 * Basic example with Feign.Builder:
 *
 * <pre>
 *
 * public interface MyApi {
 *
 *    &#64;RequestLine("POST /getObject")
 *    &#64;Headers({
 *      "SOAPAction: getObject",
 *      "Content-Type: text/xml"
 *    })
 *    MyJaxbObjectResponse getObject(MyJaxbObjectRequest request);
 *
 * }
 *
 * ...
 *
 * JAXBContextFactory jaxbFactory = new JAXBContextFactory.Builder()
 *     .withMarshallerJAXBEncoding("UTF-8")
 *     .withMarshallerSchemaLocation("http://apihost http://apihost/schema.xsd")
 *     .build();
 *
 * api = Feign.builder()
 *     .encoder(new SOAPEncoder(jaxbFactory))
 *     .target(MyApi.class, "http://api");
 *
 * ...
 *
 * try {
 *    api.getObject(new MyJaxbObjectRequest());
 * } catch (SOAPFaultException faultException) {
 *    log.info(faultException.getFault().getFaultString());
 * }
 * </pre>
 *
 * <p>
 * The JAXBContextFactory should be reused across requests as it caches the created JAXB contexts.
 */
public class SOAPEncoder implements Encoder {

  private static final String DEFAULT_SOAP_PROTOCOL = SOAPConstants.SOAP_1_1_PROTOCOL;

  private final boolean writeXmlDeclaration;
  private final boolean formattedOutput;
  private final Charset charsetEncoding;
  private final JAXBContextFactory jaxbContextFactory;
  private final String soapProtocol;

  public SOAPEncoder(Builder builder) {
    this.jaxbContextFactory = builder.jaxbContextFactory;
    this.writeXmlDeclaration = builder.writeXmlDeclaration;
    this.charsetEncoding = builder.charsetEncoding;
    this.soapProtocol = builder.soapProtocol;
    this.formattedOutput = builder.formattedOutput;
  }

  public SOAPEncoder(JAXBContextFactory jaxbContextFactory) {
    this.jaxbContextFactory = jaxbContextFactory;
    this.writeXmlDeclaration = true;
    this.formattedOutput = false;
    this.charsetEncoding = StandardCharsets.UTF_8;
    this.soapProtocol = DEFAULT_SOAP_PROTOCOL;
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template) {
    if (!(bodyType instanceof Class)) {
      throw new UnsupportedOperationException(
          "SOAP only supports encoding raw types. Found " + bodyType);
    }
    try {
      Document document =
          DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
      Marshaller marshaller = jaxbContextFactory.createMarshaller((Class<?>) bodyType);
      marshaller.marshal(object, document);
      SOAPMessage soapMessage = MessageFactory.newInstance(soapProtocol).createMessage();
      soapMessage.setProperty(
          SOAPMessage.WRITE_XML_DECLARATION, Boolean.toString(writeXmlDeclaration));
      soapMessage.setProperty(
          SOAPMessage.CHARACTER_SET_ENCODING, charsetEncoding.displayName());
      soapMessage.getSOAPBody().addDocument(document);

      soapMessage = modifySOAPMessage(soapMessage);

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      if (formattedOutput) {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        t.transform(new DOMSource(soapMessage.getSOAPPart()), new StreamResult(bos));
      } else {
        soapMessage.writeTo(bos);
      }
      template.body(bos.toString());
    } catch (SOAPException
        | JAXBException
        | ParserConfigurationException
        | IOException
        | TransformerFactoryConfigurationError
        | TransformerException e) {
      throw new EncodeException(e.toString(), e);
    }
  }

  /**
   * Override this in order to modify the SOAP message object before it's finally encoded. <br>
   * This might be useful to add SOAP Headers, which are not supported by this SOAPEncoder directly.
   * <br>
   * This is an example of how to add a security header: <code>
   *   protected SOAPMessage modifySOAPMessage(SOAPMessage soapMessage) throws SOAPException {
   *     SOAPFactory soapFactory = SOAPFactory.newInstance();
   *     String uri = "http://schemas.xmlsoap.org/ws/2002/12/secext";
   *     String prefix = "wss";
   *     SOAPElement security = soapFactory.createElement("Security", prefix, uri);
   *     SOAPElement usernameToken = soapFactory.createElement("UsernameToken", prefix, uri);
   *     usernameToken.addChildElement("Username", prefix, uri).setValue("test");
   *     usernameToken.addChildElement("Password", prefix, uri).setValue("test");
   *     security.addChildElement(usernameToken);
   *     soapMessage.getSOAPHeader().addChildElement(security);
   *     return soapMessage;
   *   }
   * </code>
   */
  protected SOAPMessage modifySOAPMessage(SOAPMessage soapMessage) throws SOAPException {
    // Intentionally blank
    return soapMessage;
  }

  /** Creates instances of {@link SOAPEncoder}. */
  public static class Builder {

    public boolean formattedOutput = false;
    private JAXBContextFactory jaxbContextFactory;
    private boolean writeXmlDeclaration = true;
    private Charset charsetEncoding = StandardCharsets.UTF_8;
    private String soapProtocol = DEFAULT_SOAP_PROTOCOL;

    /** The {@link JAXBContextFactory} for body part. */
    public Builder withJAXBContextFactory(JAXBContextFactory jaxbContextFactory) {
      this.jaxbContextFactory = jaxbContextFactory;
      return this;
    }

    /** Output format indent if true. Default is false */
    public Builder withFormattedOutput(boolean formattedOutput) {
      this.formattedOutput = formattedOutput;
      return this;
    }

    /** Write the xml declaration if true. Default is true */
    public Builder withWriteXmlDeclaration(boolean writeXmlDeclaration) {
      this.writeXmlDeclaration = writeXmlDeclaration;
      return this;
    }

    /** Specify the charset encoding. Default is {@link Charset#defaultCharset()}. */
    public Builder withCharsetEncoding(Charset charsetEncoding) {
      this.charsetEncoding = charsetEncoding;
      return this;
    }

    /**
     * The protocol used to create message factory. Default is "SOAP 1.1 Protocol".
     *
     * @param soapProtocol a string constant representing the MessageFactory protocol.
     * @see SOAPConstants#SOAP_1_1_PROTOCOL
     * @see SOAPConstants#SOAP_1_2_PROTOCOL
     * @see SOAPConstants#DYNAMIC_SOAP_PROTOCOL
     * @see MessageFactory#newInstance(String)
     */
    public Builder withSOAPProtocol(String soapProtocol) {
      this.soapProtocol = soapProtocol;
      return this;
    }

    public SOAPEncoder build() {
      if (jaxbContextFactory == null) {
        throw new IllegalStateException("JAXBContextFactory must be non-null");
      }
      return new SOAPEncoder(this);
    }
  }
}
