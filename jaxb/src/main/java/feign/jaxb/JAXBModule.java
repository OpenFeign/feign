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

import dagger.Provides;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;

import javax.inject.Singleton;
import javax.xml.bind.Marshaller;
import java.util.HashMap;
import java.util.Map;

@dagger.Module(injects = Feign.class, addsTo = Feign.Defaults.class)
public final class JAXBModule {
    private final Map<String, Object> properties = new HashMap<String, Object>(5);

    @Provides
    public Encoder encoder(JAXBContextFactory jaxbContextFactory) {
        return new JAXBEncoder(jaxbContextFactory);
    }

    @Provides
    public Decoder decoder(JAXBContextFactory jaxbContextFactory) {
        return new JAXBDecoder(jaxbContextFactory);
    }

    @Provides
    @Singleton
    JAXBContextFactory jaxbContextFactory() {
        return new JAXBContextFactory(properties);
    }

    public void setMarshallerJaxbEncoding(String value) {
        properties.put(Marshaller.JAXB_ENCODING, value);
    }

    public void setMarshallerSchemaLocation(String value) {
        properties.put(Marshaller.JAXB_SCHEMA_LOCATION, value);
    }

    public void setMarshallerNoNamespaceSchemaLocation(String value) {
        properties.put(Marshaller.JAXB_NO_NAMESPACE_SCHEMA_LOCATION, value);
    }

    public void setMarshallerFormattedOutput(Boolean value) {
        properties.put(Marshaller.JAXB_FORMATTED_OUTPUT, value);
    }

    public void setMarshallerFragment(Boolean value) {
        properties.put(Marshaller.JAXB_FRAGMENT, value);
    }
}
