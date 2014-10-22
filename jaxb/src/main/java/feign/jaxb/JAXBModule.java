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

import dagger.Provides;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;

import javax.inject.Singleton;

/**
 * Provides an Encoder and Decoder for handling XML responses with JAXB annotated classes.
 * <p>
 * <br>
 * Here is an example of configuring a custom JAXBContextFactory:
 * </p>
 * <pre>
 *    JAXBContextFactory jaxbFactory = new JAXBContextFactory.Builder()
 *               .withMarshallerJAXBEncoding("UTF-8")
 *               .withMarshallerSchemaLocation("http://apihost http://apihost/schema.xsd")
 *               .build();
 *
 *    Response response = Feign.create(Response.class, "http://apihost", new JAXBModule(jaxbFactory));
 * </pre>
 * <p>
 * The JAXBContextFactory should be reused across requests as it caches the created JAXB contexts.
 * </p>
 */
@dagger.Module(injects = Feign.class, addsTo = Feign.Defaults.class)
public final class JAXBModule {
    private final JAXBContextFactory jaxbContextFactory;

    public JAXBModule() {
        this.jaxbContextFactory = new JAXBContextFactory.Builder().build();
    }

    public JAXBModule(JAXBContextFactory jaxbContextFactory) {
        this.jaxbContextFactory = jaxbContextFactory;
    }

    @Provides Encoder encoder(JAXBEncoder jaxbEncoder) {
        return jaxbEncoder;
    }

    @Provides Decoder decoder(JAXBDecoder jaxbDecoder) {
        return jaxbDecoder;
    }

    @Provides @Singleton JAXBContextFactory jaxbContextFactory() {
        return this.jaxbContextFactory;
    }
}
