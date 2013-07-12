/*
 * Copyright 2013 Netflix, Inc.
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
package feign;

import static feign.Util.checkArgument;
import static feign.Util.checkNotNull;
import static java.lang.String.format;

import dagger.Provides;
import feign.MethodHandler.Factory;
import feign.Request.Options;
import feign.codec.BodyEncoder;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.codec.FormEncoder;
import feign.codec.StringDecoder;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Inject;

@SuppressWarnings("rawtypes")
public class ReflectiveFeign extends Feign {

  private final ParseHandlersByName targetToHandlersByName;

  @Inject
  ReflectiveFeign(ParseHandlersByName targetToHandlersByName) {
    this.targetToHandlersByName = targetToHandlersByName;
  }

  /**
   * creates an api binding to the {@code target}. As this invokes reflection, care should be taken
   * to cache the result.
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T newInstance(Target<T> target) {
    Map<String, MethodHandler> nameToHandler = targetToHandlersByName.apply(target);
    Map<Method, MethodHandler> methodToHandler = new LinkedHashMap<Method, MethodHandler>();
    for (Method method : target.type().getDeclaredMethods()) {
      if (method.getDeclaringClass() == Object.class) continue;
      methodToHandler.put(method, nameToHandler.get(Feign.configKey(method)));
    }
    FeignInvocationHandler handler = new FeignInvocationHandler(target, methodToHandler);
    return (T)
        Proxy.newProxyInstance(
            target.type().getClassLoader(), new Class<?>[] {target.type()}, handler);
  }

  static class FeignInvocationHandler implements InvocationHandler {

    private final Target target;
    private final Map<Method, MethodHandler> methodToHandler;

    FeignInvocationHandler(Target target, Map<Method, MethodHandler> methodToHandler) {
      this.target = checkNotNull(target, "target");
      this.methodToHandler = checkNotNull(methodToHandler, "methodToHandler for %s", target);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return methodToHandler.get(method).invoke(args);
    }

    @Override
    public int hashCode() {
      return target.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (FeignInvocationHandler.class != obj.getClass()) return false;
      FeignInvocationHandler that = FeignInvocationHandler.class.cast(obj);
      return this.target.equals(that.target);
    }

    @Override
    public String toString() {
      return "target(" + target + ")";
    }
  }

  @dagger.Module(
      complete = false, // Config
      injects = Feign.class,
      library = true // provides Feign
      )
  public static class Module {

    @Provides
    Feign provideFeign(ReflectiveFeign in) {
      return in;
    }
  }

  private static IllegalStateException noConfig(String configKey, Class<?> type) {
    return new IllegalStateException(
        format("no configuration for %s present for %s!", configKey, type.getSimpleName()));
  }

  static final class ParseHandlersByName {
    private final Contract contract;
    private final Map<String, Options> options;
    private final Map<String, BodyEncoder> bodyEncoders;
    private final Map<String, FormEncoder> formEncoders;
    private final Map<String, Decoder> decoders;
    private final Map<String, ErrorDecoder> errorDecoders;
    private final Factory factory;

    @Inject
    ParseHandlersByName(
        Contract contract,
        Map<String, Options> options,
        Map<String, BodyEncoder> bodyEncoders,
        Map<String, FormEncoder> formEncoders,
        Map<String, Decoder> decoders,
        Map<String, ErrorDecoder> errorDecoders,
        Factory factory) {
      this.contract = contract;
      this.options = options;
      this.bodyEncoders = bodyEncoders;
      this.formEncoders = formEncoders;
      this.decoders = decoders;
      this.factory = factory;
      this.errorDecoders = errorDecoders;
    }

    public Map<String, MethodHandler> apply(Target key) {
      List<MethodMetadata> metadata = contract.parseAndValidatateMetadata(key.type());
      Map<String, MethodHandler> result = new LinkedHashMap<String, MethodHandler>();
      for (MethodMetadata md : metadata) {
        Options options = forMethodOrClass(this.options, md.configKey());
        if (options == null) {
          options = new Options();
        }
        Decoder decoder = forMethodOrClass(decoders, md.configKey());
        if (decoder == null
            && (md.returnType() == void.class || md.returnType() == Response.class)) {
          decoder = new StringDecoder();
        }
        if (decoder == null) {
          throw noConfig(md.configKey(), Decoder.class);
        }
        ErrorDecoder errorDecoder = forMethodOrClass(errorDecoders, md.configKey());
        if (errorDecoder == null) {
          errorDecoder = ErrorDecoder.DEFAULT;
        }
        BuildTemplateByResolvingArgs buildTemplate;
        if (!md.formParams().isEmpty() && md.template().bodyTemplate() == null) {
          FormEncoder formEncoder = forMethodOrClass(formEncoders, md.configKey());
          if (formEncoder == null) {
            throw noConfig(md.configKey(), FormEncoder.class);
          }
          buildTemplate = new BuildFormEncodedTemplateFromArgs(md, formEncoder);
        } else if (md.bodyIndex() != null) {
          BodyEncoder bodyEncoder = forMethodOrClass(bodyEncoders, md.configKey());
          if (bodyEncoder == null) {
            throw noConfig(md.configKey(), BodyEncoder.class);
          }
          buildTemplate = new BuildBodyEncodedTemplateFromArgs(md, bodyEncoder);
        } else {
          buildTemplate = new BuildTemplateByResolvingArgs(md);
        }
        result.put(
            md.configKey(), factory.create(key, md, buildTemplate, options, decoder, errorDecoder));
      }
      return result;
    }
  }

  private static class BuildTemplateByResolvingArgs implements MethodHandler.BuildTemplateFromArgs {
    protected final MethodMetadata metadata;

    private BuildTemplateByResolvingArgs(MethodMetadata metadata) {
      this.metadata = metadata;
    }

    public RequestTemplate apply(Object[] argv) {
      RequestTemplate mutable = new RequestTemplate(metadata.template());
      if (metadata.urlIndex() != null) {
        int urlIndex = metadata.urlIndex();
        checkArgument(argv[urlIndex] != null, "URI parameter %s was null", urlIndex);
        mutable.insert(0, String.valueOf(argv[urlIndex]));
      }
      Map<String, Object> varBuilder = new LinkedHashMap<String, Object>();
      for (Entry<Integer, Collection<String>> entry : metadata.indexToName().entrySet()) {
        Object value = argv[entry.getKey()];
        if (value != null) { // Null values are skipped.
          for (String name : entry.getValue()) varBuilder.put(name, value);
        }
      }
      return resolve(argv, mutable, varBuilder);
    }

    protected RequestTemplate resolve(
        Object[] argv, RequestTemplate mutable, Map<String, Object> variables) {
      return mutable.resolve(variables);
    }
  }

  private static class BuildFormEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {
    private final FormEncoder formEncoder;

    private BuildFormEncodedTemplateFromArgs(MethodMetadata metadata, FormEncoder formEncoder) {
      super(metadata);
      this.formEncoder = formEncoder;
    }

    @Override
    protected RequestTemplate resolve(
        Object[] argv, RequestTemplate mutable, Map<String, Object> variables) {
      Map<String, Object> formVariables = new LinkedHashMap<String, Object>();
      for (Entry<String, Object> entry : variables.entrySet()) {
        if (metadata.formParams().contains(entry.getKey()))
          formVariables.put(entry.getKey(), entry.getValue());
      }
      formEncoder.encodeForm(formVariables, mutable);
      return super.resolve(argv, mutable, variables);
    }
  }

  private static class BuildBodyEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {
    private final BodyEncoder bodyEncoder;

    private BuildBodyEncodedTemplateFromArgs(MethodMetadata metadata, BodyEncoder bodyEncoder) {
      super(metadata);
      this.bodyEncoder = bodyEncoder;
    }

    @Override
    protected RequestTemplate resolve(
        Object[] argv, RequestTemplate mutable, Map<String, Object> variables) {
      Object body = argv[metadata.bodyIndex()];
      checkArgument(body != null, "Body parameter %s was null", metadata.bodyIndex());
      bodyEncoder.encodeBody(body, mutable);
      return super.resolve(argv, mutable, variables);
    }
  }

  static <T> T forMethodOrClass(Map<String, T> config, String configKey) {
    if (config.containsKey(configKey)) {
      return config.get(configKey);
    }
    String classKey = toClassKey(configKey);
    if (config.containsKey(classKey)) {
      return config.get(classKey);
    }
    return null;
  }

  public static String toClassKey(String methodKey) {
    return methodKey.substring(0, methodKey.indexOf('#'));
  }
}
