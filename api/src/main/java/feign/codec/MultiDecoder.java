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
import feign.Util;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A {@link Decoder} that hands each response to the first decoder that accepts it.
 *
 * <p>Decoders come from two places. A decoder that implements {@link PredicatedDecoder} declares
 * its own applicability and can simply be added; any other decoder is paired with a {@link
 * DecoderPredicate} at the call site:
 *
 * <pre>
 * Feign.builder()
 *     .decoder(
 *         MultiDecoder.builder()
 *             .add(new JacksonDecoder())
 *             .add(DecoderPredicate.xmlContentType(), new JAXBDecoder())
 *             .add((response, type) -&gt; type == byte[].class, new BinaryDecoder())
 *             .add(DecoderPredicate.any(), new DefaultDecoder())
 *             .build());
 * </pre>
 *
 * <p>Decoders are consulted in the order they were added, so the narrowest one comes first. There
 * is no implicit fallback: a response no decoder accepts fails with a {@link DecodeException}
 * naming what was tried. Add a decoder guarded by {@link DecoderPredicate#any()} last to act as a
 * default, as above.
 *
 * @see PredicatedDecoder
 * @see DecoderPredicate
 */
@Experimental
public class MultiDecoder implements Decoder {

  private final List<PredicatedDecoder> decoders;

  private MultiDecoder(List<PredicatedDecoder> decoders) {
    this.decoders = Collections.unmodifiableList(new ArrayList<>(decoders));
  }

  /** Starts building a multi-decoder. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Decodes using the first decoder that accepts the response.
   *
   * @param response {@inheritDoc}
   * @param type {@inheritDoc}
   * @return {@inheritDoc}
   * @throws IOException {@inheritDoc}
   * @throws DecodeException when no decoder accepts the response, or the chosen one fails
   * @throws FeignException {@inheritDoc}
   */
  @Override
  public Object decode(Response response, Type type)
      throws IOException, DecodeException, FeignException {
    for (PredicatedDecoder decoder : decoders) {
      if (decoder.canDecode(response, type)) {
        return decoder.decode(response, type);
      }
    }
    throw new DecodeException(
        response.status(), unableToDecode(response, type), response.request());
  }

  private String unableToDecode(Response response, Type type) {
    StringBuilder message =
        new StringBuilder("Unable to decode ")
            .append(response.status())
            .append(" response (Content-Type: ")
            .append(contentTypes(response))
            .append(") as ")
            .append(type == null ? "the expected type" : type.getTypeName());
    if (decoders.isEmpty()) {
      return message.append(". No decoders were configured.").toString();
    }
    message.append(". Decoders tried, in order:");
    for (PredicatedDecoder decoder : decoders) {
      message.append("\n  - ").append(PairedDecoder.describe(decoder));
    }
    return message
        .append("\nAdd a decoder guarded by DecoderPredicate.any() last to act as a default.")
        .toString();
  }

  private static String contentTypes(Response response) {
    String contentTypes =
        response.headers().entrySet().stream()
            .filter(header -> Util.CONTENT_TYPE.equalsIgnoreCase(header.getKey()))
            .map(Map.Entry::getValue)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .collect(Collectors.joining(", "));
    return contentTypes.isEmpty() ? "not set" : contentTypes;
  }

  @Override
  public String toString() {
    return "MultiDecoder"
        + decoders.stream().map(PairedDecoder::describe).collect(Collectors.toList());
  }

  /** Collects the decoders of a {@link MultiDecoder}. */
  @Experimental
  public static final class Builder {

    private final List<PredicatedDecoder> decoders = new ArrayList<>();

    private Builder() {}

    /**
     * Adds a decoder that declares its own applicability.
     *
     * @param decoder the decoder, consulted via {@link PredicatedDecoder#canDecode}
     */
    public Builder add(PredicatedDecoder decoder) {
      decoders.add(Objects.requireNonNull(decoder, "decoder cannot be null"));
      return this;
    }

    /**
     * Adds any decoder, guarded by the given predicate. Use this for decoders that do not implement
     * {@link PredicatedDecoder}, including ones you do not control.
     *
     * @param predicate decides whether the decoder handles a response
     * @param decoder the decoder to delegate to
     */
    public Builder add(DecoderPredicate predicate, Decoder decoder) {
      return add(PredicatedDecoder.of(predicate, decoder));
    }

    /** Builds the multi-decoder. */
    public MultiDecoder build() {
      return new MultiDecoder(decoders);
    }
  }
}
