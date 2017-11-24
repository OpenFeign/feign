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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;

/**
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 */
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class Output implements Closeable {

  ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

  @Getter
  Charset charset;

  public Output write (String string) {
    return write(string.getBytes(charset));
  }

  @SneakyThrows
  public Output write (byte[] bytes) {
    outputStream.write(bytes);
    return this;
  }

  @SneakyThrows
  public Output write (byte[] bytes, int offset, int length) {
    outputStream.write(bytes, offset, length);
    return this;
  }

  public byte[] toByteArray () {
    return outputStream.toByteArray();
  }

  @Override
  public void close () throws IOException {
    outputStream.close();
  }
}
