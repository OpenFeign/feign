package feign.gson;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.Encoder;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

import static feign.Util.ensureClosed;

public class GsonCodec implements Encoder, Decoder {
  private final Gson gson;

  public GsonCodec() {
    this(new Gson());
  }

  @Inject public GsonCodec(Gson gson) {
    this.gson = gson;
  }

  @Override public void encode(Object object, RequestTemplate template) {
    template.body(gson.toJson(object));
  }

  @Override public Object decode(Response response, Type type) throws IOException {
    if (response.body() == null) {
      return null;
    }
    Reader reader = response.body().asReader();
    try {
      return gson.fromJson(reader, type);
    } catch (JsonIOException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException) {
        throw IOException.class.cast(e.getCause());
      }
      throw e;
    } finally {
      ensureClosed(reader);
    }
  }
}
