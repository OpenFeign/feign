package feign;

import static feign.Util.checkNotNull;

import feign.codec.EncodeException;
import feign.codec.Encoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copy of private {@code ReflectiveFeign.BuildTemplateByResolvingArgs}.
 */
class VertxBuildTemplateByResolvingArgs implements RequestTemplate.Factory {
  private final QueryMapEncoder queryMapEncoder;
  final MethodMetadata metadata;
  final Target<?> target;
  private final Map<Integer, Param.Expander> indexToExpander = new HashMap<>();

  VertxBuildTemplateByResolvingArgs(
      final MethodMetadata metadata,
      final QueryMapEncoder queryMapEncoder,
      final Target target) {
    this.metadata = metadata;
    this.target = target;
    this.queryMapEncoder = queryMapEncoder;

    if (metadata.indexToExpander() != null) {
      indexToExpander.putAll(metadata.indexToExpander());
      return;
    }

    if (metadata.indexToExpanderClass().isEmpty()) {
      return;
    }

    for (final Map.Entry<Integer, Class<? extends Param.Expander>> indexToExpanderClass : metadata
        .indexToExpanderClass().entrySet()) {
      try {
        indexToExpander.put(
            indexToExpanderClass.getKey(), indexToExpanderClass.getValue().newInstance());
      } catch (final InstantiationException | IllegalAccessException exception) {
        throw new IllegalStateException(exception);
      }
    }
  }

  @Override
  public RequestTemplate create(final Object[] argv) {
    final RequestTemplate mutable = new RequestTemplate(metadata.template());

    if (metadata.urlIndex() != null) {
      final int urlIndex = metadata.urlIndex();
      checkNotNull(argv[urlIndex], "URI parameter %s was null", urlIndex);
      mutable.insert(0, String.valueOf(argv[urlIndex]));
    }

    final Map<String, Object> varBuilder = new HashMap<>();

    for (final Map.Entry<Integer, Collection<String>> entry : metadata.indexToName().entrySet()) {
      final int i = entry.getKey();
      Object value = argv[entry.getKey()];

      if (value != null) { // Null values are skipped.
        if (indexToExpander.containsKey(i)) {
          value = expandElements(indexToExpander.get(i), value);
        }

        for (final String name : entry.getValue()) {
          varBuilder.put(name, value);
        }
      }
    }

    RequestTemplate template = resolve(argv, mutable, varBuilder);

    if (metadata.queryMapIndex() != null) {
      // add query map parameters after initial resolve so that they take
      // precedence over any predefined values
      Object value = argv[metadata.queryMapIndex()];
      Map<String, Object> queryMap = toQueryMap(value);
      template = addQueryMapQueryParameters(queryMap, template);
    }

    if (metadata.headerMapIndex() != null) {
      // add header map parameters for a resolution of the user pojo object
      Object value = argv[metadata.headerMapIndex()];
      Map<String, Object> headerMap = toQueryMap(value);
      template = addHeaderMapHeaders(headerMap, template);
    }

    return template;
  }

  private Map<String, Object> toQueryMap(Object value) {
    if (value instanceof Map) {
      return (Map<String, Object>) value;
    }
    try {
      return queryMapEncoder.encode(value);
    } catch (EncodeException e) {
      throw new IllegalStateException(e);
    }
  }

  private Object expandElements(Param.Expander expander, Object value) {
    if (value instanceof Iterable) {
      return expandIterable(expander, (Iterable) value);
    }
    return expander.expand(value);
  }

  private List<String> expandIterable(Param.Expander expander, Iterable value) {
    List<String> values = new ArrayList<>();
    for (Object element : value) {
      if (element != null) {
        values.add(expander.expand(element));
      }
    }
    return values;
  }

