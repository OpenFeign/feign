package feign.codec;

import feign.Response;

import java.io.IOException;
import java.lang.reflect.Type;

public class InterceptorDecoder implements Decoder {

  private Decoder delegate;
  private ResponseMapper mapper;

  public InterceptorDecoder(Decoder decoder, ResponseMapper mapper) {
    this.delegate = decoder;
    this.mapper = mapper;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    return delegate.decode(mapper.map(response, type), type);
  }

  public interface ResponseMapper {
    Response map(Response response, Type type);
  }
}
