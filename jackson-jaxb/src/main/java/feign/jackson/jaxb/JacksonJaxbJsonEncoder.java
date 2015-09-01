package feign.jackson.jaxb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

/**
 * Created by u6028487 on 01/09/2015.
 */
public class JacksonJaxbJsonEncoder implements Encoder{
    private final JacksonJaxbJsonProvider jacksonJaxbJsonProvider;

    public JacksonJaxbJsonEncoder() {
        this.jacksonJaxbJsonProvider = new JacksonJaxbJsonProvider();
    }

    public JacksonJaxbJsonEncoder(ObjectMapper objectMapper) {
        this.jacksonJaxbJsonProvider = new JacksonJaxbJsonProvider(objectMapper,JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS);
    }


    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            jacksonJaxbJsonProvider.writeTo(object,bodyType.getClass(),null,null, MediaType.APPLICATION_JSON_TYPE,null,outputStream);
            template.body(outputStream.toByteArray(), Charset.defaultCharset());
        } catch (IOException e) {
            throw new EncodeException(e.getMessage(), e);
        }
    }
}
