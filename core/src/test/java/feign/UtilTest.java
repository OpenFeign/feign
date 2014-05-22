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

import feign.codec.Decoder;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;

import static feign.Util.resolveLastTypeParameter;
import static org.testng.Assert.assertEquals;

@Test
public class UtilTest {

  interface LastTypeParameter {
    final List<String> LIST_STRING = null;
    final Parameterized<List<String>> PARAMETERIZED_LIST_STRING = null;
    final Parameterized<? extends List<String>> PARAMETERIZED_WILDCARD_LIST_STRING = null;
    final ParameterizedDecoder<List<String>> PARAMETERIZED_DECODER_LIST_STRING = null;
    final ParameterizedDecoder<?> PARAMETERIZED_DECODER_UNBOUND = null;
  }

  interface ParameterizedDecoder<T extends List<String>> extends Decoder {
  }

  interface Parameterized<T> {
  }

  static class ParameterizedSubtype implements Parameterized<String> {
  }

  @Test public void resolveLastTypeParameterWhenNotSubtype() throws Exception {
    Type context = LastTypeParameter.class.getDeclaredField("PARAMETERIZED_LIST_STRING").getGenericType();
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    Type last = resolveLastTypeParameter(context, Parameterized.class);
    assertEquals(last, listStringType);
  }

  @Test public void lastTypeFromInstance() throws Exception {
    Parameterized instance = new ParameterizedSubtype();
    Type last = resolveLastTypeParameter(instance.getClass(), Parameterized.class);
    assertEquals(last, String.class);
  }

  @Test public void lastTypeFromAnonymous() throws Exception {
    Parameterized instance = new Parameterized<Reader>() {};
    Type last = resolveLastTypeParameter(instance.getClass(), Parameterized.class);
    assertEquals(last, Reader.class);
  }

  @Test public void resolveLastTypeParameterWhenWildcard() throws Exception {
    Type context = LastTypeParameter.class.getDeclaredField("PARAMETERIZED_WILDCARD_LIST_STRING").getGenericType();
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    Type last = resolveLastTypeParameter(context, Parameterized.class);
    assertEquals(last, listStringType);
  }

  @Test public void resolveLastTypeParameterWhenParameterizedSubtype() throws Exception {
    Type context = LastTypeParameter.class.getDeclaredField("PARAMETERIZED_DECODER_LIST_STRING").getGenericType();
    Type listStringType = LastTypeParameter.class.getDeclaredField("LIST_STRING").getGenericType();
    Type last = resolveLastTypeParameter(context, ParameterizedDecoder.class);
    assertEquals(last, listStringType);
  }

  @Test public void unboundWildcardIsObject() throws Exception {
    Type context = LastTypeParameter.class.getDeclaredField("PARAMETERIZED_DECODER_UNBOUND").getGenericType();
    Type last = resolveLastTypeParameter(context, ParameterizedDecoder.class);
    assertEquals(last, Object.class);
  }
  
  @Test public void variableToQueryString() throws Exception {
      ImmutableMap<String, String> map = ImmutableMap.of(
              "field1", "value1",
              "field2", "value2",
              "field3", "value3");
      
      String queryString = Util.variableToQueryString(map);
      assertEquals(queryString, "field1=value1&field2=value2&field3=value3");
          
      class Simple {
          private String field1;
          private int field2;
          
          public Simple(String field1, int field2) {
              this.field1 = field1;
              this.field2 = field2;
          }
          
          public String getField1() { 
              return field1; 
          }
          
          public int getField2() { 
              return field2;
          }
      }
      
      Simple simpleObject = new Simple("value1", 1234); 
      queryString = Util.variableToQueryString(simpleObject);
      assertEquals(queryString, "field1=value1&field2=1234");
  }
  
  @Test public void encodePair() throws Exception {
      assertEquals(Util.encodePair("key", "value") , "key=value");
      assertEquals(Util.encodePair("key with spaces", "value"), "key+with+spaces=value");
      assertEquals(Util.encodePair("key", "value with spaces"), "key=value+with+spaces");
      assertEquals(Util.encodePair("key", 1234), "key=1234");
      assertEquals(Util.encodePair("key", new java.util.Date(0)), "key=Thu+Jan+01+10%3A00%3A00+EST+1970");
  }
  
  @Test public void isVariable() throws Exception {
      assertEquals(Util.isVariable("{test}"), true);
      assertEquals(Util.isVariable("{test"), false);
      assertEquals(Util.isVariable(" {test} "), false);
      assertEquals(Util.isVariable("test}"), false);
  }
}
