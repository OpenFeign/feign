/*
 * Copyright 2013 Netflix, Inc.
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
package feign.codec;

import feign.RequestTemplate;
import org.testng.annotations.Test;

import java.util.Date;

import static org.testng.Assert.assertEquals;

public class DefaultEncoderTest {
  private final Encoder encoder = new Encoder.Default();

  @Test public void testEncodesStrings() throws Exception {
    String content = "This is my content";
    RequestTemplate template = new RequestTemplate();
    encoder.encode(content, template);
    assertEquals(template.body(), content);
  }

  @Test(expectedExceptions = EncodeException.class, expectedExceptionsMessageRegExp = ".* is not a type supported by this encoder.")
  public void testRefusesToEncodeOtherTypes() throws Exception {
    encoder.encode(new Date(), new RequestTemplate());
  }
}
