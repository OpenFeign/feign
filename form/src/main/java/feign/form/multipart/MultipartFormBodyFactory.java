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

import feign.codec.EncodeException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** A factory for creating {@link MultipartFormBody} instances from a map of form data. */
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultipartFormBodyFactory {
  @NonNull @Builder.Default
  private final PartContextResolverChain partContextResolverChain = new PartContextResolverChain();

  @NonNull @Builder.Default private final PartFactory partFactory = new PartFactory();

  /**
   * Creates a {@link MultipartFormBody} from the given form data.
   *
   * @param formData the form data to create the multipart form body from
   * @return a {@link MultipartFormBody} instance representing the given form data
   * @throws EncodeException if an error occurs while encoding the form data into multipart form
   *     body parts
   */
  public MultipartFormBody create(Map<String, Object> formData) throws EncodeException {
    return formData.entrySet().stream()
        .flatMap(this::resolve)
        .map(partFactory::create)
        .collect(Collectors.collectingAndThen(Collectors.toList(), MultipartFormBody::new));
  }

  private Stream<PartContext> resolve(Map.Entry<String, Object> entry) {
    return partContextResolverChain.resolve(new PartContext(entry.getKey(), entry.getValue()));
  }
}
