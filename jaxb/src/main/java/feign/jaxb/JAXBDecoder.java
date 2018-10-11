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
package feign.jaxb;

import java.io.IOException;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Decodes responses using JAXB. <br>
 * <p>
 * Basic example with with Feign.Builder:
 * </p>
 * 
 * <pre>
 * JAXBContextFactory jaxbFactory = new JAXBContextFactory.Builder()
 *     .withMarshallerJAXBEncoding(&quot;UTF-8&quot;)
 *     .withMarshallerSchemaLocation(&quot;http://apihost http://apihost/schema.xsd&quot;)
 *     .build();
 * 
 * api = Feign.builder()
 *     .decoder(new JAXBDecoder(jaxbFactory))
 *     .target(MyApi.class, &quot;http://api&quot;);
 * </pre>
 * <p>
 * The JAXBContextFactory should be reused across requests as it caches the created JAXB contexts.
 * </p>
 */
public class JAXBDecoder implements Decoder {

  private final JAXBContextFactory jaxbContextFactory;
  private final boolean namespaceAware;

  public JAXBDecoder(JAXBContextFactory jaxbContextFactory) {
    this.jaxbContextFactory = jaxbContextFactory;
    this.namespaceAware = true;
  }

  private JAXBDecoder(Builder builder) {
    this.jaxbContextFactory = builder.jaxbContextFactory;
    this.namespaceAware = builder.namespaceAware;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.status() == 404 || response.status() == 204)
      return Util.emptyValueOf(type);
    if (response.body() == null)
      return null;
    while (type instanceof ParameterizedType) {
      ParameterizedType ptype = (ParameterizedType) type;
      type = ptype.getRawType();
    }
    if (!(type instanceof Class)) {
      throw new UnsupportedOperationException(
          "JAXB only supports decoding raw types. Found " + type);
    }


    try {
      SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
      /* Explicitly control sax configuration to prevent XXE attacks */
      saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      saxParserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
      saxParserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
          false);
      saxParserFactory.setNamespaceAware(namespaceAware);

      Source source = new SAXSource(saxParserFactory.newSAXParser().getXMLReader(),
          new InputSource(response.body().asInputStream()));
      Unmarshaller unmarshaller = jaxbContextFactory.createUnmarshaller((Class) type);
      return unmarshaller.unmarshal(source);
    } catch (JAXBException e) {
      throw new DecodeException(e.toString(), e);
    } catch (ParserConfigurationException e) {
      throw new DecodeException(e.toString(), e);
    } catch (SAXException e) {
      throw new DecodeException(e.toString(), e);
    } finally {
      if (response.body() != null) {
        response.body().close();
      }
    }
  }

  public static class Builder {
    private boolean namespaceAware = true;
    private JAXBContextFactory jaxbContextFactory;

    /**
     * Controls whether the underlying XML parser is namespace aware. Default is true.
     */
    public Builder withNamespaceAware(boolean namespaceAware) {
      this.namespaceAware = namespaceAware;
      return this;
    }

    public Builder withJAXBContextFactory(JAXBContextFactory jaxbContextFactory) {
      this.jaxbContextFactory = jaxbContextFactory;
      return this;
    }

    public JAXBDecoder build() {
      if (jaxbContextFactory == null) {
        throw new IllegalStateException("JAXBContextFactory must be non-null");
      }
      return new JAXBDecoder(this);
    }
  }
}
