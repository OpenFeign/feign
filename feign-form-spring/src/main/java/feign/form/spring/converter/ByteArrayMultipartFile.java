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

package feign.form.spring.converter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import lombok.NonNull;
import lombok.Value;
import org.springframework.web.multipart.MultipartFile;

/**
 * Straight-forward implementation of interface {@link MultipartFile} where the file
 * data is held as a byte array in memory.
 */
@Value
class ByteArrayMultipartFile implements MultipartFile {

  String name;

  String originalFilename;

  String contentType;

  @NonNull
  byte[] bytes;

  @Override
  public boolean isEmpty () {
    return bytes.length == 0;
  }

  @Override
  public long getSize () {
    return bytes.length;
  }

  @Override
  public InputStream getInputStream () {
    return new ByteArrayInputStream(bytes);
  }

  @Override
  public void transferTo (File destination) throws IOException {
    OutputStream outputStream = null;
    try {
      outputStream = new FileOutputStream(destination);
      outputStream.write(bytes);
    } finally {
      if (outputStream != null) {
        outputStream.close();
      }
    }
  }
}
