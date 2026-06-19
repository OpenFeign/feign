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

import feign.Request;
import feign.codec.EncodeException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** A factory for creating multipart form data parts from {@link PartContext} instances. */
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartFactory {
  @NonNull @Builder.Default
  private final PartHeadersFactory partHeadersFactory = new PartHeadersFactory();

  @NonNull @Builder.Default private final PartBodyFactory partBodyFactory = new PartBodyFactory();

  /**
   * Creates a multipart form data part from the given {@link PartContext}.
   *
   * @param partContext the {@link PartContext} to create the multipart form data part from
   * @return the created multipart form data part
   * @throws EncodeException if an error occurs while encoding the part body from the part context
   */
  public Request.Body create(PartContext partContext) throws EncodeException {
    var headers = partHeadersFactory.create(partContext);
    var body = partBodyFactory.create(headers, partContext);

    return new Part(headers, body);
  }
}
