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
package feign.codec;

import feign.RequestTemplate;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An encoder that delegates to a list of {@link PredicatedEncoder}s, using the first one whose
 * predicate accepts the request, and falling back to a default encoder when none do.
 *
 * <p>The default encoder is declared first so the predicated ones can be supplied as varargs, but
 * it is consulted <em>last</em> — it is the fallback, not the first choice.
 *
 * <pre>
 * Encoder encoder =
 *     MultiEncoder.of(
 *         new DefaultEncoder(),
 *         PredicatedEncoder.forJsonContentType(new JacksonEncoder()),
 *         PredicatedEncoder.forXmlContentType(new JAXBEncoder()));
 * </pre>
 */
public class MultiEncoder implements Encoder {

  private final Encoder defaultEncoder;

  private final List<PredicatedEncoder> delegates;

  /**
   * Creates an encoder that tries each predicated encoder in order and falls back to {@code
   * defaultEncoder}.
   *
   * @param defaultEncoder the encoder used when no predicate accepts the request
   * @param encoders the predicated encoders, consulted in the order given
   * @return the multi-encoder
   */
  public static Encoder of(Encoder defaultEncoder, PredicatedEncoder... encoders) {
    return of(defaultEncoder, Arrays.asList(encoders));
  }

  /**
   * Creates an encoder that tries each predicated encoder in order and falls back to {@code
   * defaultEncoder}.
   *
   * @param defaultEncoder the encoder used when no predicate accepts the request
   * @param encoders the predicated encoders, consulted in the order given
   * @return the multi-encoder
   */
  public static Encoder of(Encoder defaultEncoder, List<PredicatedEncoder> encoders) {
    return new MultiEncoder(defaultEncoder, encoders);
  }

  private MultiEncoder(Encoder defaultEncoder, List<PredicatedEncoder> delegates) {
    this.defaultEncoder = Objects.requireNonNull(defaultEncoder, "defaultEncoder cannot be null");
    Objects.requireNonNull(delegates, "delegates cannot be null");
    for (PredicatedEncoder delegate : delegates) {
      Objects.requireNonNull(delegate, "delegates cannot contain null");
    }
    this.delegates = Collections.unmodifiableList(new ArrayList<>(delegates));
  }

  /**
   * Encodes using the first delegate whose predicate accepts the request, or the default encoder if
   * none do.
   *
   * @param object {@inheritDoc}
   * @param bodyType {@inheritDoc}
   * @param template {@inheritDoc}
   * @throws EncodeException {@inheritDoc}
   */
  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    for (PredicatedEncoder delegate : delegates) {
      if (delegate.canEncode(object, bodyType, template)) {
        delegate.encode(object, bodyType, template);
        return;
      }
    }
    defaultEncoder.encode(object, bodyType, template);
  }

  @Override
  public String toString() {
    return "MultiEncoder{defaultEncoder=" + defaultEncoder + ", delegates=" + delegates + '}';
  }
}
