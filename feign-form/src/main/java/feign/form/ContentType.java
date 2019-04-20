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

import static lombok.AccessLevel.PRIVATE;

import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.val;

/**
 * Supported content types.
 *
 * @author Artem Labazin
 */
@Getter
@FieldDefaults(level = PRIVATE, makeFinal = true)
public enum ContentType {

  UNDEFINED("undefined"),
  URLENCODED("application/x-www-form-urlencoded"),
  MULTIPART("multipart/form-data");

  String header;

  ContentType (String header) {
    this.header = header;
  }

  /**
   * Parses string to content type.
   *
   * @param str string representation of content type.
   *
   * @return {@link ContentType} instance or {@link ContentType#UNDEFINED}, if there is no such content type.
   */
  public static ContentType of (String str) {
    if (str == null) {
      return UNDEFINED;
    }

    val trimmed = str.trim();
    for (val type : values()) {
      if (trimmed.startsWith(type.getHeader())) {
        return type;
      }
    }
    return UNDEFINED;
  }
}
