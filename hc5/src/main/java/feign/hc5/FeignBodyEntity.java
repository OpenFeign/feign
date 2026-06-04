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
package feign.hc5;

import feign.Request;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;

/**
 * A wrapper for {@link Request.Body} that implements Apache HttpClient's {@link
 * AbstractHttpEntity}.
 */
final class FeignBodyEntity extends AbstractHttpEntity {
  private final Request.Body body;

  /**
   * Creates a new {@link FeignBodyEntity} with the given body and content type.
   *
   * @param body the body to wrap
   * @param contentType the content type of the body
   */
  FeignBodyEntity(Request.Body body, ContentType contentType) {
    super(contentType, null, body.contentLength() < 0);
    this.body = body;
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public long getContentLength() {
    return body.contentLength();
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public InputStream getContent() {
    throw new UnsupportedOperationException("Streaming request body does not expose InputStream");
  }

  /**
   * {@inheritDoc}
   *
   * @param outStream {@inheritDoc}
   * @throws {@inheritDoc}
   */
  @Override
  public void writeTo(OutputStream outStream) throws IOException {
    body.writeTo(outStream);
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public boolean isRepeatable() {
    return body.isRepeatable();
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public boolean isStreaming() {
    return !isRepeatable();
  }

  /** Does nothing. The caller is responsible for closing the output stream. */
  @Override
  public void close() {}
}
