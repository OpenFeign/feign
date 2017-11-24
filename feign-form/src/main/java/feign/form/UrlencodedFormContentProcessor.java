/*
 * Copyright 2017 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package feign.form;

import static feign.form.ContentType.URLENCODED;

import feign.RequestTemplate;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.val;

/**
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 */
public class UrlencodedFormContentProcessor implements ContentProcessor {

  @Override
  public void process (RequestTemplate template, Charset charset, Map<String, Object> data) throws Exception {
    val body = new StringBuilder();
    for (val entry : data.entrySet()) {
      if (body.length() > 0) {
        body.append('&');
      }
      body.append(createKeyValuePair(entry, charset));
    }

    val contentTypeValue = new StringBuilder()
        .append(getSupportedContentType().getHeader())
        .append("; charset=").append(charset.name())
        .toString();

    template.header(CONTENT_TYPE_HEADER, contentTypeValue);
    template.body(body.toString().getBytes(charset), charset);
  }

  @Override
  public ContentType getSupportedContentType () {
    return URLENCODED;
  }

  @SneakyThrows
  private String createKeyValuePair (Map.Entry<String, Object> entry, Charset charset) {
    return new StringBuilder()
        .append(URLEncoder.encode(entry.getKey(), charset.name()))
        .append('=')
        .append(URLEncoder.encode(entry.getValue().toString(), charset.name()))
        .toString();
  }
}
