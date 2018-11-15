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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;

/**
 * Creates and caches JAXB contexts as well as creates Marshallers and Unmarshallers for each
 * context. Since JAXB contexts creation can be an expensive task, JAXB context can be preloaded on
 * factory creation otherwise they will be created and cached dynamically when needed.
 */
public final class JAXBContextFactory {

  private final ConcurrentHashMap<Class<?>, JAXBContext> jaxbContexts =
      new ConcurrentHashMap<>(64);
  private final Map<String, Object> properties;

  private JAXBContextFactory(Map<String, Object> properties) {
    this.properties = properties;
  }

  /**
   * Creates a new {@link javax.xml.bind.Unmarshaller} that handles the supplied class.
   */
  public Unmarshaller createUnmarshaller(Class<?> clazz) throws JAXBException {
    return getContext(clazz).createUnmarshaller();
  }

  /**
   * Creates a new {@link javax.xml.bind.Marshaller} that handles the supplied class.
   */
  public Marshaller createMarshaller(Class<?> clazz) throws JAXBException {
    Marshaller marshaller = getContext(clazz).createMarshaller();
    setMarshallerProperties(marshaller);
    return marshaller;
  }

  private void setMarshallerProperties(Marshaller marshaller) throws PropertyException {
    for (Entry<String, Object> en : properties.entrySet()) {
      marshaller.setProperty(en.getKey(), en.getValue());
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
   * Will preload factory's cache with JAXBContext for provided classes
   * 
   * @param classes
   * @throws JAXBException
   */
  private void preloadContextCache(List<Class<?>> classes) throws JAXBException {
    if (classes != null && !classes.isEmpty()) {
      for (Class<?> clazz : classes) {
        getContext(clazz);
      }
    }
  }

  /**
   * Creates instances of {@link feign.jaxb.JAXBContextFactory}.
   */
  public static class Builder {

    private final Map<String, Object> properties = new HashMap<>(10);

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
     * Sets the given property of any Marshaller created by this factory.
     * 
     * <p>
     * Example : <br>
     * <br>
     * <code>
     *    new JAXBContextFactory.Builder()
     *      .withProperty("com.sun.xml.internal.bind.xmlHeaders", "&lt;!DOCTYPE Example SYSTEM \&quot;example.dtd\&quot;&gt;")
     *      .build();
     * </code>
     * </p>
     */
    public Builder withProperty(String key, Object value) {
      properties.put(key, value);
      return this;
    }

    /**
     * Creates a new {@link feign.jaxb.JAXBContextFactory} instance with a lazy loading cached
     * context
     */
    public JAXBContextFactory build() {
      return new JAXBContextFactory(properties);
    }

    /**
     * Creates a new {@link feign.jaxb.JAXBContextFactory} instance. Pre-loads context cache with
     * given classes
     *
     * @param classes
     * @return ContextFactory with a pre-populated JAXBContext cache
     * @throws JAXBException if provided classes can't be used for JAXBContext generation most
     *         likely due to missing JAXB annotations
     */
    public JAXBContextFactory build(List<Class<?>> classes) throws JAXBException {
      JAXBContextFactory factory = new JAXBContextFactory(properties);
      factory.preloadContextCache(classes);
      return factory;
    }
  }
}
