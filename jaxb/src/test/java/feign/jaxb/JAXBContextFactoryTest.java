/**
 * Copyright 2012-2018 The Feign Authors
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.jaxb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.xml.bind.Marshaller;
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
}
