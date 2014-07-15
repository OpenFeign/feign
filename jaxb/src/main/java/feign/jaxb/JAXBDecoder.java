/*
 * Copyright 2013 Netflix, Inc.
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

import feign.FeignException;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.IOException;
import java.lang.reflect.Type;

public class JAXBDecoder implements Decoder {
    private final JAXBContextFactory jaxbContextFactory;

    public JAXBDecoder(JAXBContextFactory jaxbContextFactory) {
        this.jaxbContextFactory = jaxbContextFactory;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        try {
            Unmarshaller unmarshaller = jaxbContextFactory.createUnmarshaller((Class) type);
            return unmarshaller.unmarshal(response.body().asInputStream());
        } catch (JAXBException e) {
            throw new DecodeException(e.getMessage(), e);
        }
    }
}
