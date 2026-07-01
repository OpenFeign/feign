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

import feign.PartData;
import feign.Request;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A factory for creating {@link Request.Body} instances from {@link PartData} objects using a list
 * of {@link Encoder} instances.
 */
@RequiredArgsConstructor
public class PartBodyFactory {
  @NonNull private final List<Encoder> partBodyEncoders;

  /**
   * Creates a stream of {@link Request.Body} instances from the given {@link PartData} and
   * variables using the configured {@link Encoder} instances.
   *
   * @param partData the {@link PartData} to create the {@link Request.Body} instances from
   * @param variables the variables to use for template expansion
   * @return a stream of {@link Request.Body} instances
   * @throws EncodeException if none of the configured {@link Encoder} instances can encode the
   *     given {@link PartData}
   */
  public Stream<Request.Body> create(PartData partData, Map<String, Object> variables)
      throws EncodeException {
    return PartDataFlattener.flatten(partData)
        .map(
            part ->
                PartBody.from(tryEncode(part, PartDataTemplateFactory.create(part, variables))));
  }

  private RequestTemplate tryEncode(PartData partData, RequestTemplate template)
      throws EncodeException {
    if (partBodyEncoders.isEmpty()) {
      throw new EncodeException("No part body encoders configured");
    }

    var value = partData.value();
    var bodyType = partData.type();
    var encodeExceptions = new ArrayDeque<EncodeException>();

    for (var encoder : partBodyEncoders) {
      var mutable = RequestTemplate.from(template);

      try {
        encoder.encode(value, bodyType, mutable);

        return mutable;
      } catch (EncodeException e) {
        encodeExceptions.add(e);
      }
    }

    var lastException = encodeExceptions.removeLast();

    encodeExceptions.forEach(lastException::addSuppressed);

    throw new EncodeException(
        String.format(
            "None of the partBodyEncoders was able to encode object: %s, type: %s. Suppressed errors: %d",
            value, bodyType, encodeExceptions.size()),
        lastException);
  }
}
