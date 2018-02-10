/**
 * Copyright 2013 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.jaxb.mixedns;

import static feign.Util.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.junit.Test;

import feign.RequestTemplate;
import feign.Response;
import feign.jaxb.JAXBContextFactory;
import feign.jaxb.JAXBDecoder;
import feign.jaxb.JAXBEncoder;

/**
 * We should be able to to just provide a list of POJOs with 
 * JAXB annotations and not have to provide an ObjectFactory
 * or jaxb.index (which requires ObjectFafctory)
 * or special JAXB annotated package-info.java.
 * 
 * The use-case is a project with only uses a few JAXB classes
 * and we don't want the whole JAXB package configuration,
 * especially since JAXBContext.newInstance(...) supports
 * a list of binding classes.
 * 
 */
public class NonSchemaMixedNamespacesTest {

    @Test
    public void multiClassMarshalling() throws Exception {
     
        // These two binding classes have different namespaces...
        JAXBContext ctx = JAXBContext.newInstance(
                Envelope.class, Login.class);
        
        Envelope envelope = new Envelope(new Body(new Login("demo", "secret")));
        
        Marshaller m = ctx.createMarshaller();
        StringWriter swriter = new StringWriter();
        m.marshal(envelope, swriter); 
        String xmlDoc = swriter.toString();
        System.out.println(xmlDoc);
        
        Unmarshaller unmarshaller = ctx.createUnmarshaller();
        StringReader sreader = new StringReader(xmlDoc);
        Envelope envelope2 = (Envelope) unmarshaller.unmarshal(sreader);

        // Can't do string compare because prefix name assignment seems non-deterministic
        assertSoapLoginEquals(envelope, envelope2);
    }
    
    @Test
    public void requestAndResponseWithMixedNSClasses() throws Exception {
        Envelope envelope = new Envelope(new Body(new Login("demo", "secret")));
        
        RequestTemplate template = new RequestTemplate();
        
        // These two binding classes have different namespaces...
        JAXBContextFactory contextFactory = new JAXBContextFactory.Builder()
                .withJaxbClasses(Envelope.class, Login.class)
                .build();
        
        new JAXBEncoder(contextFactory)
            .encode(envelope, Envelope.class, template);

        String xmlDoc = new String(template.body());
        System.out.println(xmlDoc);
        
        Response response = Response.builder()
                .status(200)
                .reason("OK")
                .headers(Collections.<String, Collection<String>>emptyMap())
                .body(xmlDoc, UTF_8)
                .build();

        JAXBDecoder decoder = new JAXBDecoder(contextFactory);
        
        // Can't do string compare because prefix name assignment seems non-deterministic
        assertSoapLoginEquals(envelope, (Envelope)decoder.decode(response, Envelope.class));
    }
    
    static void assertSoapLoginEquals(Envelope expected, Envelope actual) {
        assertNotNull(expected.body);
        assertNotNull(expected.body.payload);
        assertTrue(expected.body.payload instanceof Login);
        assertNotNull(actual.body);
        assertNotNull(actual.body.payload);
        assertTrue(actual.body.payload instanceof Login);
        
        assertEquals(((Login)expected.body.payload).username, ((Login)actual.body.payload).username);
        assertEquals(((Login)expected.body.payload).password, ((Login)actual.body.payload).password);
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "username",
        "password"
    })
    @XmlRootElement(name = "login", namespace="urn:partner.soap.sforce.com")
    public static class Login {

        public Login() {}
        
        public Login(final String username, final String password) {
            this.username = username;
            this.password = password;
        }
        
        @XmlElement(required = true)
        protected String username;
        @XmlElement(required = true)
        protected String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String value) {
            this.username = value;
        }
        
        public String getPassword() {
            return password;
        }

        public void setPassword(String value) {
            this.password = value;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "body"
    })
    @XmlRootElement(name = "Envelope", namespace="http://schemas.xmlsoap.org/soap/envelope/")
    public static class Envelope {
        
        public Envelope() {}
        public Envelope(final Body body) {
            this.body = body;
        }
        
        @XmlElement(name = "Body", namespace="http://schemas.xmlsoap.org/soap/envelope/", required = true)
        protected Body body;

        public Body getBody() {
            return body;
        }

        public void setBody(Body value) {
            this.body = value;
        }
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "body", propOrder = {
        "payload"
    })
    @XmlRootElement(name = "Body", namespace="http://schemas.xmlsoap.org/soap/envelope/")
    public static class Body {
        public Body() {}
        public Body(final Object payload) {
            this.payload = payload;
        }
        @XmlAnyElement(lax = true)
        protected Object payload;

        public Object getAny() {
            return payload;
        }
        
        public void setAny(Object payload) {
            this.payload = payload;
        }
    }
}
