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
package feign.form.multipart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ContentDispositionTest {
  @Test
  void testParseSimpleFilename() {
    var cd = new ContentDisposition("attachment; filename=foo.txt");

    assertEquals("attachment", cd.getType());
    assertEquals("foo.txt", cd.getFilename());
  }

  @Test
  void testParseQuotedFilename() {
    var cd = new ContentDisposition("form-data; name=\"field\"; filename=\"bar.txt\"");

    assertEquals("form-data", cd.getType());
    assertEquals("field", cd.getParameter("name"));
    assertEquals("bar.txt", cd.getFilename());
  }

  @Test
  void testParseFilenameWithSpaces() {
    var cd = new ContentDisposition("attachment; filename=\"my report.pdf\"");

    assertEquals("my report.pdf", cd.getFilename());
  }

  @Test
  void testParseFilenameWithSemicolon() {
    var cd = new ContentDisposition("attachment; filename=\"data;v2.csv\"");

    assertEquals("data;v2.csv", cd.getFilename());
  }

  @Test
  void testParseFilenameStarPrecedence() {
    var cd =
        new ContentDisposition(
            "attachment; filename=\"fallback.txt\"; filename*=UTF-8''%E2%82%AC%20rates.txt");

    assertEquals("€ rates.txt", cd.getFilename());
  }

  @Test
  void testParseEmptyOrMissingFilename() {
    var cdEmpty = new ContentDisposition("");

    assertNull(cdEmpty.getType());
    assertNull(cdEmpty.getFilename());

    var cdNoFile = new ContentDisposition("form-data; name=\"text-field\"");

    assertEquals("form-data", cdNoFile.getType());
    assertNull(cdNoFile.getFilename());
  }

  @Test
  void testParseCaseInsensitivity() {
    var cd = new ContentDisposition("ATTACHMENT; FILENAME=\"lowercase.txt\"");

    assertEquals("lowercase.txt", cd.getFilename());
  }

  @Test
  void testGetParameters() {
    var cd =
        new ContentDisposition(
            "form-data; NAME=\"avatar\"; filename=\"profile.png\"; Size=12345; custom-param=xyz");

    var parameters = cd.getParameters();

    assertNotNull(parameters);
    assertEquals(4, parameters.size());

    assertEquals("avatar", parameters.get("name"));
    assertEquals("profile.png", parameters.get("filename"));
    assertEquals("12345", parameters.get("size"));
    assertEquals("xyz", parameters.get("custom-param"));

    assertThrows(
        UnsupportedOperationException.class, () -> parameters.put("new-key", "illegal-append"));
  }
}
