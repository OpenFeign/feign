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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;

/**
 * Creates and caches JAXB contexts as well as creates Marshallers and Unmarshallers for each
 * context.
 */
public final class JAXBContextFactory {

  private final ConcurrentHashMap<Class, JAXBContext>
      jaxbContexts =
      new ConcurrentHashMap<Class, JAXBContext>(64);
  private final Map<String, Object> properties;

  private JAXBContextFactory(Map<String, Object> properties) {
    this.properties = properties;
  }

  /**
   * Creates a new {@link javax.xml.bind.Unmarshaller} that handles the supplied class.
   */
  public Unmarshaller createUnmarshaller(Class<?> clazz) throws JAXBException {
    JAXBContext ctx = getContext(clazz);
    return ctx.createUnmarshaller();
  }

  /**
   * Creates a new {@link javax.xml.bind.Marshaller} that handles the supplied class.
   */
  public Marshaller createMarshaller(Class<?> clazz) throws JAXBException {
    JAXBContext ctx = getContext(clazz);
    Marshaller marshaller = ctx.createMarshaller();
    setMarshallerProperties(marshaller);
    return marshaller;
  }

  private void setMarshallerProperties(Marshaller marshaller) throws PropertyException {
    Iterator<String> keys = properties.keySet().iterator();

    while (keys.hasNext()) {
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

  /**
   * Creates instances of {@link feign.jaxb.JAXBContextFactory}
   */
  public static class Builder {

    private final Map<String, Object> properties = new HashMap<String, Object>(5);

    /**
     * Sets the jaxb.encoding property of any Marshaller created by this factory.
     */
    public Builder withMarshallerJAXBEncoding(String value) {
      properties.put(Marshaller.JAXB_ENCODING, value);
      return this;
    }

    /**
     * Sets the jaxb.schemaLocation property of any Marshaller created by this factory.
     */
    public Builder withMarshallerSchemaLocation(String value) {
      properties.put(Marshaller.JAXB_SCHEMA_LOCATION, value);
      return this;
    }

    /**
     * Sets the jaxb.noNamespaceSchemaLocation property of any Marshaller created by this factory.
     */
    public Builder withMarshallerNoNamespaceSchemaLocation(String value) {
      properties.put(Marshaller.JAXB_NO_NAMESPACE_SCHEMA_LOCATION, value);
      return this;
    }

    /**
     * Sets the jaxb.formatted.output property of any Marshaller created by this factory.
     */
    public Builder withMarshallerFormattedOutput(Boolean value) {
      properties.put(Marshaller.JAXB_FORMATTED_OUTPUT, value);
      return this;
    }

    /**
     * Sets the jaxb.fragment property of any Marshaller created by this factory.
     */
    public Builder withMarshallerFragment(Boolean value) {
      properties.put(Marshaller.JAXB_FRAGMENT, value);
      return this;
    }

    /**
     * Creates a new {@link feign.jaxb.JAXBContextFactory} instance.
     */
    public JAXBContextFactory build() {
      return new JAXBContextFactory(properties);
    }
  }
}
