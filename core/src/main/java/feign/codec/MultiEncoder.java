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

import feign.Experimental;
import feign.RequestTemplate;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An {@link Encoder} that selects a delegate per request, falling back to a default encoder when no
 * delegate accepts it.
 *
 * <p>Delegates come from two places. An encoder that implements {@link PredicatedEncoder} declares
 * its own applicability and can simply be added; any other encoder is paired with an {@link
 * EncoderPredicate} at the call site:
 *
 * <pre>
 * Feign.builder()
 *     .encoder(
 *         MultiEncoder.builder(new DefaultEncoder())
 *             .add(new JacksonEncoder())
 *             .add(EncoderPredicate.xmlContentType(), new JAXBEncoder())
 *             .add((object, bodyType, template) -&gt; bodyType == byte[].class, new BinaryEncoder())
 *             .build());
 * </pre>
 *
 * <p>Delegates are consulted in the order they were added, so the narrowest predicate should come
 * first. The default encoder is consulted last.
 *
 * @see PredicatedEncoder
 * @see EncoderPredicate
 */
@Experimental
public class MultiEncoder implements Encoder {

  private final Encoder defaultEncoder;

  private final List<Delegate> delegates;

  private MultiEncoder(Encoder defaultEncoder, List<Delegate> delegates) {
    this.defaultEncoder = defaultEncoder;
    this.delegates = Collections.unmodifiableList(new ArrayList<>(delegates));
  }

  /**
   * Starts building a multi-encoder.
   *
   * @param defaultEncoder the encoder used when no delegate accepts the request
   * @return the builder
   */
  public static Builder builder(Encoder defaultEncoder) {
    return new Builder(defaultEncoder);
  }

  /**
   * Encodes using the first delegate that accepts the request, or the default encoder if none do.
   *
   * @param object {@inheritDoc}
   * @param bodyType {@inheritDoc}
   * @param template {@inheritDoc}
   * @throws EncodeException {@inheritDoc}
   */
  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    for (Delegate delegate : delegates) {
      if (delegate.predicate.canEncode(object, bodyType, template)) {
        delegate.encoder.encode(object, bodyType, template);
        return;
      }
    }
    defaultEncoder.encode(object, bodyType, template);
  }

  @Override
  public String toString() {
    return "MultiEncoder{defaultEncoder=" + defaultEncoder + ", delegates=" + delegates + '}';
  }

  private static final class Delegate {
    private final EncoderPredicate predicate;
    private final Encoder encoder;

    Delegate(EncoderPredicate predicate, Encoder encoder) {
      this.predicate = predicate;
      this.encoder = encoder;
    }

    @Override
    public String toString() {
      return encoder.toString();
    }
  }

  /** Collects the delegates of a {@link MultiEncoder}. */
  @Experimental
  public static final class Builder {

    private final Encoder defaultEncoder;

    private final List<Delegate> delegates = new ArrayList<>();

    private Builder(Encoder defaultEncoder) {
      this.defaultEncoder = Objects.requireNonNull(defaultEncoder, "defaultEncoder cannot be null");
    }

    /**
     * Adds an encoder that declares its own applicability.
     *
     * @param encoder the encoder, consulted via {@link PredicatedEncoder#canEncode}
     */
    public Builder add(PredicatedEncoder encoder) {
      Objects.requireNonNull(encoder, "encoder cannot be null");
      return add(encoder::canEncode, encoder);
    }

    /**
     * Adds any encoder, guarded by the given predicate. Use this for encoders that do not implement
     * {@link PredicatedEncoder}, including ones you do not control.
     *
     * @param predicate decides whether the encoder handles a request
     * @param encoder the encoder to delegate to
     */
    public Builder add(EncoderPredicate predicate, Encoder encoder) {
      Objects.requireNonNull(predicate, "predicate cannot be null");
      Objects.requireNonNull(encoder, "encoder cannot be null");
      delegates.add(new Delegate(predicate, encoder));
      return this;
    }

    /** Builds the multi-encoder. */
    public MultiEncoder build() {
      return new MultiEncoder(defaultEncoder, delegates);
    }
  }
}
