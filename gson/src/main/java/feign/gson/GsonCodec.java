package feign.gson;

import com.google.gson.Gson;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.Encoder;

import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * @deprecated use {@link GsonEncoder} and {@link GsonDecoder} instead
 */
@Deprecated
public class GsonCodec implements Encoder, Decoder {
  private final GsonEncoder encoder;
  private final GsonDecoder decoder;

  public GsonCodec() {
    this(new Gson());
  }

  @Inject public GsonCodec(Gson gson) {
    this.encoder = new GsonEncoder(gson);
    this.decoder = new GsonDecoder(gson);
  }

  @Override public void encode(Object object, RequestTemplate template) {
    encoder.encode(object, template);
  }

  @Override public Object decode(Response response, Type type) throws IOException {
    return decoder.decode(response, type);
  }
}
