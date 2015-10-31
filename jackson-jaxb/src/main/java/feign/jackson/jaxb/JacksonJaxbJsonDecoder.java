package feign.jackson.jaxb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import java.io.IOException;
import java.lang.reflect.Type;

import feign.FeignException;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;

import static com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

public final class JacksonJaxbJsonDecoder implements Decoder {
  private final JacksonJaxbJsonProvider jacksonJaxbJsonProvider;

  public JacksonJaxbJsonDecoder() {
    this.jacksonJaxbJsonProvider = new JacksonJaxbJsonProvider();
  }

  public JacksonJaxbJsonDecoder(ObjectMapper objectMapper) {
    this.jacksonJaxbJsonProvider = new JacksonJaxbJsonProvider(objectMapper, DEFAULT_ANNOTATIONS);
  }

  @Override
  public Object decode(Response response, Type type) throws IOException, FeignException {
    if (response.status() == 404) return Util.emptyValueOf(type);
    if (response.body() == null) return null;
    return jacksonJaxbJsonProvider.readFrom(Object.class, type, null, APPLICATION_JSON_TYPE, null, response.body().asInputStream());
  }
}
