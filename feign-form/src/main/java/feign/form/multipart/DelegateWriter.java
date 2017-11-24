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

package feign.form.multipart;

import static lombok.AccessLevel.PRIVATE;

import feign.RequestTemplate;
import feign.codec.Encoder;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;

/**
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 */
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class DelegateWriter extends AbstractWriter {

  Encoder delegate;

  ParameterWriter parameterWriter = new ParameterWriter();

  @Override
  public boolean isApplicable (Object value) {
    return true;
  }

  @Override
  protected void write (Output output, String key, Object value) throws Exception {
    val fake = new RequestTemplate();
    delegate.encode(value, value.getClass(), fake);
    val string = new String(fake.body(), output.getCharset()).replaceAll("\n", "");
    parameterWriter.write(output, key, string);
  }
}
