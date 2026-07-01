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
package feign.form.spring;

import feign.Request;
import java.io.IOException;
import java.io.OutputStream;
import lombok.NonNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * A {@link Request.Body} implementation that represents a {@link MultipartFile} body.
 *
 * @param multipartFile the {@link MultipartFile} to be sent as the request body
 */
record MultipartFileBody(@NonNull MultipartFile multipartFile) implements Request.Body {
  /**
   * Writes the {@link MultipartFile} content to the given output stream.
   *
   * @param outputStream {@inheritDoc}
   * @throws IOException {@inheritDoc}
   */
  @Override
  public void writeTo(OutputStream outputStream) throws IOException {
    multipartFile.getInputStream().transferTo(outputStream);
  }

  /**
   * Returns the content length of the {@link MultipartFile}.
   *
   * @return the content length of the {@link MultipartFile}
   */
  @Override
  public long contentLength() {
    return multipartFile.getSize();
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public String toString() {
    return "[Binary data (" + contentLength() + " bytes)]";
  }
}
