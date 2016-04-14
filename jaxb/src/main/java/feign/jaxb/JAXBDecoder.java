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

import java.io.IOException;
import java.lang.reflect.Type;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;

/**
 * Decodes responses using JAXB. <br> <p> Basic example with with Feign.Builder: </p>
 * <pre>
 * JAXBContextFactory jaxbFactory = new JAXBContextFactory.Builder()
 *      .withMarshallerJAXBEncoding("UTF-8")
 *      .withMarshallerSchemaLocation("http://apihost http://apihost/schema.xsd")
 *      .build();
 *
 * api = Feign.builder()
 *            .decoder(new JAXBDecoder(jaxbFactory))
 *            .target(MyApi.class, "http://api");
 * </pre>
 * <p> The JAXBContextFactory should be reused across requests as it caches the created JAXB
 * contexts. </p>
 */
public class JAXBDecoder implements Decoder {

  private final JAXBContextFactory jaxbContextFactory;

  public JAXBDecoder(JAXBContextFactory jaxbContextFactory) {
    this.jaxbContextFactory = jaxbContextFactory;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.status() == 404) return Util.emptyValueOf(type);
    if (response.body() == null) return null;
    if (!(type instanceof Class)) {
      throw new UnsupportedOperationException(
          "JAXB only supports decoding raw types. Found " + type);
    }
    try {
      Unmarshaller unmarshaller = jaxbContextFactory.createUnmarshaller((Class) type);
      return unmarshaller.unmarshal(response.body().asInputStream());
    } catch (JAXBException e) {
      throw new DecodeException(e.toString(), e);
    } finally {
      if (response.body() != null) {
        response.body().close();
      }
    }
  }
}