  @SuppressWarnings("unchecked")
  private RequestTemplate addHeaderMapHeaders(
      final Map<String, Object> headerMap,
      final RequestTemplate mutableRequestTemplate) {
    for (final Map.Entry<String, Object> currEntry : headerMap.entrySet()) {
      final Object currValue = currEntry.getValue();
      final Collection<String> values = new ArrayList<>();

      if (currValue instanceof Iterable<?>) {
        for (final Object valueObject : (Iterable<?>) currValue) {
          values.add(valueObject == null ? null : valueObject.toString());
        }
      } else {
        values.add(currValue == null ? null : currValue.toString());
      }

      mutableRequestTemplate.header(currEntry.getKey(), values);
    }

    return mutableRequestTemplate;
  }

  @SuppressWarnings("unchecked")
  private RequestTemplate addQueryMapQueryParameters(
      final Map<String, Object> queryMap,
      final RequestTemplate mutableRequestTemplate) {
    for (final Map.Entry<String, Object> currEntry : queryMap.entrySet()) {
      final Object currValue = currEntry.getValue();
      final Collection<String> values = new ArrayList<>();

      if (currValue instanceof Iterable<?>) {
        for (final Object valueObject : (Iterable<?>) currValue) {
          values.add(valueObject == null ? null : valueObject.toString());
        }
      } else {
        values.add(currValue == null ? null : currValue.toString());
      }

      mutableRequestTemplate.query(currEntry.getKey(), values);
    }

    return mutableRequestTemplate;
  }

  protected RequestTemplate resolve(
      final Object[] argv,
      final RequestTemplate mutable,
      final Map<String, Object> variables) {
    return mutable.resolve(variables);
  }

  /**
   * Public copy of {@code ReflectiveFeign.BuildFormEncodedTemplateFromArgs}.
   */
  static final class BuildFormEncodedTemplateFromArgs extends VertxBuildTemplateByResolvingArgs {
    private final Encoder encoder;

    BuildFormEncodedTemplateFromArgs(
        final MethodMetadata metadata,
        final QueryMapEncoder queryMapEncoder,
        final Target target,
        final Encoder encoder) {
      super(metadata, queryMapEncoder, target);
      this.encoder = encoder;
    }

    @Override
    protected RequestTemplate resolve(
        final Object[] argv,
        final RequestTemplate mutable,
        final Map<String, Object> variables) {
      final Map<String, Object> formVariables = new HashMap<>();

      for (final Map.Entry<String, Object> entry : variables.entrySet()) {
        if (metadata.formParams().contains(entry.getKey())) {
          formVariables.put(entry.getKey(), entry.getValue());
        }
      }

      try {
        encoder.encode(formVariables, Encoder.MAP_STRING_WILDCARD, mutable);
      } catch (final EncodeException encodeException) {
        throw encodeException;
      } catch (final RuntimeException unexpectedException) {
        throw new EncodeException(unexpectedException.getMessage(), unexpectedException);
      }

      return super.resolve(argv, mutable, variables);
    }
  }

  /**
   * Public copy of {@code ReflectiveFeign.BuildEncodedTemplateFromArgs}.
   */
  static final class BuildEncodedTemplateFromArgs extends VertxBuildTemplateByResolvingArgs {
    private final Encoder encoder;

    BuildEncodedTemplateFromArgs(
        final MethodMetadata metadata,
        final QueryMapEncoder queryMapEncoder,
        final Target target,
        final Encoder encoder) {
      super(metadata, queryMapEncoder, target);
      this.encoder = encoder;
    }

    @Override
    protected RequestTemplate resolve(
        final Object[] argv,
        final RequestTemplate mutable,
        final Map<String, Object> variables) {
      final Object body = argv[metadata.bodyIndex()];
      checkNotNull(body, "Body parameter %s was null", metadata.bodyIndex());

      try {
        encoder.encode(body, metadata.bodyType(), mutable);
      } catch (final EncodeException encodeException) {
        throw encodeException;
      } catch (final RuntimeException unexpectedException) {
        throw new EncodeException(unexpectedException.getMessage(), unexpectedException);
      }

      return super.resolve(argv, mutable, variables);
    }
  }
}
