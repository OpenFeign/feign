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
import feign.FeignException;
import feign.Response;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A {@link Decoder} that selects a delegate per response, falling back to a default decoder when no
 * delegate accepts it.
 *
 * <p>Delegates come from two places. A decoder that implements {@link PredicatedDecoder} declares
 * its own applicability and can simply be added; any other decoder is paired with a {@link
 * DecoderPredicate} at the call site:
 *
 * <pre>
 * Feign.builder()
 *     .decoder(
 *         MultiDecoder.builder(new DefaultDecoder())
 *             .add(new JacksonDecoder())
 *             .add(DecoderPredicate.xmlContentType(), new JAXBDecoder())
 *             .add((response, type) -&gt; type == byte[].class, new BinaryDecoder())
 *             .build());
 * </pre>
 *
 * <p>Delegates are consulted in the order they were added, so the narrowest predicate should come
 * first. The default decoder is consulted last.
 *
 * @see PredicatedDecoder
 * @see DecoderPredicate
 */
@Experimental
public class MultiDecoder implements Decoder {

  private final Decoder defaultDecoder;

  private final List<Delegate> delegates;

  private MultiDecoder(Decoder defaultDecoder, List<Delegate> delegates) {
    this.defaultDecoder = defaultDecoder;
    this.delegates = Collections.unmodifiableList(new ArrayList<>(delegates));
  }

  /**
   * Starts building a multi-decoder.
   *
   * @param defaultDecoder the decoder used when no delegate accepts the response
   * @return the builder
   */
  public static Builder builder(Decoder defaultDecoder) {
    return new Builder(defaultDecoder);
  }

  /**
   * Decodes using the first delegate that accepts the response, or the default decoder if none do.
   *
   * @param response {@inheritDoc}
   * @param type {@inheritDoc}
   * @return {@inheritDoc}
   * @throws IOException {@inheritDoc}
   * @throws DecodeException {@inheritDoc}
   * @throws FeignException {@inheritDoc}
   */
  @Override
  public Object decode(Response response, Type type)
      throws IOException, DecodeException, FeignException {
    for (Delegate delegate : delegates) {
      if (delegate.predicate.canDecode(response, type)) {
        return delegate.decoder.decode(response, type);
      }
    }
    return defaultDecoder.decode(response, type);
  }

  @Override
  public String toString() {
    return "MultiDecoder{defaultDecoder=" + defaultDecoder + ", delegates=" + delegates + '}';
  }

  private static final class Delegate {
    private final DecoderPredicate predicate;
    private final Decoder decoder;

    Delegate(DecoderPredicate predicate, Decoder decoder) {
      this.predicate = predicate;
      this.decoder = decoder;
    }

    @Override
    public String toString() {
      return decoder.toString();
    }
  }

  /** Collects the delegates of a {@link MultiDecoder}. */
  @Experimental
  public static final class Builder {

    private final Decoder defaultDecoder;

    private final List<Delegate> delegates = new ArrayList<>();

    private Builder(Decoder defaultDecoder) {
      this.defaultDecoder = Objects.requireNonNull(defaultDecoder, "defaultDecoder cannot be null");
    }

    /**
     * Adds a decoder that declares its own applicability.
     *
     * @param decoder the decoder, consulted via {@link PredicatedDecoder#canDecode}
     */
    public Builder add(PredicatedDecoder decoder) {
      Objects.requireNonNull(decoder, "decoder cannot be null");
      return add(decoder::canDecode, decoder);
    }

    /**
     * Adds any decoder, guarded by the given predicate. Use this for decoders that do not implement
     * {@link PredicatedDecoder}, including ones you do not control.
     *
     * @param predicate decides whether the decoder handles a response
     * @param decoder the decoder to delegate to
     */
    public Builder add(DecoderPredicate predicate, Decoder decoder) {
      Objects.requireNonNull(predicate, "predicate cannot be null");
      Objects.requireNonNull(decoder, "decoder cannot be null");
      delegates.add(new Delegate(predicate, decoder));
      return this;
    }

    /** Builds the multi-decoder. */
    public MultiDecoder build() {
      return new MultiDecoder(defaultDecoder, delegates);
    }
  }
}
