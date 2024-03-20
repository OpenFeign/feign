package feign.jaxrs3;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.Decoder;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

public class JAXRS3Decoder implements Decoder {

    private final Decoder delegate;
    private final ObjectMapper objectMapper;

    public JAXRS3Decoder(Decoder delegate, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {

        if (jakarta.ws.rs.core.Response.class.equals(type)) {
            var bodyString = new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
            jakarta.ws.rs.core.Response.ResponseBuilder responseBuilder = jakarta.ws.rs.core.Response
                    .status(response.status())
                    .entity(bodyString);
            for (Map.Entry<String, Collection<String>> entry : response.headers().entrySet()) {
                responseBuilder.header(entry.getKey(), entry.getValue());
            }

            return new JAXRS3Response(responseBuilder.build(), objectMapper);
        }
        return delegate.decode(response, type);
    }
}
