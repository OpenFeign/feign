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

import static lombok.AccessLevel.PRIVATE;

import feign.Request;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * A delegate writer.
 *
 * @author Artem Labazin
 */
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class DelegateWriter extends AbstractWriter {

  Encoder delegate;

  SingleParameterWriter parameterWriter = new SingleParameterWriter();

  @Override
  public boolean isApplicable(Object value) {
    return true;
  }

  @Override
  protected void write(Output output, String key, Object value) throws EncodeException {
    final var fake = new RequestTemplate();
    delegate.encode(value, value.getClass(), fake);
    fake.requestBody().ifPresent(body -> write(output, key, body));
  }

  private void write(Output output, String key, Request.Body body) {
    try {
      final var encoded = body.writeToString(StandardCharsets.UTF_8).replaceAll("\n", "");
      parameterWriter.write(output, key, encoded);
    } catch (IOException e) {
      throw new EncodeException("Failed to write request body for key: " + key, e);
    }
  }
}
