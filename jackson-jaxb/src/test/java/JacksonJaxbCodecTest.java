import feign.RequestTemplate;
import feign.Response;
import feign.jackson.jaxb.JacksonJaxbJsonDecoder;
import feign.jackson.jaxb.JacksonJaxbJsonEncoder;
import org.junit.Test;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.Collection;
import java.util.Collections;

import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Created by u6028487 on 01/09/2015.
 */
public class JacksonJaxbCodecTest {

    @Test
    public void encodeTest(){
        MockObject mock = new MockObject();
        mock.value = "Test";

        JacksonJaxbJsonEncoder encoder = new JacksonJaxbJsonEncoder();
        RequestTemplate template = new RequestTemplate();
        encoder.encode(mock, MockObject.class, template);
        assertThat(template).hasBody("{\"value\":\"Test\"}");

    }

    @Test
    public void decodeTest()throws Exception {
        MockObject mock = new MockObject();
        mock.value = "Test";

        String mockJson = "{"
                + "\"value\":\"Test\"}";

        Response
                response =
                Response
                        .create(200, "OK", Collections.<String, Collection<String>>emptyMap(), mockJson, UTF_8);
        JacksonJaxbJsonDecoder decoder = new JacksonJaxbJsonDecoder();
        MockObject mockObject = (MockObject)decoder.decode(response, MockObject.class);
        assertEquals(mock, mockObject);

    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    static class MockObject {

        @XmlElement
        private String value;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MockObject) {
                MockObject other = (MockObject) obj;
                return value.equals(other.value);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }
    }
}
