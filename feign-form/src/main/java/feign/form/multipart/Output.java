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
 * Output representation utility class.
 *
 * @author Artem Labazin
 */
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class Output implements Closeable {

  ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

  @Getter
  Charset charset;

  /**
   * Writes the string to the output.
   *
   * @param string string to write to this output
   *
   * @return this output
   */
  public Output write (String string) {
    return write(string.getBytes(charset));
  }

  /**
   * Writes the byte array to the output.
   *
   * @param bytes byte arrays to write to this output
   *
   * @return this output
   */
  @SneakyThrows
  public Output write (byte[] bytes) {
    outputStream.write(bytes);
    return this;
  }

  /**
   * Writes the byte array to the output with specified offset and fixed length.
   *
   * @param bytes  byte arrays to write to this output
   * @param offset the offset within the array of the first byte to be read. Must be non-negative and no larger than <tt>bytes.length</tt>
   * @param length the number of bytes to be read from the given array
   *
   * @return this output
   */
  @SneakyThrows
  public Output write (byte[] bytes, int offset, int length) {
    outputStream.write(bytes, offset, length);
    return this;
  }

  /**
   * Returns byte array representation of this output class.
   *
   * @return byte array representation of output
   */
  public byte[] toByteArray () {
    return outputStream.toByteArray();
  }

  @Override
  public void close () throws IOException {
    outputStream.close();
  }
}
