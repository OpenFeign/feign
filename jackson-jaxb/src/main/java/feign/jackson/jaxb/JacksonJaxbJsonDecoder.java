package feign.jackson.jaxb;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import feign.FeignException;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Created by u6028487 on 01/09/2015.
 */
public class JacksonJaxbJsonDecoder implements Decoder{

    private final JacksonJaxbJsonProvider jacksonJaxbJsonProvider;

    public JacksonJaxbJsonDecoder() {
        this.jacksonJaxbJsonProvider = new JacksonJaxbJsonProvider();
    }

    public JacksonJaxbJsonDecoder(ObjectMapper objectMapper) {
        this.jacksonJaxbJsonProvider = new JacksonJaxbJsonProvider(objectMapper,JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS);
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, DecodeException, FeignException {
        return jacksonJaxbJsonProvider.readFrom(Object.class, type, null, MediaType.APPLICATION_JSON_TYPE, null, response.body().asInputStream());
    }
}
