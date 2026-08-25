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
import feign.Request;
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
 * <p>A multi-decoder is itself a {@link PredicatedDecoder}, accepting whatever any of its decoders
 * accepts, so one can be added to another. That is how a library ships a set of decoders as a
 * single unit: given a hypothetical {@code AcmeFeign.decoders()} returning a multi-decoder over
 * that library's decoders, the whole set is added in one go:
 *
 * <pre>
 * Feign.builder().decoders(AcmeFeign.decoders(), new JacksonDecoder());
 * </pre>
 *
 * @see PredicatedDecoder
 * @see DecoderPredicate
 */
@Experimental
public class MultiDecoder implements PredicatedDecoder {

  private final List<PredicatedDecoder> decoders;

  private MultiDecoder(List<PredicatedDecoder> decoders) {
    this.decoders = Collections.unmodifiableList(new ArrayList<>(decoders));
  }

  /** Starts building a multi-decoder. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Whether any of the decoders accepts the response.
   *
   * @param response {@inheritDoc}
   * @param type {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public boolean canDecode(Response response, Type type) {
    return decoders.stream().anyMatch(decoder -> decoder.canDecode(response, type));
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
            .append(" response (")
            .append(headers(response))
            .append(") as ")
            .append(type == null ? "the expected type" : type.getTypeName())
            .append(". Decoders tried, in order:");
    appendTo(message, "\n  ");
    return message
        .append("\nRegister a decoder that accepts it, or add a catch-all")
        .append(" (DecoderPredicate.any()) last.")
        .toString();
  }

  /**
   * Lists the decoders one per line, unfolding nested multi-decoders so that a set contributed as a
   * single unit still shows what it contains.
   */
  private void appendTo(StringBuilder message, String indent) {
    for (PredicatedDecoder decoder : decoders) {
      if (decoder instanceof MultiDecoder) {
        message.append(indent).append("- MultiDecoder:");
        ((MultiDecoder) decoder).appendTo(message, indent + "  ");
      } else {
        message.append(indent).append("- ").append(PairedDecoder.describe(decoder));
      }
    }
  }

  /**
   * The headers a decoder is most likely to have been chosen on: what came back, and what was asked
   * for. Everything else a predicate looks at belongs in that predicate's own description, which is
   * listed alongside it.
   */
  private static String headers(Response response) {
    String contentType = header(response.headers(), Util.CONTENT_TYPE);
    StringBuilder headers =
        new StringBuilder(Util.CONTENT_TYPE)
            .append(": ")
            .append(contentType == null ? "not set" : contentType);
    Request request = response.request();
    String accept = request == null ? null : header(request.headers(), Util.ACCEPT);
    if (accept != null) {
      headers.append(", ").append(Util.ACCEPT).append(": ").append(accept);
    }
    return headers.toString();
  }

  private static String header(Map<String, Collection<String>> headers, String name) {
    String values =
        headers.entrySet().stream()
            .filter(header -> name.equalsIgnoreCase(header.getKey()))
            .map(Map.Entry::getValue)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .collect(Collectors.joining(", "));
    return values.isEmpty() ? null : values;
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
     * {@link PredicatedDecoder}, including ones you do not control. The predicate is the whole
     * answer: whatever the decoder may declare about itself is replaced, so this can widen a
     * decoder as well as narrow it. Use {@link #narrow(DecoderPredicate, Decoder)} to keep the
     * decoder's own declaration.
     *
     * @param predicate decides whether the decoder handles a response
     * @param decoder the decoder to delegate to
     */
    public Builder add(DecoderPredicate predicate, Decoder decoder) {
      return add(PredicatedDecoder.of(predicate, decoder));
    }

    /**
     * Adds a decoder, narrowed by the given predicate. If the decoder is itself a {@link
     * PredicatedDecoder}, the predicate applies in addition to the decoder's own {@code canDecode}
     * rather than instead of it: both have to accept the response.
     *
     * <pre>
     * MultiDecoder.builder()
     *     .narrow(DecoderPredicate.status(200), new JacksonDecoder())
     *     .add(DecoderPredicate.any(), new DefaultDecoder())
     *     .build();
     * </pre>
     *
     * @param predicate narrows what the decoder handles
     * @param decoder the decoder to delegate to
     */
    public Builder narrow(DecoderPredicate predicate, Decoder decoder) {
      return add(PredicatedDecoder.narrowing(predicate, decoder));
    }

    /**
     * Builds the multi-decoder.
     *
     * @throws IllegalStateException if no decoder was added
     */
    public MultiDecoder build() {
      if (decoders.isEmpty()) {
        throw new IllegalStateException("at least one decoder is required");
      }
      return new MultiDecoder(decoders);
    }
  }
}
