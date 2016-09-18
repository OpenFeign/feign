/*
 * Copyright 2016 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.form;

import static feign.Util.UTF_8;

import feign.RequestTemplate;
import java.net.URLEncoder;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.val;

/**
 * Form urlencoded implementation of {@link feign.form.FormDataProcessor}.
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since 30.04.2016
 */
public class FormEncodedDataProcessor implements FormDataProcessor {

  public static final String CONTENT_TYPE;

  static {
    CONTENT_TYPE = "application/x-www-form-urlencoded";
  }

  @Override
  public void process(Map<String, Object> data, RequestTemplate template) {
    val body = new StringBuilder();
    for (Map.Entry<String, Object> entry : data.entrySet()) {
      if (body.length() > 0) {
        body.append('&');
      }
      body.append(createKeyValuePair(entry));
    }

    template.header("Content-Type", CONTENT_TYPE);
    template.body(body.toString());
  }

  @Override
  public String getSupportetContentType() {
    return CONTENT_TYPE;
  }

  @SneakyThrows
  private String createKeyValuePair(Map.Entry<String, Object> entry) {
    return new StringBuilder()
        .append(URLEncoder.encode(entry.getKey(), UTF_8.name()))
        .append('=')
        .append(URLEncoder.encode(entry.getValue().toString(), UTF_8.name()))
        .toString();
  }
}
