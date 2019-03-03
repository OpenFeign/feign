/*
 * Copyright 2019 the original author or authors.
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

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import feign.Request;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import lombok.SneakyThrows;
import lombok.val;

/**
 *
 * @author Artem Labazin
 */
public class UrlencodedFormContentProcessor implements ContentProcessor {

  private static final char QUERY_DELIMITER = '&';

  private static final char EQUAL_SIGN = '=';

  @SneakyThrows
  private static String encode (Object string, Charset charset) {
    return URLEncoder.encode(string.toString(), charset.name());
  }

  @Override
  public void process (RequestTemplate template, Charset charset, Map<String, Object> data) throws EncodeException {
    val bodyData = new StringBuilder();
    for (Entry<String, Object> entry : data.entrySet()) {
      if (entry == null || entry.getKey() == null) {
        continue;
      }
      if (bodyData.length() > 0) {
        bodyData.append(QUERY_DELIMITER);
      }
      bodyData.append(createKeyValuePair(entry, charset));
    }

    val contentTypeValue = new StringBuilder()
        .append(getSupportedContentType().getHeader())
        .append("; charset=").append(charset.name())
        .toString();

    val bytes = bodyData.toString().getBytes(charset);
    val body = Request.Body.encoded(bytes, charset);

    template.header(CONTENT_TYPE_HEADER, Collections.<String>emptyList()); // reset header
    template.header(CONTENT_TYPE_HEADER, contentTypeValue);
    template.body(body);
  }

  @Override
  public ContentType getSupportedContentType () {
    return URLENCODED;
  }

  private String createKeyValuePair (Entry<String, Object> entry, Charset charset) {
    String encodedKey = encode(entry.getKey(), charset);
    Object value = entry.getValue();

    if (value == null) {
      return encodedKey;
    } else if (value.getClass().isArray()) {
      return createKeyValuePairFromArray(encodedKey, value, charset);
    } else if (value instanceof Collection) {
      return createKeyValuePairFromCollection(encodedKey, value, charset);
    }
    return new StringBuilder()
        .append(encodedKey)
        .append(EQUAL_SIGN)
        .append(encode(value, charset))
        .toString();
  }

  @SuppressWarnings("unchecked")
  private String createKeyValuePairFromCollection (String key, Object values, Charset charset) {
    val collection = (Collection) values;
    val array = collection.toArray(new Object[0]);
    return createKeyValuePairFromArray(key, array, charset);
  }

  private String createKeyValuePairFromArray (String key, Object values, Charset charset) {
    val result = new StringBuilder();
    val array = (Object[]) values;

    for (int index = 0; index < array.length; index++) {
      val value = array[index];
      if (value == null) {
        continue;
      }

      if (index > 0) {
        result.append(QUERY_DELIMITER);
      }

      result
          .append(key)
          .append(EQUAL_SIGN)
          .append(encode(value, charset));
    }
    return result.toString();
  }
}
