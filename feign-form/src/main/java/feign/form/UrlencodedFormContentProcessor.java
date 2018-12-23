/*
 * Copyright 2018 Artem Labazin
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
import java.util.Map;
import java.util.Map.Entry;

import feign.Request;
import feign.RequestTemplate;

import lombok.SneakyThrows;
import lombok.val;

/**
 *
 * @author Artem Labazin
 */
public class UrlencodedFormContentProcessor implements ContentProcessor {

  @Override
  public void process (RequestTemplate template, Charset charset, Map<String, Object> data) throws Exception {
    val bodyData = new StringBuilder();
    for (Entry<String, Object> entry : data.entrySet()) {
      if (bodyData.length() > 0) {
        bodyData.append('&');
      }
      bodyData.append(createKeyValuePair(entry, charset));
    }

    val contentTypeValue = new StringBuilder()
        .append(getSupportedContentType().getHeader())
        .append("; charset=").append(charset.name())
        .toString();

    val bytes = bodyData.toString().getBytes(charset);
    val body = Request.Body.encoded(bytes, charset);

    template.header(CONTENT_TYPE_HEADER, new String[0]); // reset header
    template.header(CONTENT_TYPE_HEADER, contentTypeValue);
    template.body(body);
  }

  @Override
  public ContentType getSupportedContentType () {
    return URLENCODED;
  }

  @SneakyThrows
  private String createKeyValuePair (Entry<String, Object> entry, Charset charset) {
    return new StringBuilder()
        .append(URLEncoder.encode(entry.getKey(), charset.name()))
        .append('=')
        .append(URLEncoder.encode(entry.getValue().toString(), charset.name()))
        .toString();
  }
}
