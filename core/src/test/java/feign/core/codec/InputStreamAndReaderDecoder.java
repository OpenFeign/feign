package feign.core.codec;

import feign.FeignException;
import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.utils.ContentTypeParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;

public class InputStreamAndReaderDecoder implements Decoder {
  private final Decoder delegateDecoder;

  public InputStreamAndReaderDecoder(Decoder delegate) {
    this.delegateDecoder = delegate;
  }

  @Override
  public Object decode(Response response, Type type)
      throws IOException, DecodeException, FeignException {

    if (InputStream.class.equals(type)) return response.body().asInputStream();

    if (Reader.class.equals(type))
      return response
          .body()
          .asReader(
              ContentTypeParser.parseContentTypeFromHeaders(response.headers(), "UTF-8")
                  .getCharset()
                  .orElse(Util.UTF_8));

    if (delegateDecoder == null) return null;

    return delegateDecoder.decode(response, type);
  }
}
