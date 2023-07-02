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

import static org.junit.Assert.*;

import feign.jaxb.mock.onepackage.AnotherMockedJAXBObject;
import feign.jaxb.mock.onepackage.MockedJAXBObject;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.ValidationEventHandler;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.junit.Test;

public class JAXBContextFactoryTest {

  @Test
  public void buildsMarshallerWithJAXBEncodingProperty() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withMarshallerJAXBEncoding("UTF-16").build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertEquals("UTF-16", marshaller.getProperty(Marshaller.JAXB_ENCODING));
  }

  @Test
  public void buildsMarshallerWithSchemaLocationProperty() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder()
            .withMarshallerSchemaLocation("http://apihost http://apihost/schema.xsd")
            .build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertEquals(
        "http://apihost http://apihost/schema.xsd",
        marshaller.getProperty(Marshaller.JAXB_SCHEMA_LOCATION));
  }

  @Test
  public void buildsMarshallerWithNoNamespaceSchemaLocationProperty() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder()
            .withMarshallerNoNamespaceSchemaLocation("http://apihost/schema.xsd")
            .build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertEquals(
        "http://apihost/schema.xsd",
        marshaller.getProperty(Marshaller.JAXB_NO_NAMESPACE_SCHEMA_LOCATION));
  }

  @Test
  public void buildsMarshallerWithFormattedOutputProperty() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withMarshallerFormattedOutput(true).build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertTrue((Boolean) marshaller.getProperty(Marshaller.JAXB_FORMATTED_OUTPUT));
  }

  @Test
  public void buildsMarshallerWithFragmentProperty() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withMarshallerFragment(true).build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertTrue((Boolean) marshaller.getProperty(Marshaller.JAXB_FRAGMENT));
  }

  @Test
  public void buildsMarshallerWithSchema() throws Exception {
    Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema();
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withMarshallerSchema(schema).build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertSame(schema, marshaller.getSchema());
  }

  @Test
  public void buildsUnmarshallerWithSchema() throws Exception {
    Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema();
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withUnmarshallerSchema(schema).build();

    Unmarshaller unmarshaller = factory.createUnmarshaller(Object.class);
    assertSame(schema, unmarshaller.getSchema());
  }

  @Test
  public void buildsMarshallerWithCustomEventHandler() throws Exception {
    ValidationEventHandler handler = event -> false;
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withMarshallerEventHandler(handler).build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertSame(handler, marshaller.getEventHandler());
  }

  @Test
  public void buildsMarshallerWithDefaultEventHandler() throws Exception {
    JAXBContextFactory factory = new JAXBContextFactory.Builder().build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertNotNull(marshaller.getEventHandler());
  }

  @Test
  public void buildsUnmarshallerWithCustomEventHandler() throws Exception {
    ValidationEventHandler handler = event -> false;
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().withUnmarshallerEventHandler(handler).build();

    Unmarshaller unmarshaller = factory.createUnmarshaller(Object.class);
    assertSame(handler, unmarshaller.getEventHandler());
  }

  @Test
  public void buildsUnmarshallerWithDefaultEventHandler() throws Exception {
    JAXBContextFactory factory = new JAXBContextFactory.Builder().build();

    Unmarshaller unmarshaller = factory.createUnmarshaller(Object.class);
    assertNotNull(unmarshaller.getEventHandler());
  }

  @Test
  public void testPreloadCache() throws Exception {

    List<Class<?>> classes = Arrays.asList(String.class, Integer.class);
    JAXBContextFactory factory = new JAXBContextFactory.Builder().build(classes);

    Field f = factory.getClass().getDeclaredField("jaxbContexts"); // NoSuchFieldException
    f.setAccessible(true);
    Map internalCache = (Map) f.get(factory); // IllegalAccessException
    assertFalse(internalCache.isEmpty());
    assertTrue(internalCache.size() == classes.size());
    assertNotNull(internalCache.get(new JAXBContextClassCacheKey(String.class)));
    assertNotNull(internalCache.get(new JAXBContextClassCacheKey(Integer.class)));
  }

  @Test
  public void testClassModeInstantiation() throws Exception {

    List<Class<?>> classes = Arrays.asList(String.class, Integer.class);
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder()
            .withJAXBContextInstantiationMode(JAXBContextInstantationMode.CLASS)
            .build(classes);

    Field f = factory.getClass().getDeclaredField("jaxbContexts"); // NoSuchFieldException
    f.setAccessible(true);
    Map internalCache = (Map) f.get(factory); // IllegalAccessException
    assertFalse(internalCache.isEmpty());
    assertEquals(internalCache.size(), classes.size());
    assertNotNull(internalCache.get(new JAXBContextClassCacheKey(String.class)));
    assertNotNull(internalCache.get(new JAXBContextClassCacheKey(Integer.class)));
  }

  @Test
  public void testPackageModeInstantiationUsingSamePackage() throws Exception {

    JAXBContextFactory factory =
        new JAXBContextFactory.Builder()
            .withJAXBContextInstantiationMode(JAXBContextInstantationMode.PACKAGE)
            .build(Arrays.asList(MockedJAXBObject.class, AnotherMockedJAXBObject.class));

    Field f = factory.getClass().getDeclaredField("jaxbContexts"); // NoSuchFieldException
    f.setAccessible(true);
    Map internalCache = (Map) f.get(factory); // IllegalAccessException
    assertFalse(internalCache.isEmpty());
    assertEquals(1, internalCache.size());
    assertNotNull(
        internalCache.get(
            new JAXBContextPackageCacheKey(
                "feign.jaxb.mock.onepackage", AnotherMockedJAXBObject.class.getClassLoader())));
  }

  @Test
  public void testPackageModeInstantiationUsingMultiplePackages() throws Exception {

    JAXBContextFactory factory =
        new JAXBContextFactory.Builder()
            .withJAXBContextInstantiationMode(JAXBContextInstantationMode.PACKAGE)
            .build(
                Arrays.asList(
                    MockedJAXBObject.class, feign.jaxb.mock.anotherpackage.MockedJAXBObject.class));

    Field f = factory.getClass().getDeclaredField("jaxbContexts"); // NoSuchFieldException
    f.setAccessible(true);
    Map internalCache = (Map) f.get(factory); // IllegalAccessException
    assertFalse(internalCache.isEmpty());
    assertEquals(2, internalCache.size());
    assertNotNull(
        internalCache.get(
            new JAXBContextPackageCacheKey(
                "feign.jaxb.mock.onepackage", MockedJAXBObject.class.getClassLoader())));
    assertNotNull(
        internalCache.get(
            new JAXBContextPackageCacheKey(
                "feign.jaxb.mock.anotherpackage",
                feign.jaxb.mock.anotherpackage.MockedJAXBObject.class.getClassLoader())));
  }
}
