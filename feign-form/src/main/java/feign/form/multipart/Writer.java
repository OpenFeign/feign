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

package feign.form.multipart;

import feign.codec.EncodeException;

/**
 *
 * @author Artem Labazin
 */
public interface Writer {

  /**
   * Processing form data to request body.
   *
   * @param output    output writer.
   * @param boundary  data boundary.
   * @param key       name for piece of data.
   * @param value     piece of data.
   *
   * @throws EncodeException in case of any encode exception
   */
  void write (Output output, String boundary, String key, Object value) throws EncodeException;

  /**
   * Answers on question - "could this writer properly write the value".
   *
   * @param value object to write.
   *
   * @return {@code true} - if could write this object, otherwise {@code true}
   */
  boolean isApplicable (Object value);
}
