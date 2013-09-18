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
package feign.sax;

import static feign.Util.checkNotNull;
import static feign.Util.checkState;
import static feign.Util.ensureClosed;
import static feign.Util.resolveLastTypeParameter;

import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Provider;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Decodes responses using SAX, which is supported both in normal JVM environments, as well Android.
 * <br>
 *
 * <h4>Basic example with with Feign.Builder</h4>
 *
 * <br>
 *
 * <pre>
 * api = Feign.builder()
 *            .decoder(SAXDecoder.builder()
 *                               .registerContentHandler(ContentHandlerForFoo.class)
 *                               .registerContentHandler(ContentHandlerForBar.class)
 *                               .build())
 *            .target(MyApi.class, "http://api");
 * </pre>
 *
 * <p>
 *
 * <h4>Advanced example with Dagger</h4>
 *
 * <br>
 *
 * <pre>
 * &#064;Provides
 * Decoder saxDecoder(Provider&lt;ContentHandlerForFoo&gt; foo, //
 *         Provider&lt;ContentHandlerForBar&gt; bar) {
 *     return SAXDecoder.builder() //
 *             .registerContentHandler(Foo.class, foo) //
 *             .registerContentHandler(Bar.class, bar) //
 *             .build();
 * }
 * </pre>
 */
public class SAXDecoder implements Decoder {

  public static Builder builder() {
    return new Builder();
  }

  // builder as dagger doesn't support wildcard bindings, map bindings, or set bindings of
  // providers.
  public static class Builder {
    private final Map<Type, Provider<? extends ContentHandlerWithResult<?>>> handlerProviders =
        new LinkedHashMap<Type, Provider<? extends ContentHandlerWithResult<?>>>();

    /**
     * Will call {@link Constructor#newInstance(Object...)} on {@code handlerClass} for each content
     * stream.
     *
     * <p>
     *
     * <h3>Note</h3>
     *
     * <br>
     * While this is costly vs {@code new}, it may not affect real performance due to the high cost
     * of reading streams.
     *
     * @throws IllegalArgumentException if there's no no-arg constructor on {@code handlerClass}.
     */
    public <T extends ContentHandlerWithResult<?>> Builder registerContentHandler(
        Class<T> handlerClass) {
      Type type =
          resolveLastTypeParameter(
              checkNotNull(handlerClass, "handlerClass"), ContentHandlerWithResult.class);
      return registerContentHandler(type, new NewInstanceProvider(handlerClass));
    }

    private static class NewInstanceProvider<T extends ContentHandlerWithResult<?>>
        implements Provider<T> {
      private final Constructor<T> ctor;

      private NewInstanceProvider(Class<T> clazz) {
        try {
          this.ctor = clazz.getDeclaredConstructor();
          // allow private or package protected ctors
          ctor.setAccessible(true);
        } catch (NoSuchMethodException e) {
          throw new IllegalArgumentException("ensure " + clazz + " has a no-args constructor", e);
        }
      }

      @Override
      public T get() {
        try {
          return ctor.newInstance();
        } catch (Exception e) {
          throw new IllegalArgumentException("exception attempting to instantiate " + ctor, e);
        }
      }
    }

    /**
     * Will call {@link Provider#get()} on {@code handler} for each content stream. The {@code
     * handler} is expected to have a generic parameter of {@code type}.
     */
    public Builder registerContentHandler(
        Type type, Provider<? extends ContentHandlerWithResult<?>> handler) {
      this.handlerProviders.put(checkNotNull(type, "type"), checkNotNull(handler, "handler"));
      return this;
    }

    public SAXDecoder build() {
      return new SAXDecoder(handlerProviders);
    }
  }

  /** Implementations are not intended to be shared across requests. */
  public interface ContentHandlerWithResult<T> extends ContentHandler {
    /** expected to be set following a call to {@link XMLReader#parse(InputSource)} */
    T result();
  }

  private final Map<Type, Provider<? extends ContentHandlerWithResult<?>>> handlerProviders;

  private SAXDecoder(Map<Type, Provider<? extends ContentHandlerWithResult<?>>> handlerProviders) {
    this.handlerProviders = handlerProviders;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException, DecodeException {
    if (response.body() == null) {
      return null;
    }
    Provider<? extends ContentHandlerWithResult<?>> handlerProvider = handlerProviders.get(type);
    checkState(
        handlerProvider != null,
        "type %s not in configured handlers %s",
        type,
        handlerProviders.keySet());
    ContentHandlerWithResult<?> handler = handlerProvider.get();
    try {
      XMLReader xmlReader = XMLReaderFactory.createXMLReader();
      xmlReader.setFeature("http://xml.org/sax/features/namespaces", false);
      xmlReader.setFeature("http://xml.org/sax/features/validation", false);
      xmlReader.setContentHandler(handler);
      Reader reader = response.body().asReader();
      try {
        xmlReader.parse(new InputSource(reader));
      } finally {
        ensureClosed(reader);
      }
      return handler.result();
    } catch (SAXException e) {
      throw new DecodeException(e.getMessage(), e);
    }
  }
}
