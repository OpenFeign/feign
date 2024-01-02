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

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import feign.jaxb.mock.onepackage.AnotherMockedJAXBObject;
import feign.jaxb.mock.onepackage.MockedJAXBObject;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.ValidationEventHandler;

class JAXBContextFactoryTest {

  @Test
  void buildsMarshallerWithJAXBEncodingProperty() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withMarshallerJAXBEncoding("UTF-16").build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertThat(marshaller.getProperty(Marshaller.JAXB_ENCODING)).isEqualTo("UTF-16");
  }

  @Test
  void buildsMarshallerWithSchemaLocationProperty() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder()
            .withMarshallerSchemaLocation("http://apihost http://apihost/schema.xsd")
            .build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertThat(marshaller.getProperty(Marshaller.JAXB_SCHEMA_LOCATION))
        .isEqualTo("http://apihost http://apihost/schema.xsd");
  }

  @Test
  void buildsMarshallerWithNoNamespaceSchemaLocationProperty() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder()
            .withMarshallerNoNamespaceSchemaLocation("http://apihost/schema.xsd").build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertThat(marshaller.getProperty(Marshaller.JAXB_NO_NAMESPACE_SCHEMA_LOCATION))
        .isEqualTo("http://apihost/schema.xsd");
  }

  @Test
  void buildsMarshallerWithFormattedOutputProperty() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withMarshallerFormattedOutput(true).build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertThat((Boolean) marshaller.getProperty(Marshaller.JAXB_FORMATTED_OUTPUT)).isTrue();
  }

  @Test
  void buildsMarshallerWithFragmentProperty() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withMarshallerFragment(true).build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertThat((Boolean) marshaller.getProperty(Marshaller.JAXB_FRAGMENT)).isTrue();
  }

  @Test
  void buildsMarshallerWithSchema() throws Exception {
    Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema();
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withMarshallerSchema(schema).build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertThat(marshaller.getSchema()).isSameAs(schema);
  }

  @Test
  void buildsUnmarshallerWithSchema() throws Exception {
    Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema();
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withUnmarshallerSchema(schema).build();

    Unmarshaller unmarshaller = factory.createUnmarshaller(Object.class);
    assertThat(unmarshaller.getSchema()).isSameAs(schema);
  }

  @Test
  void buildsMarshallerWithCustomEventHandler() throws Exception {
    ValidationEventHandler handler = event -> false;
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withMarshallerEventHandler(handler).build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertThat(marshaller.getEventHandler()).isSameAs(handler);
  }

  @Test
  void buildsMarshallerWithDefaultEventHandler() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertThat(marshaller.getEventHandler()).isNotNull();
  }

  @Test
  void buildsUnmarshallerWithCustomEventHandler() throws Exception {
    ValidationEventHandler handler = event -> false;
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withUnmarshallerEventHandler(handler).build();

    Unmarshaller unmarshaller = factory.createUnmarshaller(Object.class);
    assertThat(unmarshaller.getEventHandler()).isSameAs(handler);
  }

  @Test
  void buildsUnmarshallerWithDefaultEventHandler() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().build();

    Unmarshaller unmarshaller = factory.createUnmarshaller(Object.class);
    assertThat(unmarshaller.getEventHandler()).isNotNull();
  }

  @Test
  void preloadCache() throws Exception {

    List<Class<?>> classes = Arrays.asList(String.class, Integer.class);
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().build(classes);

    Field f = factory.getClass().getDeclaredField("jaxbContexts"); // NoSuchFieldException
    f.setAccessible(true);
    Map internalCache = (Map) f.get(factory); // IllegalAccessException
    assertThat(internalCache.isEmpty()).isFalse();
    assertThat(internalCache.size() == classes.size()).isTrue();
    assertThat(internalCache.get(new JAXBContextClassCacheKey(String.class))).isNotNull();
    assertThat(internalCache.get(new JAXBContextClassCacheKey(Integer.class))).isNotNull();

  }

  @Test
  void classModeInstantiation() throws Exception {

    List<Class<?>> classes = Arrays.asList(String.class, Integer.class);
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder()
            .withJAXBContextInstantiationMode(JAXBContextInstantationMode.CLASS)
            .build(classes);

    Field f = factory.getClass().getDeclaredField("jaxbContexts"); // NoSuchFieldException
    f.setAccessible(true);
    Map internalCache = (Map) f.get(factory); // IllegalAccessException
    assertThat(internalCache.isEmpty()).isFalse();
    assertThat(classes).hasSize(internalCache.size());
    assertThat(internalCache.get(new JAXBContextClassCacheKey(String.class))).isNotNull();
    assertThat(internalCache.get(new JAXBContextClassCacheKey(Integer.class))).isNotNull();

  }

  @Test
  void packageModeInstantiationUsingSamePackage() throws Exception {

    JAXBContextFactory factory = new JAXBContextFactory.Builder()
        .withJAXBContextInstantiationMode(JAXBContextInstantationMode.PACKAGE)
        .build(Arrays.asList(MockedJAXBObject.class, AnotherMockedJAXBObject.class));

    Field f = factory.getClass().getDeclaredField("jaxbContexts"); // NoSuchFieldException
    f.setAccessible(true);
    Map internalCache = (Map) f.get(factory); // IllegalAccessException
    assertThat(internalCache.isEmpty()).isFalse();
    assertThat(internalCache).hasSize(1);
    assertThat(internalCache.get(new JAXBContextPackageCacheKey("feign.jaxb.mock.onepackage",
        AnotherMockedJAXBObject.class.getClassLoader()))).isNotNull();

  }

  @Test
  void packageModeInstantiationUsingMultiplePackages() throws Exception {

    JAXBContextFactory factory = new JAXBContextFactory.Builder()
        .withJAXBContextInstantiationMode(JAXBContextInstantationMode.PACKAGE)
        .build(Arrays.asList(MockedJAXBObject.class,
            feign.jaxb.mock.anotherpackage.MockedJAXBObject.class));

    Field f = factory.getClass().getDeclaredField("jaxbContexts"); // NoSuchFieldException
    f.setAccessible(true);
    Map internalCache = (Map) f.get(factory); // IllegalAccessException
    assertThat(internalCache.isEmpty()).isFalse();
    assertThat(internalCache).hasSize(2);
    assertThat(internalCache.get(new JAXBContextPackageCacheKey("feign.jaxb.mock.onepackage",
        MockedJAXBObject.class.getClassLoader()))).isNotNull();
    assertThat(internalCache.get(new JAXBContextPackageCacheKey("feign.jaxb.mock.anotherpackage",
        feign.jaxb.mock.anotherpackage.MockedJAXBObject.class.getClassLoader()))).isNotNull();


  }
}
