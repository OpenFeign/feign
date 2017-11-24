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

import feign.RequestTemplate;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Interface for content processors.
 *
 * @see MultipartFormContentProcessor
 * @see UrlencodedFormContentProcessor
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 */
public interface ContentProcessor {

  String CONTENT_TYPE_HEADER = "Content-Type";

  String CRLF = "\r\n";

  /**
   * Processes a request.
   *
   * @param template  Feign's request template.
   * @param charset   request charset from 'Content-Type' header (UTF-8 by default).
   * @param data      reqeust data.
   *
   * @throws Exception in case of...exception
   */
  void process (RequestTemplate template, Charset charset, Map<String, Object> data) throws Exception;

  /**
   * Returns supported {@link ContentType} of this processor.
   *
   * @return supported content type enum value.
   */
  ContentType getSupportedContentType ();
}
