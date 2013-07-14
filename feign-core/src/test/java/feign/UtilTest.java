/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import static feign.Util.resolveLastTypeParameter;
import static org.testng.Assert.assertEquals;

import feign.codec.Decoder;
import feign.codec.Decoders;
import feign.codec.StringDecoder;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;
import org.testng.annotations.Test;

@Test
public class UtilTest {

  interface LastTypeParameter {
    final List<String> LIST_STRING = null;
    final Decoder.TextStream<List<String>> DECODER_LIST_STRING = null;
    final Decoder.TextStream<? extends List<String>> DECODER_WILDCARD_LIST_STRING = null;
    final ParameterizedDecoder<List<String>> PARAMETERIZED_DECODER_LIST_STRING = null;
    final ParameterizedDecoder<?> PARAMETERIZED_DECODER_UNBOUND = null;
  }

  interface ParameterizedDecoder<T extends List<String>> extends Decoder.TextStream<T> {}

  @Test
  public void resolveLastTypeParameterWhenNotSubtype() throws Exception {
    Type context = LastTypeParameter.class.getDeclaredField("DECODER_LIST_STRING").getGenericType();
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    Type last = resolveLastTypeParameter(context, Decoder.class);
    assertEquals(last, listStringType);
  }

  @Test
  public void lastTypeFromInstance() throws Exception {
    Decoder.TextStream<?> decoder = new StringDecoder();
    Type last = resolveLastTypeParameter(decoder.getClass(), Decoder.class);
    assertEquals(last, String.class);
  }

  @Test
  public void lastTypeFromStaticMethod() throws Exception {
    Decoder.TextStream<?> decoder = Decoders.firstGroup("foo");
    Type last = resolveLastTypeParameter(decoder.getClass(), Decoder.class);
    assertEquals(last, String.class);
  }

  @Test
  public void lastTypeFromAnonymous() throws Exception {
    Decoder.TextStream<?> decoder =
        new Decoder.TextStream<Reader>() {
          @Override
          public Reader decode(Reader reader, Type type) {
            return null;
          }
        };
    Type last = resolveLastTypeParameter(decoder.getClass(), Decoder.class);
    assertEquals(last, Reader.class);
  }

  @Test
  public void resolveLastTypeParameterWhenWildcard() throws Exception {
    Type context =
        LastTypeParameter.class.getDeclaredField("DECODER_WILDCARD_LIST_STRING").getGenericType();
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    Type last = resolveLastTypeParameter(context, Decoder.class);
    assertEquals(last, listStringType);
  }

  @Test
  public void resolveLastTypeParameterWhenParameterizedSubtype() throws Exception {
    Type context =
        LastTypeParameter.class
            .getDeclaredField("PARAMETERIZED_DECODER_LIST_STRING")
            .getGenericType();
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    Type last = resolveLastTypeParameter(context, ParameterizedDecoder.class);
    assertEquals(last, listStringType);
  }

  @Test
  public void unboundWildcardIsObject() throws Exception {
    Type context =
        LastTypeParameter.class.getDeclaredField("PARAMETERIZED_DECODER_UNBOUND").getGenericType();
    Type last = resolveLastTypeParameter(context, ParameterizedDecoder.class);
    assertEquals(last, Object.class);
  }
}
