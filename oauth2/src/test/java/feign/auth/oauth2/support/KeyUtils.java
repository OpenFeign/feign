/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.auth.oauth2.support;

public final class KeyUtils {
  private KeyUtils() {}

  public static String parseKey(String fileContent) {
    String[] lines = fileContent.split("\n");
    boolean started = false;

    String key = "";

    for (String line : lines) {
      if (line.startsWith("-----BEGIN")) {
        started = true;
      } else if (line.startsWith("-----END")) {
        break;
      } else if (started) {
        key += line;
      }
      ;
    }

    return key;
  }
}
