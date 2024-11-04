package feign.vertx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class TestUtils {
  static final ObjectMapper MAPPER = new ObjectMapper();
  static {
    MAPPER.registerModule(new JavaTimeModule());
  }

  static String encodeAsJsonString(final Object object) {
    try {
      return MAPPER.writeValueAsString(object);
    } catch (JsonProcessingException unexpectedException) {
      return "";
    }
  }
}
