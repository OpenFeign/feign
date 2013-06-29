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
package feign.codec;

import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static feign.Util.checkNotNull;
import static java.lang.String.format;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.compile;

/**
 * Static utility methods pertaining to {@code Decoder} instances.
 * <p/>
 * <h4>Pattern Decoders</h4>
 * <p/>
 * Pattern decoders typically require less initialization, dependencies, and
 * code than reflective decoders, but not can be awkward to those unfamiliar
 * with regex. Typical use of pattern decoders is to grab a single field from an
 * xml response, or parse a list of Strings. The pattern decoders here
 * facilitate these use cases.
 */
public class Decoders {
  /**
   * guava users will implement this with {@code ApplyFirstGroup<String, T>}.
   *
   * @param <T> intended result type
   */
  public interface ApplyFirstGroup<T> {
    /**
     * create a new instance from the non-null {@code firstGroup} specified.
     */
    T apply(String firstGroup);
  }

  /**
   * The first match group is applied to {@code applyGroups} and result
   * returned. If no matches are found, the response is null;
   * <p/>
   * Ex. to pull the first interesting element from an xml response:
   * <p/>
   * <pre>
   * decodeFirstDirPoolID = transformFirstGroup(&quot;&lt;DirPoolID[&circ;&gt;]*&gt;([&circ;&lt;]+)&lt;/DirPoolID&gt;&quot;, ToLong.INSTANCE);
   * </pre>
   */
  public static <T> Decoder transformFirstGroup(String pattern, final ApplyFirstGroup<T> applyFirstGroup) {
    final Pattern patternForMatcher = compile(checkNotNull(pattern, "pattern"), DOTALL);
    checkNotNull(applyFirstGroup, "applyFirstGroup");
    return new Decoder() {
      @Override
      public Object decode(String methodKey, Reader reader, Type type) throws Throwable {
        Matcher matcher = patternForMatcher.matcher(Decoders.toString(reader));
        if (matcher.find()) {
          return applyFirstGroup.apply(matcher.group(1));
        }
        return null;
      }

      @Override public String toString() {
        return format("decode groups from %s into %s", patternForMatcher, applyFirstGroup);
      }
    };
  }

  /**
   * shortcut for {@link Decoders#transformFirstGroup(String, ApplyFirstGroup)} when
   * {@code String} is the type you are decoding into.
   * <p/>
   * <p/>
   * Ex. to pull the first interesting element from an xml response:
   * <p/>
   * <pre>
   * decodeFirstDirPoolID = firstGroup(&quot;&lt;DirPoolID[&circ;&gt;]*&gt;([&circ;&lt;]+)&lt;/DirPoolID&gt;&quot;);
   * </pre>
   */
  public static Decoder firstGroup(String pattern) {
    return transformFirstGroup(pattern, IDENTITY);
  }

  /**
   * On the each find the first match group is applied to
   * {@code applyFirstGroup} and added to the list returned. If no matches are
   * found, the response is an empty list;
   * <p/>
   * Ex. to pull a list zones constructed from http paths starting with
   * {@code /Rest/Zone/}:
   * <p/>
   * <pre>
   * decodeListOfZones = transformEachFirstGroup(&quot;/REST/Zone/([&circ;/]+)/&quot;, ToZone.INSTANCE);
   * </pre>
   */
  public static <T> Decoder transformEachFirstGroup(String pattern, final ApplyFirstGroup<T> applyFirstGroup) {
    final Pattern patternForMatcher = compile(checkNotNull(pattern, "pattern"), DOTALL);
    checkNotNull(applyFirstGroup, "applyFirstGroup");
    return new Decoder() {
      @Override
      public List<T> decode(String methodKey, Reader reader, Type type) throws Throwable {
        Matcher matcher = patternForMatcher.matcher(Decoders.toString(reader));
        List<T> result = new ArrayList<T>();
        while (matcher.find()) {
          result.add(applyFirstGroup.apply(matcher.group(1)));
        }
        return result;
      }

      @Override public String toString() {
        return format("decode %s into list elements, where each group(1) is transformed with %s",
            patternForMatcher, applyFirstGroup);
      }
    };
  }

  /**
   * shortcut for {@link Decoders#transformEachFirstGroup(String, ApplyFirstGroup)}
   * when {@code List<String>} is the type you are decoding into.
   * <p/>
   * Ex. to pull a list zones names, which are http paths starting with
   * {@code /Rest/Zone/}:
   * <p/>
   * <pre>
   * decodeListOfZonesNames = eachFirstGroup(&quot;/REST/Zone/([&circ;/]+)/&quot;);
   * </pre>
   */
  public static Decoder eachFirstGroup(String pattern) {
    return transformEachFirstGroup(pattern, IDENTITY);
  }

  private static String toString(Reader reader) throws Throwable {
    return TO_STRING.decode(null, reader, null).toString();
  }

  private static final Decoder TO_STRING = new ToStringDecoder();

  private static final ApplyFirstGroup<String> IDENTITY = new ApplyFirstGroup<String>() {
    @Override public String apply(String firstGroup) {
      return firstGroup;
    }
  };
}
