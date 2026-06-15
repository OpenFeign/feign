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
package feign.form;

import feign.RequestTemplate;
import feign.Util;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.core.codec.DefaultEncoder;
import feign.form.multipart.ArrayPartResolver;
import feign.form.multipart.ByteArrayPartEncoder;
import feign.form.multipart.DefaultPartEncoder;
import feign.form.multipart.DelegatingPartEncoder;
import feign.form.multipart.FilePartEncoder;
import feign.form.multipart.FormDataPartResolver;
import feign.form.multipart.InputStreamPartEncoder;
import feign.form.multipart.IterablePartResolver;
import feign.form.multipart.LeafPartResolver;
import feign.form.multipart.MultipartFormBody;
import feign.form.multipart.PartEncoder;
import feign.form.multipart.PartMetadata;
import feign.form.multipart.PartResolver;
import feign.form.multipart.PartResolverChain;
import feign.form.multipart.PathPartEncoder;
import feign.form.util.PojoUtil;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** An {@link Encoder} that encodes a request body as {@code multipart/form-data}. */
@RequiredArgsConstructor
public class MultipartFormEncoder implements Encoder {
  @NonNull private final Encoder delegate;

  @NonNull private final PartResolverChain partResolverChain;

  /**
   * Creates a new {@link MultipartFormEncoder} with a {@link DefaultEncoder} delegate and a default
   * {@link PartResolverChain} that supports {@link FormData}, arrays, iterables, and scalar parts.
   */
  public MultipartFormEncoder() {
    this((Encoder) null);
  }

  /**
   * Creates a new {@link MultipartFormEncoder} with the given delegate {@link Encoder} and a
   * default {@link PartResolverChain} that supports {@link FormData}, arrays, iterables, and scalar
   * parts.
   *
   * @param delegate the delegate {@link Encoder} to use for encoding non-multipart request bodies.
   *     If {@code null}, a default {@link DefaultEncoder} will be used.
   */
  public MultipartFormEncoder(Encoder delegate) {
    this(delegate, null, null, null);
  }

  /**
   * Creates a new {@link MultipartFormEncoder} with the given {@link PartResolverChain} and a
   * default {@link DefaultEncoder} delegate.
   *
   * @param partResolverChain the {@link PartResolverChain} to use for resolving multipart parts. If
   *     {@code null}, a default {@link PartResolverChain} that supports {@link FormData}, arrays,
   *     iterables, and scalar parts will be used.
   */
  public MultipartFormEncoder(PartResolverChain partResolverChain) {
    this(defaultDelegate(), partResolverChain);
  }

  @Builder
  private MultipartFormEncoder(
      Encoder delegate,
      Consumer<List<PartResolver>> partResolvers,
      Consumer<List<PartEncoder>> partEncoders,
      Collection<Encoder> partBodyEncoders) {
    this(
        Objects.requireNonNullElseGet(delegate, MultipartFormEncoder::defaultDelegate),
        buildPartResolverOrchestrator(
            partResolvers,
            partEncoders,
            Objects.requireNonNullElseGet(partBodyEncoders, Collections::emptyList)));
  }

  private static Encoder defaultDelegate() {
    return new DefaultEncoder();
  }

  private static PartResolverChain buildPartResolverOrchestrator(
      Consumer<List<PartResolver>> partResolversCustomizer,
      Consumer<List<PartEncoder>> partEncodersCustomizer,
      Collection<Encoder> partBodyEncoders) {
    var pathPartEncoder = new PathPartEncoder();
    var partEncoders = new LinkedList<PartEncoder>();

    partEncoders.add(new ByteArrayPartEncoder());
    partEncoders.add(new FilePartEncoder(pathPartEncoder));
    partEncoders.add(pathPartEncoder);
    partEncoders.add(new InputStreamPartEncoder());
    partEncoders.add(new DelegatingPartEncoder(partBodyEncoders));
    partEncoders.add(new DefaultPartEncoder());

    if (partEncodersCustomizer != null) {
      partEncodersCustomizer.accept(partEncoders);
    }

    var partResolvers = new LinkedList<PartResolver>();

    partResolvers.add(new FormDataPartResolver());
    partResolvers.add(new ArrayPartResolver());
    partResolvers.add(new IterablePartResolver());
    partResolvers.add(new LeafPartResolver(partEncoders));

    if (partResolversCustomizer != null) {
      partResolversCustomizer.accept(partResolvers);
    }

    return new PartResolverChain(partResolvers);
  }

  /**
   * Encodes the given {@code object} as a multipart form body if the request template indicates a
   * {@code multipart/form-data} content type. Otherwise, delegates to the provided {@link Encoder}.
   *
   * @param object {@inheritDoc}
   * @param bodyType {@inheritDoc}
   * @param template {@inheritDoc}
   * @throws EncodeException {@inheritDoc}
   */
  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    if (!isMultipart(template)) {
      delegate.encode(object, bodyType, template);
      return;
    }

    var formData = getFormData(object, bodyType);

    if (formData.isEmpty()) {
      delegate.encode(object, bodyType, template);
      return;
    }

    var multipartFormBody =
        formData.entrySet().stream()
            .flatMap(
                entry ->
                    partResolverChain.resolve(new PartMetadata(entry.getKey(), entry.getValue())))
            .collect(Collectors.collectingAndThen(Collectors.toList(), MultipartFormBody::new));

    template.header(Util.CONTENT_TYPE, Collections.emptyList()); // reset header
    template.header(
        Util.CONTENT_TYPE, "multipart/form-data; boundary=" + multipartFormBody.boundary());
    template.body(multipartFormBody);
  }

  /**
   * {@inheritDoc}
   *
   * @param contentType {@inheritDoc}
   * @return {@code true} if the given {@code contentType} is not {@code null} and starts with
   *     {@code multipart/form-data} (case-insensitive), {@code false} otherwise.
   */
  @Override
  public boolean supports(String contentType) {
    return contentType != null
        && contentType.trim().toLowerCase().startsWith("multipart/form-data");
  }

  private boolean isMultipart(RequestTemplate template) {
    return template.headers().getOrDefault(Util.CONTENT_TYPE, Collections.emptyList()).stream()
        .anyMatch(this::supports);
  }

  private Map<String, Object> getFormData(Object object, Type bodyType) {
    if (object instanceof Map) {
      @SuppressWarnings("unchecked")
      var formData = (Map<String, Object>) object;

      return formData;
    }

    return PojoUtil.isUserPojo(bodyType) ? PojoUtil.toMap(object) : Collections.emptyMap();
  }
}
