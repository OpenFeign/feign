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
import feign.Util;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An {@link Encoder} that hands each request to the first encoder that accepts it.
 *
 * <p>Encoders come from two places. An encoder that implements {@link PredicatedEncoder} declares
 * its own applicability and can simply be added; any other encoder is paired with an {@link
 * EncoderPredicate} at the call site:
 *
 * <pre>
 * Feign.builder()
 *     .encoder(
 *         MultiEncoder.builder()
 *             .add(new JacksonEncoder())
 *             .add(EncoderPredicate.xmlContentType(), new JAXBEncoder())
 *             .add((object, bodyType, template) -&gt; bodyType == byte[].class, new BinaryEncoder())
 *             .add(EncoderPredicate.any(), new DefaultEncoder())
 *             .build());
 * </pre>
 *
 * <p>Encoders are consulted in the order they were added, so the narrowest one comes first. There
 * is no implicit fallback: a request no encoder accepts fails with an {@link EncodeException}
 * naming what was tried. Add an encoder guarded by {@link EncoderPredicate#any()} last to act as a
 * default, as above.
 *
 * <p>A multi-encoder is itself a {@link PredicatedEncoder}, accepting whatever any of its encoders
 * accepts, so one can be added to another. That is how a library ships a set of encoders as a
 * single unit: given a hypothetical {@code AcmeFeign.encoders()} returning a multi-encoder over
 * that library's encoders, the whole set is added in one go:
 *
 * <pre>
 * Feign.builder().encoders(AcmeFeign.encoders(), new JacksonEncoder());
 * </pre>
 *
 * @see PredicatedEncoder
 * @see EncoderPredicate
 */
@Experimental
public class MultiEncoder implements PredicatedEncoder {

  private final List<PredicatedEncoder> encoders;

  private MultiEncoder(List<PredicatedEncoder> encoders) {
    this.encoders = Collections.unmodifiableList(new ArrayList<>(encoders));
  }

  /** Starts building a multi-encoder. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Whether any of the encoders accepts the request.
   *
   * @param object {@inheritDoc}
   * @param bodyType {@inheritDoc}
   * @param template {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
    return encoders.stream().anyMatch(encoder -> encoder.canEncode(object, bodyType, template));
  }

  /**
   * Encodes using the first encoder that accepts the request.
   *
   * @param object {@inheritDoc}
   * @param bodyType {@inheritDoc}
   * @param template {@inheritDoc}
   * @throws EncodeException when no encoder accepts the request, or the chosen one fails
   */
  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    for (PredicatedEncoder encoder : encoders) {
      if (encoder.canEncode(object, bodyType, template)) {
        encoder.encode(object, bodyType, template);
        return;
      }
    }
    throw new EncodeException(unableToEncode(bodyType, template));
  }

  private String unableToEncode(Type bodyType, RequestTemplate template) {
    StringBuilder message =
        new StringBuilder("Unable to encode ")
            .append(bodyType == null ? "request body" : bodyType.getTypeName())
            .append(" (")
            .append(headers(template))
            .append(')');
    if (template.method() != null) {
      message.append(" for ").append(template.method()).append(' ').append(template.path());
    }
    message.append(". Encoders tried, in order:");
    appendTo(message, "\n  ");
    return message
        .append("\nRegister an encoder that accepts it, or add a catch-all")
        .append(" (EncoderPredicate.any()) last.")
        .toString();
  }

  /**
   * Lists the encoders one per line, unfolding nested multi-encoders so that a set contributed as a
   * single unit still shows what it contains.
   */
  private void appendTo(StringBuilder message, String indent) {
    for (PredicatedEncoder encoder : encoders) {
      if (encoder instanceof MultiEncoder) {
        message.append(indent).append("- MultiEncoder:");
        ((MultiEncoder) encoder).appendTo(message, indent + "  ");
      } else {
        message.append(indent).append("- ").append(PairedEncoder.describe(encoder));
      }
    }
  }

  /**
   * The headers an encoder is most likely to have been chosen on. Everything else a predicate looks
   * at belongs in that predicate's own description, which is listed alongside it.
   */
  private static String headers(RequestTemplate template) {
    String contentType = header(template, Util.CONTENT_TYPE);
    StringBuilder headers =
        new StringBuilder(Util.CONTENT_TYPE)
            .append(": ")
            .append(contentType == null ? "not set" : contentType);
    String accept = header(template, Util.ACCEPT);
    if (accept != null) {
      headers.append(", ").append(Util.ACCEPT).append(": ").append(accept);
    }
    return headers.toString();
  }

  private static String header(RequestTemplate template, String name) {
    String values =
        template.headers().entrySet().stream()
            .filter(header -> name.equalsIgnoreCase(header.getKey()))
            .map(Map.Entry::getValue)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .collect(Collectors.joining(", "));
    return values.isEmpty() ? null : values;
  }

  @Override
  public String toString() {
    return "MultiEncoder"
        + encoders.stream().map(PairedEncoder::describe).collect(Collectors.toList());
  }

  /** Collects the encoders of a {@link MultiEncoder}. */
  @Experimental
  public static final class Builder {

    private final List<PredicatedEncoder> encoders = new ArrayList<>();

    private Builder() {}

    /**
     * Adds an encoder that declares its own applicability.
     *
     * @param encoder the encoder, consulted via {@link PredicatedEncoder#canEncode}
     */
    public Builder add(PredicatedEncoder encoder) {
      encoders.add(Objects.requireNonNull(encoder, "encoder cannot be null"));
      return this;
    }

    /**
     * Adds any encoder, guarded by the given predicate. Use this for encoders that do not implement
     * {@link PredicatedEncoder}, including ones you do not control. The predicate is the whole
     * answer: whatever the encoder may declare about itself is replaced, so this can widen an
     * encoder as well as narrow it. Use {@link #narrow(EncoderPredicate, Encoder)} to keep the
     * encoder's own declaration.
     *
     * @param predicate decides whether the encoder handles a request
     * @param encoder the encoder to delegate to
     */
    public Builder add(EncoderPredicate predicate, Encoder encoder) {
      return add(PredicatedEncoder.of(predicate, encoder));
    }

    /**
     * Adds an encoder, narrowed by the given predicate. If the encoder is itself a {@link
     * PredicatedEncoder}, the predicate applies in addition to the encoder's own {@code canEncode}
     * rather than instead of it: both have to accept the request.
     *
     * <pre>
     * MultiEncoder.builder()
     *     .narrow(EncoderPredicate.contentType("application/vnd.acme+json"), new GsonEncoder())
     *     .add(EncoderPredicate.any(), new DefaultEncoder())
     *     .build();
     * </pre>
     *
     * @param predicate narrows what the encoder handles
     * @param encoder the encoder to delegate to
     */
    public Builder narrow(EncoderPredicate predicate, Encoder encoder) {
      return add(PredicatedEncoder.narrowing(predicate, encoder));
    }

    /**
     * Builds the multi-encoder.
     *
     * @throws IllegalStateException if no encoder was added
     */
    public MultiEncoder build() {
      if (encoders.isEmpty()) {
        throw new IllegalStateException("at least one encoder is required");
      }
      return new MultiEncoder(encoders);
    }
  }
}
