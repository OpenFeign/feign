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

import static feign.form.ContentType.URLENCODED;

import feign.CollectionFormat;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.val;

/**
 * An URL encoded form content processor.
 *
 * @author Artem Labazin
 */
public class UrlencodedFormContentProcessor implements ContentProcessor {

  private static final char QUERY_DELIMITER = '&';

  private static final char EQUAL_SIGN = '=';

  @SneakyThrows
  private static String encode(Object string, Charset charset) {
    return URLEncoder.encode(string.toString(), charset.name());
  }

  @Override
  public void process(RequestTemplate template, Charset charset, Map<String, Object> data)
      throws EncodeException {
    val bodyData = new StringBuilder();
    for (Entry<String, Object> entry : data.entrySet()) {
      if (entry == null || entry.getKey() == null) {
        continue;
      }
      if (bodyData.length() > 0) {
        bodyData.append(QUERY_DELIMITER);
      }
      bodyData.append(createKeyValuePair(template.collectionFormat(), entry, charset));
    }

    val contentTypeValue =
        new StringBuilder()
            .append(getSupportedContentType().getHeader())
            .append("; charset=")
            .append(charset.name())
            .toString();

    val bytes = bodyData.toString().getBytes(charset);

    template.header(CONTENT_TYPE_HEADER, Collections.<String>emptyList()); // reset header
    template.header(CONTENT_TYPE_HEADER, contentTypeValue);
    template.body(bytes, charset);
  }

  @Override
  public ContentType getSupportedContentType() {
    return URLENCODED;
  }

  private CharSequence createKeyValuePair(
      CollectionFormat collectionFormat, Entry<String, Object> entry, Charset charset) {
    String encodedKey = encode(entry.getKey(), charset);
    Object value = entry.getValue();

    if (value == null) {
      return encodedKey;
    } else if (value.getClass().isArray()) {
      return createKeyValuePair(
          collectionFormat, encodedKey, Arrays.stream((Object[]) value), charset);
    } else if (value instanceof Collection) {
      return createKeyValuePair(
          collectionFormat, encodedKey, ((Collection<?>) value).stream(), charset);
    }
    return new StringBuilder()
        .append(encodedKey)
        .append(EQUAL_SIGN)
        .append(encode(value, charset))
        .toString();
  }

  private CharSequence createKeyValuePair(
      CollectionFormat collectionFormat, String key, Stream<?> values, Charset charset) {
    val stringValues =
        values.filter(Objects::nonNull).map(Object::toString).collect(Collectors.toList());
    return collectionFormat.join(key, stringValues, charset);
  }
}
