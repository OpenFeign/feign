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

import javax.xml.bind.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JAXBContextFactory {
    private final ConcurrentHashMap<Class, JAXBContext> jaxbContexts = new ConcurrentHashMap<Class, JAXBContext>(64);
    private Map<String, Object> properties = new HashMap<String, Object>(5);

    public JAXBContextFactory(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Unmarshaller createUnmarshaller(Class<?> clazz) throws JAXBException {
        JAXBContext ctx = getContext(clazz);
        return ctx.createUnmarshaller();
    }

    public Marshaller createMarshaller(Class<?> clazz) throws JAXBException {
        JAXBContext ctx = getContext(clazz);
        Marshaller marshaller = ctx.createMarshaller();
        setMarshallerProperties(marshaller);
        return marshaller;
    }

    private void setMarshallerProperties(Marshaller marshaller) throws PropertyException {
        Iterator<String> keys = properties.keySet().iterator();

        while(keys.hasNext()) {
            String key = keys.next();
            marshaller.setProperty(key, properties.get(key));
        }
    }

    private JAXBContext getContext(Class<?> clazz) throws JAXBException {
        JAXBContext jaxbContext = this.jaxbContexts.get(clazz);
        if (jaxbContext == null) {
            jaxbContext = JAXBContext.newInstance(clazz);
            this.jaxbContexts.putIfAbsent(clazz, jaxbContext);
        }
        return jaxbContext;
    }
}
