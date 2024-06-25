/*
 * Copyright 2024 the original author or authors.
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

import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.experimental.FieldDefaults;
import lombok.val;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;

/**
 * A Feign's form encoder.
 *
 * @author Artem Labazin
 */
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class FormEncoder implements Encoder {

  private static final String CONTENT_TYPE_HEADER;

  private static final Pattern CHARSET_PATTERN;

  static {
    CONTENT_TYPE_HEADER = "Content-Type";
    CHARSET_PATTERN = Pattern.compile("(?<=charset=)([\\w\\-]+)");
  }

  Encoder delegate;

  Map<ContentType, ContentProcessor> processors;

  /**
   * Constructor with the default Feign's encoder as a delegate.
   */
  public FormEncoder () {
    this(new Encoder.Default());
  }

  /**
   * Constructor with specified delegate encoder.
   *
   * @param delegate  delegate encoder, if this encoder couldn't encode object.
   */
  public FormEncoder (Encoder delegate) {
    this.delegate = delegate;

    val list = asList(
        new MultipartFormContentProcessor(delegate),
        new UrlencodedFormContentProcessor()
    );

    processors = new HashMap<ContentType, ContentProcessor>(list.size(), 1.F);
    for (ContentProcessor processor : list) {
      processors.put(processor.getSupportedContentType(), processor);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void encode (Object object, Type bodyType, RequestTemplate template) throws EncodeException {
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
   *
   * @return {@link ContentProcessor} instance for specified type or null.
   */
  public final ContentProcessor getContentProcessor (ContentType type) {
    return processors.get(type);
  }

  @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
  private String getContentTypeValue (Map<String, Collection<String>> headers) {
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

  private Charset getCharset (String contentTypeValue) {
    val matcher = CHARSET_PATTERN.matcher(contentTypeValue);
    return matcher.find()
           ? Charset.forName(matcher.group(1))
           : UTF_8;
  }
}
