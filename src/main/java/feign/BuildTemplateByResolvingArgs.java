package feign;

import static feign.Util.checkArgument;
import static feign.Util.checkState;

import feign.codec.EncodeException;
import feign.codec.Encoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copy of private {@code ReflectiveFeign.BuildTemplateByResolvingArgs}.
 */
public class BuildTemplateByResolvingArgs implements RequestTemplate.Factory {
  protected final MethodMetadata metadata;
  private final Map<Integer, Param.Expander> indexToExpander = new LinkedHashMap<Integer, Param.Expander>();

  public BuildTemplateByResolvingArgs(MethodMetadata metadata) {
    this.metadata = metadata;
    if (metadata.indexToExpander() != null) {
      indexToExpander.putAll(metadata.indexToExpander());
      return;
    }
    if (metadata.indexToExpanderClass().isEmpty()) {
      return;
    }
    for (Map.Entry<Integer, Class<? extends Param.Expander>> indexToExpanderClass : metadata
        .indexToExpanderClass().entrySet()) {
      try {
        indexToExpander
            .put(indexToExpanderClass.getKey(), indexToExpanderClass.getValue().newInstance());
      } catch (InstantiationException e) {
        throw new IllegalStateException(e);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @Override
  public RequestTemplate create(Object[] argv) {
    RequestTemplate mutable = new RequestTemplate(metadata.template());
    if (metadata.urlIndex() != null) {
      int urlIndex = metadata.urlIndex();
      checkArgument(argv[urlIndex] != null, "URI parameter %s was null", urlIndex);
      mutable.insert(0, String.valueOf(argv[urlIndex]));
    }
    Map<String, Object> varBuilder = new LinkedHashMap<String, Object>();
    for (Map.Entry<Integer, Collection<String>> entry : metadata.indexToName().entrySet()) {
      int i = entry.getKey();
      Object value = argv[entry.getKey()];
      if (value != null) { // Null values are skipped.
        if (indexToExpander.containsKey(i)) {
          value = indexToExpander.get(i).expand(value);
        }
        for (String name : entry.getValue()) {
          varBuilder.put(name, value);
        }
      }
    }

    RequestTemplate template = resolve(argv, mutable, varBuilder);
    if (metadata.queryMapIndex() != null) {
      // add query map parameters after initial resolve so that they take
      // precedence over any predefined values
      template = addQueryMapQueryParameters(argv, template);
    }

    if (metadata.headerMapIndex() != null) {
      template = addHeaderMapHeaders(argv, template);
    }

    return template;
  }

  @SuppressWarnings("unchecked")
  private RequestTemplate addHeaderMapHeaders(Object[] argv, RequestTemplate mutable) {
    Map<Object, Object> headerMap = (Map<Object, Object>) argv[metadata.headerMapIndex()];
    for (Map.Entry<Object, Object> currEntry : headerMap.entrySet()) {
      checkState(currEntry.getKey().getClass() == String.class, "HeaderMap key must be a String: %s", currEntry.getKey());

      Collection<String> values = new ArrayList<String>();

      Object currValue = currEntry.getValue();
      if (currValue instanceof Iterable<?>) {
        Iterator<?> iter = ((Iterable<?>) currValue).iterator();
        while (iter.hasNext()) {
          Object nextObject = iter.next();
          values.add(nextObject == null ? null : nextObject.toString());
        }
      } else {
        values.add(currValue == null ? null : currValue.toString());
      }

      mutable.header((String) currEntry.getKey(), values);
    }
    return mutable;
  }

  @SuppressWarnings("unchecked")
  private RequestTemplate addQueryMapQueryParameters(Object[] argv, RequestTemplate mutable) {
    Map<Object, Object> queryMap = (Map<Object, Object>) argv[metadata.queryMapIndex()];
    for (Map.Entry<Object, Object> currEntry : queryMap.entrySet()) {
      checkState(currEntry.getKey().getClass() == String.class, "QueryMap key must be a String: %s", currEntry.getKey());

      Collection<String> values = new ArrayList<String>();

      Object currValue = currEntry.getValue();
      if (currValue instanceof Iterable<?>) {
        Iterator<?> iter = ((Iterable<?>) currValue).iterator();
        while (iter.hasNext()) {
          Object nextObject = iter.next();
          values.add(nextObject == null ? null : nextObject.toString());
        }
      } else {
        values.add(currValue == null ? null : currValue.toString());
      }

      mutable.query((String) currEntry.getKey(), values);
    }
    return mutable;
  }

  protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable,
                                    Map<String, Object> variables) {
    return mutable.resolve(variables);
  }

  /**
   * Public copy of {@code ReflectiveFeign.BuildFormEncodedTemplateFromArgs}.
   */
  public static class BuildFormEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

    private final Encoder encoder;

    public BuildFormEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder) {
      super(metadata);
      this.encoder = encoder;
    }

    @Override
    protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable,
                                      Map<String, Object> variables) {
      Map<String, Object> formVariables = new LinkedHashMap<String, Object>();
      for (Map.Entry<String, Object> entry : variables.entrySet()) {
        if (metadata.formParams().contains(entry.getKey())) {
          formVariables.put(entry.getKey(), entry.getValue());
        }
      }
      try {
        encoder.encode(formVariables, Encoder.MAP_STRING_WILDCARD, mutable);
      } catch (EncodeException e) {
        throw e;
      } catch (RuntimeException e) {
        throw new EncodeException(e.getMessage(), e);
      }
      return super.resolve(argv, mutable, variables);
    }
  }

  /**
   * Public copy of {@code ReflectiveFeign.BuildEncodedTemplateFromArgs}.
   */
  public static class BuildEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

    private final Encoder encoder;

    public BuildEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder) {
      super(metadata);
      this.encoder = encoder;
    }

    @Override
    protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable,
                                      Map<String, Object> variables) {
      Object body = argv[metadata.bodyIndex()];
      checkArgument(body != null, "Body parameter %s was null", metadata.bodyIndex());
      try {
        encoder.encode(body, metadata.bodyType(), mutable);
      } catch (EncodeException e) {
        throw e;
      } catch (RuntimeException e) {
        throw new EncodeException(e.getMessage(), e);
      }
      return super.resolve(argv, mutable, variables);
    }
  }
}
