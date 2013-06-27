package feign.codec;

import com.google.common.io.CharStreams;
import com.google.common.reflect.TypeToken;

import java.io.Reader;

public class ToStringDecoder extends Decoder {
  @Override public Object decode(String methodKey, Reader reader, TypeToken<?> type) throws Throwable {
    return CharStreams.toString(reader);
  }
}