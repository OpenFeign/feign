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
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.core.codec.DefaultEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** A factory for creating part bodies from {@link PartContext} instances. */
@RequiredArgsConstructor
public class PartBodyFactory {
  @NonNull private final Collection<Encoder> partBodyEncoders;

  /** Creates a new {@link PartBodyFactory} with the default list of {@link Encoder} instances. */
  public PartBodyFactory() {
    this(defaultEncoders());
  }

  /**
   * Creates a new {@link PartBodyFactory} with the customized list of {@link Encoder} instances.
   *
   * @param partBodyEncodersCustomizer a {@link Consumer} that can be used to customize the list of
   *     {@link Encoder}
   */
  public PartBodyFactory(@NonNull Consumer<List<Encoder>> partBodyEncodersCustomizer) {
    this(customEncoders(partBodyEncodersCustomizer));
  }

  private static List<Encoder> defaultEncoders() {
    var encoders = new ArrayList<Encoder>();

    encoders.add(new DefaultEncoder());

    return encoders;
  }

  private static List<Encoder> customEncoders(Consumer<List<Encoder>> partBodyEncodersCustomizer) {
    var encoders = defaultEncoders();

    partBodyEncodersCustomizer.accept(encoders);

    return encoders;
  }

  /**
   * Creates a new part body from the given headers and part context.
   *
   * @param partHeaders the headers for the part
   * @param partContext the context for the part, which may contain the content to be encoded into
   *     the body
   * @return the created part body
   * @throws EncodeException if no suitable encoder is found for the content in the part context
   */
  public Request.Body create(Map<String, String> partHeaders, PartContext partContext)
      throws EncodeException {
    var content = partContext.content().orElse(null);
    var bodyType = content != null ? content.getClass() : Void.class;

    for (var encoder : partBodyEncoders) {
      var template = new RequestTemplate();

      partHeaders.forEach(template::headerLiteral);

      try {
        encoder.encode(content, bodyType, template);

        return template.requestBody().orElse(null);
      } catch (EncodeException ignored) {
      }
    }

    throw new EncodeException("No suitable part body encoder found for object: " + content);
  }
}
