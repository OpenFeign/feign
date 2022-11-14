/*
 * Copyright 2012-2022 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import static feign.Util.checkArgument;
import static feign.Util.checkNotNull;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Param.Expander;
import feign.codec.*;
import feign.template.UriUtils;

public class ReflectiveFeign<C> extends Feign {

  private final ParseHandlersByName<C> targetToHandlersByName;
  private final InvocationHandlerFactory factory;
  private final AsyncContextSupplier<C> defaultContextSupplier;

  ReflectiveFeign(ParseHandlersByName<C> targetToHandlersByName, InvocationHandlerFactory factory,
      AsyncContextSupplier<C> defaultContextSupplier) {
    this.targetToHandlersByName = targetToHandlersByName;
    this.factory = factory;
    this.defaultContextSupplier = defaultContextSupplier;
  }

  /**
   * creates an api binding to the {@code target}. As this invokes reflection, care should be taken
   * to cache the result.
   */
  public <T> T newInstance(Target<T> target) {
    return newInstance(target, defaultContextSupplier.newContext());
  }

  @SuppressWarnings("unchecked")
  public <T> T newInstance(Target<T> target, C requestContext) {
    TargetSpecificationVerifier.verify(target);

    Map<Method, MethodHandler> methodToHandler =
        targetToHandlersByName.apply(target, requestContext);
    InvocationHandler handler = factory.create(target, methodToHandler);
    T proxy = (T) Proxy.newProxyInstance(target.type().getClassLoader(),
        new Class<?>[] {target.type()}, handler);

    for (MethodHandler methodHandler : methodToHandler.values()) {
      if (methodHandler instanceof DefaultMethodHandler) {
        ((DefaultMethodHandler) methodHandler).bindTo(proxy);
      }
    }

    return proxy;
  }

  static class FeignInvocationHandler implements InvocationHandler {

    private final Target target;
    private final Map<Method, MethodHandler> dispatch;

    FeignInvocationHandler(Target target, Map<Method, MethodHandler> dispatch) {
      this.target = checkNotNull(target, "target");
      this.dispatch = checkNotNull(dispatch, "dispatch for %s", target);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if ("equals".equals(method.getName())) {
        try {
          Object otherHandler =
              args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
          return equals(otherHandler);
        } catch (IllegalArgumentException e) {
          return false;
        }
      } else if ("hashCode".equals(method.getName())) {
        return hashCode();
      } else if ("toString".equals(method.getName())) {
        return toString();
      }

      return dispatch.get(method).invoke(args);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof FeignInvocationHandler) {
        FeignInvocationHandler other = (FeignInvocationHandler) obj;
        return target.equals(other.target);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return target.hashCode();
    }

    @Override
    public String toString() {
      return target.toString();
    }
  }

  static final class ParseHandlersByName<C> {

    private final Contract contract;
    private final Encoder encoder;
    private final QueryMapEncoder queryMapEncoder;
    private final MethodHandler.Factory<C> factory;

    ParseHandlersByName(
        Contract contract,
        Encoder encoder,
        QueryMapEncoder queryMapEncoder,
        MethodHandler.Factory<C> factory) {
      this.contract = contract;
      this.factory = factory;
      this.queryMapEncoder = queryMapEncoder;
      this.encoder = checkNotNull(encoder, "encoder");
    }

    public Map<Method, MethodHandler> apply(Target target, C requestContext) {
      final Map<Method, MethodHandler> result = new LinkedHashMap<>();

      final List<MethodMetadata> metadataList = contract.parseAndValidateMetadata(target.type());
      for (MethodMetadata md : metadataList) {
        final Method method = md.method();
        if (method.getDeclaringClass() == Object.class) {
          continue;
        }

        final MethodHandler handler = createMethodHandler(target, md, requestContext);
        result.put(method, handler);
      }

      for (Method method : target.type().getMethods()) {
        if (Util.isDefault(method)) {
          final MethodHandler handler = new DefaultMethodHandler(method);
          result.put(method, handler);
        }
      }

      return result;
    }

    private MethodHandler createMethodHandler(final Target<?> target,
                                              final MethodMetadata md,
                                              final C requestContext) {
      if (md.isIgnored()) {
        return args -> {
          throw new IllegalStateException(md.configKey() + " is not a method handled by feign");
        };
      }

      BuildTemplateByResolvingArgs buildTemplate = getBuildTemplate(target, md);
      return factory.create(target, md, buildTemplate, requestContext);
    }

    private BuildTemplateByResolvingArgs getBuildTemplate(Target target, MethodMetadata md) {
      if (!md.formParams().isEmpty() && md.template().bodyTemplate() == null) {
        return new BuildFormEncodedTemplateFromArgs(md, encoder, queryMapEncoder, target);
      } else if (md.bodyIndex() != null || md.alwaysEncodeBody()) {
        return new BuildEncodedTemplateFromArgs(md, encoder, queryMapEncoder, target);
      } else {
        return new BuildTemplateByResolvingArgs(md, queryMapEncoder, target);
      }
    }
  }

  private static class BuildTemplateByResolvingArgs implements RequestTemplate.Factory {

    private final QueryMapEncoder queryMapEncoder;

    protected final MethodMetadata metadata;
    protected final Target<?> target;
    private final Map<Integer, Expander> indexToExpander = new LinkedHashMap<Integer, Expander>();

    private BuildTemplateByResolvingArgs(MethodMetadata metadata, QueryMapEncoder queryMapEncoder,
        Target target) {
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
      for (Entry<Integer, Class<? extends Expander>> indexToExpanderClass : metadata
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
      RequestTemplate mutable = RequestTemplate.from(metadata.template());
      mutable.feignTarget(target);
      if (metadata.urlIndex() != null) {
        int urlIndex = metadata.urlIndex();
        checkArgument(argv[urlIndex] != null, "URI parameter %s was null", urlIndex);
        mutable.target(String.valueOf(argv[urlIndex]));
      }
      Map<String, Object> varBuilder = new LinkedHashMap<String, Object>();
      for (Entry<Integer, Collection<String>> entry : metadata.indexToName().entrySet()) {
        int i = entry.getKey();
        Object value = argv[entry.getKey()];
        if (value != null) { // Null values are skipped.
          if (indexToExpander.containsKey(i)) {
            value = expandElements(indexToExpander.get(i), value);
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

    private Object expandElements(Expander expander, Object value) {
      if (value instanceof Iterable) {
        return expandIterable(expander, (Iterable) value);
      }
      return expander.expand(value);
    }

    private List<String> expandIterable(Expander expander, Iterable value) {
      List<String> values = new ArrayList<String>();
      for (Object element : value) {
        if (element != null) {
          values.add(expander.expand(element));
        }
      }
      return values;
    }

    @SuppressWarnings("unchecked")
    private RequestTemplate addHeaderMapHeaders(Map<String, Object> headerMap,
                                                RequestTemplate mutable) {
      for (Entry<String, Object> currEntry : headerMap.entrySet()) {
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

        mutable.header(currEntry.getKey(), values);
      }
      return mutable;
    }

    @SuppressWarnings("unchecked")
    private RequestTemplate addQueryMapQueryParameters(Map<String, Object> queryMap,
                                                       RequestTemplate mutable) {
      for (Entry<String, Object> currEntry : queryMap.entrySet()) {
        Collection<String> values = new ArrayList<String>();

        Object currValue = currEntry.getValue();
        if (currValue instanceof Iterable<?>) {
          Iterator<?> iter = ((Iterable<?>) currValue).iterator();
          while (iter.hasNext()) {
            Object nextObject = iter.next();
            values.add(nextObject == null ? null : UriUtils.encode(nextObject.toString()));
          }
        } else if (currValue instanceof Object[]) {
          for (Object value : (Object[]) currValue) {
            values.add(value == null ? null : UriUtils.encode(value.toString()));
          }
        } else {
          if (currValue != null) {
            values.add(UriUtils.encode(currValue.toString()));
          }
        }

        if (values.size() > 0) {
          mutable.query(UriUtils.encode(currEntry.getKey()), values);
        }
      }
      return mutable;
    }

    protected RequestTemplate resolve(Object[] argv,
                                      RequestTemplate mutable,
                                      Map<String, Object> variables) {
      return mutable.resolve(variables);
    }
  }

  private static class BuildFormEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

    private final Encoder encoder;

    private BuildFormEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder,
        QueryMapEncoder queryMapEncoder, Target target) {
      super(metadata, queryMapEncoder, target);
      this.encoder = encoder;
    }

    @Override
    protected RequestTemplate resolve(Object[] argv,
                                      RequestTemplate mutable,
                                      Map<String, Object> variables) {
      Map<String, Object> formVariables = new LinkedHashMap<String, Object>();
      for (Entry<String, Object> entry : variables.entrySet()) {
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

  private static class BuildEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

    private final Encoder encoder;

    private BuildEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder,
        QueryMapEncoder queryMapEncoder, Target target) {
      super(metadata, queryMapEncoder, target);
      this.encoder = encoder;
    }

    @Override
    protected RequestTemplate resolve(Object[] argv,
                                      RequestTemplate mutable,
                                      Map<String, Object> variables) {

      boolean alwaysEncodeBody = mutable.methodMetadata().alwaysEncodeBody();

      Object body = null;
      if (!alwaysEncodeBody) {
        body = argv[metadata.bodyIndex()];
        checkArgument(body != null, "Body parameter %s was null", metadata.bodyIndex());
      }

      try {
        if (alwaysEncodeBody) {
          body = argv == null ? new Object[0] : argv;
          encoder.encode(body, Object[].class, mutable);
        } else {
          encoder.encode(body, metadata.bodyType(), mutable);
        }
      } catch (EncodeException e) {
        throw e;
      } catch (RuntimeException e) {
        throw new EncodeException(e.getMessage(), e);
      }
      return super.resolve(argv, mutable, variables);
    }
  }

  private static class TargetSpecificationVerifier {
    public static <T> void verify(Target<T> target) {
      Class<T> type = target.type();
      if (!type.isInterface()) {
        throw new IllegalArgumentException("Type must be an interface: " + type);
      }

      for (final Method m : type.getMethods()) {
        final Class<?> retType = m.getReturnType();

        if (!CompletableFuture.class.isAssignableFrom(retType)) {
          continue; // synchronous case
        }

        if (retType != CompletableFuture.class) {
          throw new IllegalArgumentException("Method return type is not CompleteableFuture: "
              + getFullMethodName(type, retType, m));
        }

        final Type genRetType = m.getGenericReturnType();

        if (!(genRetType instanceof ParameterizedType)) {
          throw new IllegalArgumentException("Method return type is not parameterized: "
              + getFullMethodName(type, genRetType, m));
        }

        if (((ParameterizedType) genRetType).getActualTypeArguments()[0] instanceof WildcardType) {
          throw new IllegalArgumentException(
              "Wildcards are not supported for return-type parameters: "
                  + getFullMethodName(type, genRetType, m));
        }
      }
    }

    private static String getFullMethodName(Class<?> type, Type retType, Method m) {
      return retType.getTypeName() + " " + type.toGenericString() + "." + m.getName();
    }
  }
}
