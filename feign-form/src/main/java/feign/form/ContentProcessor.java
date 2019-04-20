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

import java.nio.charset.Charset;
import java.util.Map;

import feign.RequestTemplate;
import feign.codec.EncodeException;

/**
 * Interface for content processors.
 *
 * @see MultipartFormContentProcessor
 * @see UrlencodedFormContentProcessor
 *
 * @author Artem Labazin
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
   * @throws EncodeException in case of any encode exception
   */
  void process (RequestTemplate template, Charset charset, Map<String, Object> data) throws EncodeException;

  /**
   * Returns supported {@link ContentType} of this processor.
   *
   * @return supported content type enum value.
   */
  ContentType getSupportedContentType ();
}
