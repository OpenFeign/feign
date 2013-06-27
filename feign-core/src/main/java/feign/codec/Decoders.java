package feign.codec;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.compile;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.reflect.TypeToken;
import java.io.Reader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static utility methods pertaining to {@code Decoder} instances.
 *
 * <p>
 *
 * <h4>Pattern Decoders</h4>
 *
 * <p>Pattern decoders typically require less initialization, dependencies, and code than reflective
 * decoders, but not can be awkward to those unfamiliar with regex. Typical use of pattern decoders
 * is to grab a single field from an xml response, or parse a list of Strings. The pattern decoders
 * here facilitate these use cases.
 */
public class Decoders {

  /**
   * The first match group is applied to {@code applyGroups} and result returned. If no matches are
   * found, the response is null;
   *
   * <p>Ex. to pull the first interesting element from an xml response:
   *
   * <p>
   *
   * <pre>
   * decodeFirstDirPoolID = transformFirstGroup(&quot;&lt;DirPoolID[&circ;&gt;]*&gt;([&circ;&lt;]+)&lt;/DirPoolID&gt;&quot;, ToLong.INSTANCE);
   * </pre>
   */
  public static <T> Decoder transformFirstGroup(
      String pattern, final Function<String, T> applyFirstGroup) {
    final Pattern patternForMatcher = compile(checkNotNull(pattern, "pattern"), DOTALL);
    checkNotNull(applyFirstGroup, "applyFirstGroup");
    return new Decoder() {
      @Override
      public Object decode(String methodKey, Reader reader, TypeToken<?> type) throws Throwable {
        Matcher matcher = patternForMatcher.matcher(CharStreams.toString(reader));
        if (matcher.find()) {
          return applyFirstGroup.apply(matcher.group(1));
        }
        return null;
      }

      @Override
      public String toString() {
        return format("decode groups from %s into %s", patternForMatcher, applyFirstGroup);
      }
    };
  }

  /**
   * shortcut for {@link Decoders#transformFirstGroup(String, Function)} when {@code String} is the
   * type you are decoding into.
   *
   * <p>
   *
   * <p>Ex. to pull the first interesting element from an xml response:
   *
   * <p>
   *
   * <pre>
   * decodeFirstDirPoolID = firstGroup(&quot;&lt;DirPoolID[&circ;&gt;]*&gt;([&circ;&lt;]+)&lt;/DirPoolID&gt;&quot;);
   * </pre>
   */
  public static Decoder firstGroup(String pattern) {
    return transformFirstGroup(pattern, Functions.<String>identity());
  }

  /**
   * On the each find the first match group is applied to {@code applyFirstGroup} and added to the
   * list returned. If no matches are found, the response is an empty list;
   *
   * <p>Ex. to pull a list zones constructed from http paths starting with {@code /Rest/Zone/}:
   *
   * <p>
   *
   * <pre>
   * decodeListOfZones = transformEachFirstGroup(&quot;/REST/Zone/([&circ;/]+)/&quot;, ToZone.INSTANCE);
   * </pre>
   */
  public static <T> Decoder transformEachFirstGroup(
      String pattern, final Function<String, T> applyFirstGroup) {
    final Pattern patternForMatcher = compile(checkNotNull(pattern, "pattern"), DOTALL);
    checkNotNull(applyFirstGroup, "applyFirstGroup");
    return new Decoder() {
      @Override
      public List<T> decode(String methodKey, Reader reader, TypeToken<?> type) throws Throwable {
        Matcher matcher = patternForMatcher.matcher(CharStreams.toString(reader));
        ImmutableList.Builder<T> builder = ImmutableList.<T>builder();
        while (matcher.find()) {
          builder.add(applyFirstGroup.apply(matcher.group(1)));
        }
        return builder.build();
      }

      @Override
      public String toString() {
        return format(
            "decode %s into list elements, where each group(1) is transformed with %s",
            patternForMatcher, applyFirstGroup);
      }
    };
  }

  /**
   * shortcut for {@link Decoders#transformEachFirstGroup(String, Function)} when {@code
   * List<String>} is the type you are decoding into.
   *
   * <p>Ex. to pull a list zones names, which are http paths starting with {@code /Rest/Zone/}:
   *
   * <p>
   *
   * <pre>
   * decodeListOfZonesNames = eachFirstGroup(&quot;/REST/Zone/([&circ;/]+)/&quot;);
   * </pre>
   */
  public static Decoder eachFirstGroup(String pattern) {
    return transformEachFirstGroup(pattern, Functions.<String>identity());
  }
}
