/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.soap;

import feign.Experimental;
import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.PredicatedDecoder;
import feign.jaxb.JAXBContextFactory;
import jakarta.activation.DataHandler;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.attachment.AttachmentUnmarshaller;
import jakarta.xml.soap.AttachmentPart;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.MimeHeaders;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.ws.soap.SOAPFaultException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Iterator;

/**
 * Decodes SOAP responses using SOAPMessage and JAXB for the body part. <br>
 *
 * <p>The JAXBContextFactory should be reused across requests as it caches the created JAXB
 * contexts.
 *
 * <p>A SOAP Fault can be returned with a 200 HTTP code. Hence, faults could be handled with no
 * error on the HTTP layer. In this case, you'll certainly have to catch {@link SOAPFaultException}
 * to get fault from your API client service. In the other case (Faults are returned with 4xx or 5xx
 * HTTP error code), you may use {@link SOAPErrorDecoder} in your API configuration.
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
 * @see SOAPErrorDecoder
 * @see SOAPFaultException
 */
public class SOAPDecoder implements Decoder, PredicatedDecoder {

  private final JAXBContextFactory jaxbContextFactory;
  private final String soapProtocol;
  private final boolean useFirstChild;
  private final boolean propagateContentType;

  public SOAPDecoder(JAXBContextFactory jaxbContextFactory) {
    this.jaxbContextFactory = jaxbContextFactory;
    this.soapProtocol = SOAPConstants.DEFAULT_SOAP_PROTOCOL;
    this.useFirstChild = false;
    this.propagateContentType = false;
  }

  private SOAPDecoder(Builder builder) {
    this.soapProtocol = builder.soapProtocol;
    this.jaxbContextFactory = builder.jaxbContextFactory;
    this.useFirstChild = builder.useFirstChild;
    this.propagateContentType = builder.propagateContentType;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.status() == 404) return Util.emptyValueOf(type);
    if (response.body() == null) return null;
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
          MessageFactory.newInstance(soapProtocol)
              .createMessage(mimeHeaders(response), response.body().asInputStream());
      if (message.getSOAPBody() != null) {
        if (message.getSOAPBody().hasFault()) {
          throw new SOAPFaultException(message.getSOAPBody().getFault());
        }

        Unmarshaller unmarshaller = jaxbContextFactory.createUnmarshaller((Class<?>) type);
        if (message.countAttachments() > 0) {
          unmarshaller.setAttachmentUnmarshaller(new SOAPAttachmentUnmarshaller(message));
        }

        if (this.useFirstChild) {
          return unmarshaller.unmarshal(message.getSOAPBody().getFirstChild());
        } else {
          return unmarshaller.unmarshal(message.getSOAPBody().extractContentAsDocument());
        }
      }
    } catch (SOAPException | JAXBException e) {
      throw new DecodeException(response.status(), e.toString(), response.request(), e);
    } catch (AttachmentException e) {
      throw new DecodeException(response.status(), e.getMessage(), response.request(), e);
    } finally {
      if (response.body() != null) {
        response.body().close();
      }
    }
    return Util.emptyValueOf(type);
  }

  private MimeHeaders mimeHeaders(Response response) {
    if (!propagateContentType) {
      return null;
    }
    MimeHeaders mimeHeaders = new MimeHeaders();
    Collection<String> contentTypes = response.headers().get(Util.CONTENT_TYPE);
    if (contentTypes != null) {
      for (String contentType : contentTypes) {
        mimeHeaders.addHeader(Util.CONTENT_TYPE, contentType);
      }
    }
    return mimeHeaders;
  }

  /**
   * Resolves XOP {@code <xop:Include href="cid:..."/>} references against the attachments of the
   * {@link SOAPMessage}, so MTOM content can be bound to {@code byte[]} or {@link DataHandler}.
   */
  private static final class SOAPAttachmentUnmarshaller extends AttachmentUnmarshaller {

    private final SOAPMessage message;

    SOAPAttachmentUnmarshaller(SOAPMessage message) {
      this.message = message;
    }

    @Override
    public boolean isXOPPackage() {
      return true;
    }

    @Override
    public DataHandler getAttachmentAsDataHandler(String cid) {
      try {
        return attachment(cid).getDataHandler();
      } catch (SOAPException e) {
        throw new AttachmentException("Cannot read attachment " + cid, e);
      }
    }

    @Override
    public byte[] getAttachmentAsByteArray(String cid) {
      try {
        return attachment(cid).getRawContentBytes();
      } catch (SOAPException e) {
        throw new AttachmentException("Cannot read attachment " + cid, e);
      }
    }

    private AttachmentPart attachment(String cid) {
      String contentId = contentId(cid);
      Iterator<?> attachments = message.getAttachments();
      while (attachments.hasNext()) {
        AttachmentPart part = (AttachmentPart) attachments.next();
        if (part.getContentId() != null && contentId.equals(contentId(part.getContentId()))) {
          return part;
        }
      }
      throw new AttachmentException("No attachment found for " + cid, null);
    }

    /** Normalizes a {@code cid:} URL (RFC 2392) or a {@code Content-ID} header to a bare id. */
    private static String contentId(String value) {
      String id = value.trim();
      if (id.regionMatches(true, 0, "cid:", 0, 4)) {
        try {
          id = URLDecoder.decode(id.substring(4).replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException e) {
          throw new IllegalStateException(e);
        }
      }
      if (id.startsWith("<") && id.endsWith(">")) {
        id = id.substring(1, id.length() - 1);
      }
      return id;
    }
  }

  /** Carries attachment failures out of JAXB, which does not wrap unchecked exceptions. */
  private static final class AttachmentException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    AttachmentException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class Builder {
    String soapProtocol = SOAPConstants.DEFAULT_SOAP_PROTOCOL;
    JAXBContextFactory jaxbContextFactory;
    boolean useFirstChild = false;
    boolean propagateContentType = false;

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

    /**
     * Alters the behavior of the code to use the {@link SOAPBody#getFirstChild()} in place of
     * {@link SOAPBody#extractContentAsDocument()}.
     *
     * @return the builder instance.
     */
    public Builder useFirstChild() {
      this.useFirstChild = true;
      return this;
    }

    /**
     * Passes the {@code Content-Type} header of the response to the {@link MessageFactory} when
     * creating the {@link SOAPMessage}. This is required to read responses that are not a plain
     * SOAP envelope, such as MTOM/XOP responses sent as {@code multipart/related}. XOP {@code
     * xop:Include} references in the root part are resolved against the other MIME parts, so MTOM
     * content can be bound to {@code byte[]} or {@link DataHandler} properties.
     *
     * <p>The propagated content type must match the configured SOAP protocol, e.g. {@code text/xml}
     * for SOAP 1.1. Use {@link SOAPConstants#DYNAMIC_SOAP_PROTOCOL} to let the content type select
     * the protocol. Disabled by default.
     *
     * @return the builder instance.
     */
    @Experimental
    public Builder propagateContentType() {
      this.propagateContentType = true;
      return this;
    }

    public SOAPDecoder build() {
      if (jaxbContextFactory == null) {
        throw new IllegalStateException("JAXBContextFactory must be non-null");
      }
      return new SOAPDecoder(this);
    }
  }

  @Override
  public boolean canDecode(Response response, Type type) {
    return Util.isXmlContentType(response);
  }
}
