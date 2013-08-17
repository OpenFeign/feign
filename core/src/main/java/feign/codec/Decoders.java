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

import java.io.IOException;
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
 * Static utility methods pertaining to {@code Decoder} instances. <br>
 * <br>
 * <br>
 * <b>Pattern Decoders</b><br>
 * <br>
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
   * shortcut for <pre>new TransformFirstGroup<String>(pattern, applyFirstGroup){}</pre> when
   * {@code String} is the type you are decoding into. <br>
   * <br>
   * Ex. to pull the first interesting element from an xml response: <br>
   * <p/>
   * <pre>
   * decodeFirstDirPoolID = firstGroup(&quot;&lt;DirPoolID[&circ;&gt;]*&gt;([&circ;&lt;]+)&lt;/DirPoolID&gt;&quot;);
   * </pre>
   */
  public static Decoder.TextStream<String> firstGroup(String pattern) {
    return new TransformFirstGroup<String>(pattern, IDENTITY) {
    };
  }

  /**
   * shortcut for <pre>new TransformEachFirstGroup<String>(pattern, applyFirstGroup){}</pre> when
   * {@code List<String>} is the type you are decoding into. <br>
   * Ex. to pull a list zones names, which are http paths starting with
   * {@code /Rest/Zone/}: <br>
   * <p/>
   * <pre>
   * decodeListOfZonesNames = eachFirstGroup(&quot;/REST/Zone/([&circ;/]+)/&quot;);
   * </pre>
   */
  public static Decoder.TextStream<List<String>> eachFirstGroup(String pattern) {
    return new TransformEachFirstGroup<String>(pattern, IDENTITY) {
    };
  }

  private static String toString(Reader reader) throws IOException {
    return TO_STRING.decode(reader, null).toString();
  }

  private static final StringDecoder TO_STRING = new StringDecoder();

  private static final ApplyFirstGroup<String> IDENTITY = new ApplyFirstGroup<String>() {
    @Override
    public String apply(String firstGroup) {
      return firstGroup;
    }
  };

  /**
   * The first match group is applied to {@code applyGroups} and result
   * returned. If no matches are found, the response is null; <br>
   * Ex. to pull the first interesting element from an xml response: <br>
   * <p/>
   * <pre>
   * decodeFirstDirPoolID = new TransformFirstGroup&lt;Long&gt;(&quot;&lt;DirPoolID[&circ;&gt;]*&gt;([&circ;&lt;]+)&lt;/DirPoolID&gt;&quot;, ToLong.INSTANCE) {
   * };
   * </pre>
   */
  public static class TransformFirstGroup<T> implements Decoder.TextStream<T> {
    private final Pattern patternForMatcher;
    private final ApplyFirstGroup<T> applyFirstGroup;

    /**
     * You must subclass this, in order to prevent type erasure on {@code T}
     * . In addition to making a concrete type, you can also use the
     * following form.
     * <p/>
     * <br>
     * <p/>
     * <pre>
     * new TransformFirstGroup&lt;Foo&gt;(pattern, applyFirstGroup) {
     * }; // note the curly braces ensures no type erasure!
     * </pre>
     */
    protected TransformFirstGroup(String pattern, ApplyFirstGroup<T> applyFirstGroup) {
      this.patternForMatcher = compile(checkNotNull(pattern, "pattern"), DOTALL);
      this.applyFirstGroup = checkNotNull(applyFirstGroup, "applyFirstGroup");
    }

    @Override
    public T decode(Reader reader, Type type) throws IOException {
      Matcher matcher = patternForMatcher.matcher(Decoders.toString(reader));
      if (matcher.find()) {
        return applyFirstGroup.apply(matcher.group(1));
      }
      return null;
    }

    @Override
    public String toString() {
      return format("decode groups from %s into %s", patternForMatcher, applyFirstGroup);
    }
  }

  /**
   * On the each find the first match group is applied to
   * {@code applyFirstGroup} and added to the list returned. If no matches are
   * found, the response is an empty list; <br>
   * Ex. to pull a list zones constructed from http paths starting with
   * {@code /Rest/Zone/}:
   * <p/>
   * <br>
   * <p/>
   * <pre>
   * decodeListOfZones = new TransformEachFirstGroup(&quot;/REST/Zone/([&circ;/]+)/&quot;, ToZone.INSTANCE) {
   * };
   * </pre>
   */
  public static class TransformEachFirstGroup<T> implements Decoder.TextStream<List<T>> {
    private final Pattern patternForMatcher;
    private final ApplyFirstGroup<T> applyFirstGroup;

    /**
     * You must subclass this, in order to prevent type erasure on {@code T}
     * . In addition to making a concrete type, you can also use the
     * following form.
     * <p/>
     * <br>
     * <p/>
     * <pre>
     * new TransformEachFirstGroup&lt;Foo&gt;(pattern, applyFirstGroup) {
     * }; // note the curly braces ensures no type erasure!
     * </pre>
     */
    protected TransformEachFirstGroup(String pattern, ApplyFirstGroup<T> applyFirstGroup) {
      this.patternForMatcher = compile(checkNotNull(pattern, "pattern"), DOTALL);
      this.applyFirstGroup = checkNotNull(applyFirstGroup, "applyFirstGroup");
    }

    @Override
    public List<T> decode(Reader reader, Type type) throws IOException {
      Matcher matcher = patternForMatcher.matcher(Decoders.toString(reader));
      List<T> result = new ArrayList<T>();
      while (matcher.find()) {
        result.add(applyFirstGroup.apply(matcher.group(1)));
      }
      return result;
    }

    @Override
    public String toString() {
      return format("decode %s into list elements, where each group(1) is transformed with %s",
          patternForMatcher, applyFirstGroup);
    }
  }
}
