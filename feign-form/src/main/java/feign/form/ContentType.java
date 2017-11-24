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

import lombok.Getter;
import lombok.val;

/**
 * Supported content types.
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 */
public enum ContentType {

  UNDEFINED("undefined"),
  URLENCODED("application/x-www-form-urlencoded"),
  MULTIPART("multipart/form-data");

  @Getter
  private final String header;

  private ContentType (String header) {
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
