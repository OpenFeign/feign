package feign.jackson.jaxb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;

import static com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

public final class JacksonJaxbJsonEncoder implements Encoder {
  private final JacksonJaxbJsonProvider jacksonJaxbJsonProvider;

  public JacksonJaxbJsonEncoder() {
    this.jacksonJaxbJsonProvider = new JacksonJaxbJsonProvider();
  }

  public JacksonJaxbJsonEncoder(ObjectMapper objectMapper) {
    this.jacksonJaxbJsonProvider = new JacksonJaxbJsonProvider(objectMapper, DEFAULT_ANNOTATIONS);
  }


  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      jacksonJaxbJsonProvider.writeTo(object, bodyType.getClass(), null, null, APPLICATION_JSON_TYPE, null, outputStream);
      template.body(outputStream.toByteArray(), Charset.defaultCharset());
    } catch (IOException e) {
      throw new EncodeException(e.getMessage(), e);
    }
  }
}
