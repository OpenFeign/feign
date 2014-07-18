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

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;

import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;

public class JAXBEncoder implements Encoder {
    private final JAXBContextFactory jaxbContextFactory;

    @Inject
    public JAXBEncoder(JAXBContextFactory jaxbContextFactory) {
        this.jaxbContextFactory = jaxbContextFactory;
    }

    @Override
    public void encode(Object object, RequestTemplate template) throws EncodeException {
        try {
            Marshaller marshaller = jaxbContextFactory.createMarshaller(object.getClass());
            StringWriter stringWriter = new StringWriter();
            marshaller.marshal(object, stringWriter);
            template.body(stringWriter.toString());
        } catch (JAXBException e) {
            throw new EncodeException(e.getMessage(), e);
        }
    }
}
