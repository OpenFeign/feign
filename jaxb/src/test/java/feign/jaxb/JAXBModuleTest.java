package feign.jaxb;

import com.google.common.reflect.TypeToken;
import dagger.Module;
import dagger.ObjectGraph;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.testng.annotations.Test;

import javax.inject.Inject;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collection;
import java.util.Collections;

import static feign.Util.UTF_8;
import static org.testng.Assert.assertEquals;

/**
 * @author gwhit7
 */
@Test
public class JAXBModuleTest {
    @Module(includes = JAXBModule.class, injects = EncoderAndDecoderBindings.class)
    static class EncoderAndDecoderBindings {
        @Inject
        Encoder encoder;

        @Inject
        Decoder decoder;
    }

    @Module(includes = JAXBModule.class, injects = EncoderBindings.class)
    static class EncoderBindings {
        @Inject Encoder encoder;
    }

    @Module(includes = JAXBModule.class, injects = DecoderBindings.class)
    static class DecoderBindings {
        @Inject Decoder decoder;
    }

    @Test
    public void providesEncoderDecoder() throws Exception {
        EncoderAndDecoderBindings bindings = new EncoderAndDecoderBindings();
        ObjectGraph.create(bindings).inject(bindings);

        assertEquals(bindings.encoder.getClass(), JAXBEncoder.class);
        assertEquals(bindings.decoder.getClass(), JAXBDecoder.class);
    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    static class MockObject {

        @XmlElement
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MockObject that = (MockObject) o;

            if (value != null ? !value.equals(that.value) : that.value != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }
    }

    @Test
    public void encodesXml() throws Exception {
        EncoderBindings bindings = new EncoderBindings();
        ObjectGraph.create(bindings).inject(bindings);

        MockObject mock = new MockObject();
        mock.setValue("Test");

        RequestTemplate template = new RequestTemplate();
        bindings.encoder.encode(mock, template);

        System.out.println(new String(template.body()));

        assertEquals(new String(template.body(), UTF_8), "<?xml version=\"1.0\" encoding=\"UTF-8\" " +
                "standalone=\"yes\"?><mockObject><value>Test</value></mockObject>");
    }

    @Test
    public void decodesXml() throws Exception {
        DecoderBindings bindings = new DecoderBindings();
        ObjectGraph.create(bindings).inject(bindings);

        MockObject mock = new MockObject();
        mock.setValue("Test");

        String mockXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><mockObject>" +
                "<value>Test</value></mockObject>";

        Response response =
                Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), mockXml, UTF_8);

        assertEquals(bindings.decoder.decode(response, new TypeToken<MockObject>() {}.getType()), mock);
    }
}
