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

import java.io.StringWriter;
import java.lang.reflect.Type;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;

/**
 * Encodes requests using JAXB. <br> <p> Basic example with with Feign.Builder: </p>
 * <pre>
 * JAXBContextFactory jaxbFactory = new JAXBContextFactory.Builder()
 *      .withMarshallerJAXBEncoding("UTF-8")
 *      .withMarshallerSchemaLocation("http://apihost http://apihost/schema.xsd")
 *      .build();
 *
 * api = Feign.builder()
 *            .encoder(new JAXBEncoder(jaxbFactory))
 *            .target(MyApi.class, "http://api");
 * </pre>
 * <p> The JAXBContextFactory should be reused across requests as it caches the created JAXB
 * contexts. </p>
 */
public class JAXBEncoder implements Encoder {

  private final JAXBContextFactory jaxbContextFactory;

  public JAXBEncoder(JAXBContextFactory jaxbContextFactory) {
    this.jaxbContextFactory = jaxbContextFactory;
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template) {
    if (!(bodyType instanceof Class)) {
      throw new UnsupportedOperationException(
          "JAXB only supports encoding raw types. Found " + bodyType);
    }
    try {
      Marshaller marshaller = jaxbContextFactory.createMarshaller((Class) bodyType);
      StringWriter stringWriter = new StringWriter();
      marshaller.marshal(object, stringWriter);
      template.body(stringWriter.toString());
    } catch (JAXBException e) {
      throw new EncodeException(e.toString(), e);
    }
  }
}
