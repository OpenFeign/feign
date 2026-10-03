/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.form;

import static feign.form.util.PojoUtil.isUserPojo;
import static feign.form.util.PojoUtil.toMap;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static lombok.AccessLevel.PRIVATE;

import feign.RequestTemplate;
import feign.codec.DefaultEncoder;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.EncoderPredicate;
import feign.codec.PredicatedEncoder;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.experimental.FieldDefaults;
import lombok.val;

/**
 * A Feign's form encoder.
 *
 * @author Artem Labazin
 */
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class FormEncoder implements Encoder {

  private static final String CONTENT_TYPE_HEADER;

  private static final Pattern CHARSET_PATTERN;

  /** Stands in for a delegate that was never supplied, see {@link #FormEncoder(Encoder)}. */
  private static final Encoder NO_DELEGATE =
      (object, bodyType, template) -> {
        throw new EncodeException(
            "This form encoder has no delegate encoder, so it can only encode form and multipart"
                + " requests, and "
                + bodyType
                + " is neither. Register an encoder that handles it.");
      };

  static {
    CONTENT_TYPE_HEADER = "Content-Type";
    CHARSET_PATTERN = Pattern.compile("(?<=charset=)([\\w\\-]+)");
  }

  Encoder delegate;

  Map<ContentType, ContentProcessor> processors;

  /** Constructor with the default Feign's encoder as a delegate. */
  public FormEncoder() {
    this(new DefaultEncoder());
  }

  /**
   * Constructor with specified delegate encoder.
   *
   * @param delegate delegate encoder, if this encoder couldn't encode object. {@code null} leaves
   *     this encoder without one, in which case anything it cannot encode itself fails with an
   *     {@link EncodeException} rather than being passed on. Prefer {@link
   *     #createPredicatedFormEncoder()} together with {@code BaseBuilder.encoders(...)}: chaining
   *     belongs there rather than in a delegate, and this constructor is expected to be deprecated
   *     once that surface stops being experimental.
   */
  public FormEncoder(Encoder delegate) {
    this.delegate = delegate == null ? NO_DELEGATE : delegate;

    val list =
        asList(
            new MultipartFormContentProcessor(this.delegate), new UrlencodedFormContentProcessor());

    processors = new HashMap<ContentType, ContentProcessor>(list.size(), 1.F);
    for (ContentProcessor processor : list) {
      processors.put(processor.getSupportedContentType(), processor);
    }
  }

  /**
   * Creates a form encoder that declares what it can handle, for use with {@code MultiEncoder}.
   *
   * <p>It has no delegate: a request it does not accept is left for the other encoders registered
   * alongside it, instead of being swallowed by a fallback of its own.
   *
   * <pre>
   * Feign.builder()
   *     .encoders(FormEncoder.createPredicatedFormEncoder(), new JacksonEncoder());
   * </pre>
   *
   * @return a form encoder guarded by {@link #formRequests()}
   */
  public static PredicatedEncoder createPredicatedFormEncoder() {
    return PredicatedEncoder.of(formRequests(), new FormEncoder(null));
  }

  /**
   * Creates a predicate for the requests a delegate-less form encoder can handle: a form or
   * multipart {@code Content-Type}, carrying a body this encoder knows how to turn into fields.
   *
   * @return the predicate
   */
  public static EncoderPredicate formRequests() {
    return EncoderPredicate.describedAs(
        "Content-Type is a form type and the body is a map or a user pojo",
        (object, bodyType, template) ->
            ContentType.of(getContentTypeValue(template.headers())) != ContentType.UNDEFINED
                && (object instanceof Map || (bodyType != null && isUserPojo(bodyType))));
  }

  @Override
  @SuppressWarnings("unchecked")
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    String contentTypeValue = getContentTypeValue(template.headers());
    val contentType = ContentType.of(contentTypeValue);
    if (processors.containsKey(contentType) == false) {
      delegate.encode(object, bodyType, template);
      return;
    }

    Map<String, Object> data;
    if (object instanceof Map) {
      data = (Map<String, Object>) object;
    } else if (isUserPojo(bodyType)) {
      data = toMap(object);
    } else {
      delegate.encode(object, bodyType, template);
      return;
    }

    val charset = getCharset(contentTypeValue);
    processors.get(contentType).process(template, charset, data);
  }

  /**
   * Returns {@link ContentProcessor} for specific {@link ContentType}.
   *
   * @param type a type for content processor search.
   * @return {@link ContentProcessor} instance for specified type or null.
   */
  public final ContentProcessor getContentProcessor(ContentType type) {
    return processors.get(type);
  }

  @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
  private static String getContentTypeValue(Map<String, Collection<String>> headers) {
    for (val entry : headers.entrySet()) {
      if (!entry.getKey().equalsIgnoreCase(CONTENT_TYPE_HEADER)) {
        continue;
      }
      for (val contentTypeValue : entry.getValue()) {
        if (contentTypeValue == null) {
          continue;
        }
        return contentTypeValue;
      }
    }
    return null;
  }

  private Charset getCharset(String contentTypeValue) {
    val matcher = CHARSET_PATTERN.matcher(contentTypeValue);
    if (!matcher.find()) {
      return UTF_8;
    }
    try {
      return Charset.forName(matcher.group(1));
    } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
      return UTF_8;
    }
  }
}
