package feign.core.codec;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.EncoderPredicate;
import java.lang.reflect.Type;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PredicateEncoder implements Encoder {
  private final Encoder delegate;
  private final EncoderPredicate predicate;

  @Override
  public boolean encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    if (predicate.test(object, bodyType, template))
      return delegate.encode(object, bodyType, template);

    return false;
  }
}
