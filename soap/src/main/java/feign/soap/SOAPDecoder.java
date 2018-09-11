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

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import javax.xml.bind.JAXBException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.soap.SOAPFaultException;
import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.jaxb.JAXBContextFactory;

/**
 * Decodes SOAP responses using SOAPMessage and JAXB for the body part. <br>
 *
 * <p>
 * The JAXBContextFactory should be reused across requests as it caches the created JAXB contexts.
 * </p>
 *
 * <p>
 * A SOAP Fault can be returned with a 200 HTTP code. Hence, faults could be handled with no error
 * on the HTTP layer. In this case, you'll certainly have to catch {@link SOAPFaultException} to get
 * fault from your API client service. In the other case (Faults are returned with 4xx or 5xx HTTP
 * error code), you may use {@link SOAPErrorDecoder} in your API configuration.
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
 *     .withMarshallerJAXBEncoding(&quot;UTF-8&quot;)
 *     .withMarshallerSchemaLocation(&quot;http://apihost http://apihost/schema.xsd&quot;)
 *     .build();
 *
 * api = Feign.builder()
 *     .decoder(new SOAPDecoder(jaxbFactory))
 *     .target(MyApi.class, &quot;http://api&quot;);
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
 * </p>
 *
 * @see SOAPErrorDecoder
 * @see SOAPFaultException
 */
public class SOAPDecoder implements Decoder {


  private final JAXBContextFactory jaxbContextFactory;
  private final String soapProtocol;

  public SOAPDecoder(JAXBContextFactory jaxbContextFactory) {
    this.jaxbContextFactory = jaxbContextFactory;
    this.soapProtocol = SOAPConstants.DEFAULT_SOAP_PROTOCOL;
  }

  private SOAPDecoder(Builder builder) {
    this.soapProtocol = builder.soapProtocol;
    this.jaxbContextFactory = builder.jaxbContextFactory;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.status() == 404)
      return Util.emptyValueOf(type);
    if (response.body() == null)
      return null;
    while (type instanceof ParameterizedType) {
      ParameterizedType ptype = (ParameterizedType) type;
      type = ptype.getRawType();
    }
    if (!(type instanceof Class)) {
      throw new UnsupportedOperationException(
          "SOAP only supports decoding raw types. Found " + type);
    }

    try {
      SOAPMessage message =
          MessageFactory.newInstance(soapProtocol).createMessage(null,
              response.body().asInputStream());
      if (message.getSOAPBody() != null) {
        if (message.getSOAPBody().hasFault()) {
          throw new SOAPFaultException(message.getSOAPBody().getFault());
        }

        return jaxbContextFactory.createUnmarshaller((Class<?>) type)
            .unmarshal(message.getSOAPBody().extractContentAsDocument());
      }
    } catch (SOAPException | JAXBException e) {
      throw new DecodeException(e.toString(), e);
    } finally {
      if (response.body() != null) {
        response.body().close();
      }
    }
    return Util.emptyValueOf(type);

  }


  public static class Builder {
    String soapProtocol = SOAPConstants.DEFAULT_SOAP_PROTOCOL;
    JAXBContextFactory jaxbContextFactory;

    public Builder withJAXBContextFactory(JAXBContextFactory jaxbContextFactory) {
      this.jaxbContextFactory = jaxbContextFactory;
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

    public SOAPDecoder build() {
      if (jaxbContextFactory == null) {
        throw new IllegalStateException("JAXBContextFactory must be non-null");
      }
      return new SOAPDecoder(this);
    }
  }

}
