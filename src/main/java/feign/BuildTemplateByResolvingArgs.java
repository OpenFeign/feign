package feign;

import static feign.Util.checkNotNull;
import static feign.Util.checkState;

import feign.codec.EncodeException;
import feign.codec.Encoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Copy of private {@code ReflectiveFeign.BuildTemplateByResolvingArgs}.
 */
class BuildTemplateByResolvingArgs implements RequestTemplate.Factory {
  final MethodMetadata metadata;
  private final Map<Integer, Param.Expander> indexToExpander = new HashMap<>();

  BuildTemplateByResolvingArgs(final MethodMetadata metadata) {
    this.metadata = metadata;

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
          value = indexToExpander.get(i).expand(value);
        }

        for (final String name : entry.getValue()) {
          varBuilder.put(name, value);
        }
      }
    }

    RequestTemplate template = resolve(argv, mutable, varBuilder);

    if (metadata.queryMapIndex() != null) {
      /* Add query map parameters after initial resolve so that they take precedence over any
         predefined values */
      template = addQueryMapQueryParameters(argv, template);
    }

    if (metadata.headerMapIndex() != null) {
      template = addHeaderMapHeaders(argv, template);
    }

    return template;
  }

  @SuppressWarnings("unchecked")
  private RequestTemplate addHeaderMapHeaders(
      final Object[] argv,
      final RequestTemplate mutableRequestTemplate) {
    final Map<Object, Object> headerMap = (Map<Object, Object>) argv[metadata.headerMapIndex()];

    for (final Map.Entry<Object, Object> currEntry : headerMap.entrySet()) {
      checkState(currEntry.getKey().getClass() == String.class,
          "HeaderMap key must be a String: %s", currEntry.getKey());

      final Object currValue = currEntry.getValue();
      final Collection<String> values = new ArrayList<>();

      if (currValue instanceof Iterable<?>) {
        for (final Object valueObject : (Iterable<?>) currValue) {
          values.add(valueObject == null ? null : valueObject.toString());
        }
      } else {
        values.add(currValue == null ? null : currValue.toString());
      }

      mutableRequestTemplate.header((String) currEntry.getKey(), values);
    }

    return mutableRequestTemplate;
  }

  @SuppressWarnings("unchecked")
  private RequestTemplate addQueryMapQueryParameters(
      final Object[] argv,
      final RequestTemplate mutableRequestTemplate) {
    final Map<Object, Object> queryMap = (Map<Object, Object>) argv[metadata.queryMapIndex()];

    for (final Map.Entry<Object, Object> currEntry : queryMap.entrySet()) {
      checkState(currEntry.getKey().getClass() == String.class,
          "QueryMap key must be a String: %s", currEntry.getKey());

      final Object currValue = currEntry.getValue();
      final Collection<String> values = new ArrayList<>();

      if (currValue instanceof Iterable<?>) {
        for (final Object valueObject : (Iterable<?>) currValue) {
          values.add(valueObject == null ? null : valueObject.toString());
        }
      } else {
        values.add(currValue == null ? null : currValue.toString());
      }

      mutableRequestTemplate.query((String) currEntry.getKey(), values);
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
  static final class BuildFormEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {
    private final Encoder encoder;

    BuildFormEncodedTemplateFromArgs(final MethodMetadata metadata, final Encoder encoder) {
      super(metadata);
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
  static final class BuildEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {
    private final Encoder encoder;

    BuildEncodedTemplateFromArgs(final MethodMetadata metadata, final Encoder encoder) {
      super(metadata);
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
