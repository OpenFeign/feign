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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * This object encapsulates a byte array and its associated content type.
 * Use if if you want to specify the content type of your provided byte array.
 */

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class FormData {
  private final String contentType;
  private final byte[] data;

  public FormData (String contentType, byte[] data) {
    this.contentType = contentType;
    this.data = data;
  }

  public String getContentType () {
    return contentType;
  }

  public byte[] getData () {
    return data;
  }
}
