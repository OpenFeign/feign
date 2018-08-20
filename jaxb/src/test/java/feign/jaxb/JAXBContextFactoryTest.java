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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import javax.xml.bind.Marshaller;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
    assertEquals("http://apihost http://apihost/schema.xsd",
        marshaller.getProperty(Marshaller.JAXB_SCHEMA_LOCATION));
  }

  @Test
  public void buildsMarshallerWithNoNamespaceSchemaLocationProperty() throws Exception {
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder()
            .withMarshallerNoNamespaceSchemaLocation("http://apihost/schema.xsd").build();

    Marshaller marshaller = factory.createMarshaller(Object.class);
    assertEquals("http://apihost/schema.xsd",
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
  public void testPreloadCache() throws Exception {

    List<Class<?>> classes = Arrays.asList(String.class, Integer.class);
    JAXBContextFactory factory =
        new JAXBContextFactory.Builder().build(classes);

    Field f = factory.getClass().getDeclaredField("jaxbContexts"); // NoSuchFieldException
    f.setAccessible(true);
    Map internalCache = (Map) f.get(factory); // IllegalAccessException
    assertFalse(internalCache.isEmpty());
    assertTrue(internalCache.size() == classes.size());
    assertNotNull(internalCache.get(String.class));
    assertNotNull(internalCache.get(Integer.class));

  }

}
