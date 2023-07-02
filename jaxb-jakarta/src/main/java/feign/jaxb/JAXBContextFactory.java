/*
 * Copyright 2012-2023 The Feign Authors
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

import jakarta.xml.bind.*;
import javax.xml.validation.Schema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and caches JAXB contexts as well as creates Marshallers and Unmarshallers for each
 * context. Since JAXB contexts creation can be an expensive task, JAXB context can be preloaded on
 * factory creation otherwise they will be created and cached dynamically when needed.
 */
public final class JAXBContextFactory {

  private final ConcurrentHashMap<JAXBContextCacheKey, JAXBContext> jaxbContexts =
      new ConcurrentHashMap<>(64);
  private final Map<String, Object> properties;
  private final JAXBContextInstantationMode jaxbContextInstantationMode;
  private final ValidationEventHandler marshallerEventHandler;
  private final ValidationEventHandler unmarshallerEventHandler;
  private final Schema marshallerSchema;
  private final Schema unmashallerSchema;

  private JAXBContextFactory(Map<String, Object> properties,
      JAXBContextInstantationMode jaxbContextInstantationMode,
      ValidationEventHandler marshallerEventHandler,
      ValidationEventHandler unmarshallerEventHandler,
      Schema marshallerSchema,
      Schema unmashallerSchema) {
    this.properties = properties;
    this.jaxbContextInstantationMode = jaxbContextInstantationMode;
    this.marshallerEventHandler = marshallerEventHandler;
    this.unmarshallerEventHandler = unmarshallerEventHandler;
    this.marshallerSchema = marshallerSchema;
    this.unmashallerSchema = unmashallerSchema;
  }

  /**
   * @deprecated please use the constructor with all parameters.
   */
  @Deprecated
  private JAXBContextFactory(Map<String, Object> properties,
      JAXBContextInstantationMode jaxbContextInstantationMode) {
    this(properties, jaxbContextInstantationMode, null, null, null, null);
  }

  /**
   * Creates a new {@link jakarta.xml.bind.Unmarshaller} that handles the supplied class.
   */
  public Unmarshaller createUnmarshaller(Class<?> clazz) throws JAXBException {
    Unmarshaller unmarshaller = getContext(clazz).createUnmarshaller();
    if (unmarshallerEventHandler != null) {
      unmarshaller.setEventHandler(unmarshallerEventHandler);
    }
    unmarshaller.setSchema(unmashallerSchema);
    return unmarshaller;
  }

  /**
   * Creates a new {@link jakarta.xml.bind.Marshaller} that handles the supplied class.
   */
  public Marshaller createMarshaller(Class<?> clazz) throws JAXBException {
    Marshaller marshaller = getContext(clazz).createMarshaller();
    setMarshallerProperties(marshaller);
    if (marshallerEventHandler != null) {
      marshaller.setEventHandler(marshallerEventHandler);
    }
    marshaller.setSchema(marshallerSchema);
    return marshaller;
  }

  private void setMarshallerProperties(Marshaller marshaller) throws PropertyException {
    for (Entry<String, Object> en : properties.entrySet()) {
      marshaller.setProperty(en.getKey(), en.getValue());
    }
  }

  private JAXBContext getContext(Class<?> clazz) throws JAXBException {
    JAXBContextCacheKey cacheKey = jaxbContextInstantationMode.getJAXBContextCacheKey(clazz);
    JAXBContext jaxbContext = this.jaxbContexts.get(cacheKey);

    if (jaxbContext == null) {
      jaxbContext = jaxbContextInstantationMode.getJAXBContext(clazz);
      this.jaxbContexts.putIfAbsent(cacheKey, jaxbContext);
    }
    return jaxbContext;
  }

  /**
   * Will preload factory's cache with JAXBContext for provided classes
   * 
   * @param classes
   * @throws jakarta.xml.bind.JAXBException
   */
  private void preloadContextCache(List<Class<?>> classes) throws JAXBException {
    if (classes != null && !classes.isEmpty()) {
      for (Class<?> clazz : classes) {
        getContext(clazz);
      }
    }
  }

  /**
   * Creates instances of {@link JAXBContextFactory}.
   */
  public static class Builder {

    private final Map<String, Object> properties = new HashMap<>(10);

    private JAXBContextInstantationMode jaxbContextInstantationMode =
        JAXBContextInstantationMode.CLASS;

    private ValidationEventHandler marshallerEventHandler;

    private ValidationEventHandler unmarshallerEventHandler;

    private Schema marshallerSchema;

    private Schema unmarshallerSchema;

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
     * Sets the validation event handler of any Marshaller created by this factory.
     */
    public Builder withMarshallerEventHandler(ValidationEventHandler handler) {
      this.marshallerEventHandler = handler;
      return this;
    }

    /**
     * Sets the validation event handler of any Unmarshaller created by this factory.
     */
    public Builder withUnmarshallerEventHandler(ValidationEventHandler handler) {
      this.unmarshallerEventHandler = handler;
      return this;
    }

    /**
     * Sets the schema of any Marshaller created by this factory.
     */
    public Builder withMarshallerSchema(Schema schema) {
      this.marshallerSchema = schema;
      return this;
    }

    /**
     * Sets the schema of any Unmarshaller created by this factory.
     */
    public Builder withUnmarshallerSchema(Schema schema) {
      this.unmarshallerSchema = schema;
      return this;
    }

    /**
     * Provide an instantiation mode for JAXB Contexts, can be class or package, default is class if
     * this method is not called.
     *
     * <p>
     * Example : <br>
     * <br>
     * <code>
     *    new JAXBContextFactory.Builder()
     *      .withJAXBContextInstantiationMode(JAXBContextInstantationMode.PACKAGE)
     *      .build();
     * </code>
     * </p>
     */
    public Builder withJAXBContextInstantiationMode(JAXBContextInstantationMode jaxbContextInstantiationMode) {
      this.jaxbContextInstantationMode = jaxbContextInstantiationMode;
      return this;
    }

    /**
     * Creates a new {@link JAXBContextFactory} instance with a lazy loading cached context
     */
    public JAXBContextFactory build() {
      return new JAXBContextFactory(properties, jaxbContextInstantationMode, marshallerEventHandler,
          unmarshallerEventHandler, marshallerSchema, unmarshallerSchema);
    }

    /**
     * Creates a new {@link JAXBContextFactory} instance. Pre-loads context cache with given classes
     *
     * @param classes
     * @return ContextFactory with a pre-populated JAXBContext cache
     * @throws jakarta.xml.bind.JAXBException if provided classes can't be used for JAXBContext
     *         generation most likely due to missing JAXB annotations
     */
    public JAXBContextFactory build(List<Class<?>> classes) throws JAXBException {
      JAXBContextFactory factory = build();
      factory.preloadContextCache(classes);
      return factory;
    }
  }
}
