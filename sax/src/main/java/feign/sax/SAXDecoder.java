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

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;

import static feign.Util.checkNotNull;
import static feign.Util.checkState;
import static feign.Util.ensureClosed;
import static feign.Util.resolveLastTypeParameter;

/**
 * Decodes responses using SAX, which is supported both in normal JVM environments, as well Android.
 * <br> <h4>Basic example with with Feign.Builder</h4> <br>
 * <pre>
 * api = Feign.builder()
 *            .decoder(SAXDecoder.builder()
 *                               .registerContentHandler(ContentHandlerForFoo.class)
 *                               .registerContentHandler(ContentHandlerForBar.class)
 *                               .build())
 *            .target(MyApi.class, "http://api");
 * </pre>
 */
public class SAXDecoder implements Decoder {

  private final Map<Type, ContentHandlerWithResult.Factory<?>> handlerFactories;

  private SAXDecoder(Map<Type, ContentHandlerWithResult.Factory<?>> handlerFactories) {
    this.handlerFactories = handlerFactories;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public Object decode(Response response, Type type) throws IOException, DecodeException {
    if (response.status() == 404) return Util.emptyValueOf(type);
    if (response.body() == null) return null;
    ContentHandlerWithResult.Factory<?> handlerFactory = handlerFactories.get(type);
    checkState(handlerFactory != null, "type %s not in configured handlers %s", type,
               handlerFactories.keySet());
    ContentHandlerWithResult<?> handler = handlerFactory.create();
    try {
      XMLReader xmlReader = XMLReaderFactory.createXMLReader();
      xmlReader.setFeature("http://xml.org/sax/features/namespaces", false);
      xmlReader.setFeature("http://xml.org/sax/features/validation", false);
      /* Explicitly control sax configuration to prevent XXE attacks */
      xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
      xmlReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      xmlReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
      xmlReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      xmlReader.setContentHandler(handler);
      InputStream inputStream = response.body().asInputStream();
      try {
        xmlReader.parse(new InputSource(inputStream));
      } finally {
        ensureClosed(inputStream);
      }
      return handler.result();
    } catch (SAXException e) {
      throw new DecodeException(e.getMessage(), e);
    }
  }

  /**
   * Implementations are not intended to be shared across requests.
   */
  public interface ContentHandlerWithResult<T> extends ContentHandler {

    /**
     * expected to be set following a call to {@link XMLReader#parse(InputSource)}
     */
    T result();

    public interface Factory<T> {

      ContentHandlerWithResult<T> create();
    }
  }

  public static class Builder {

    private final Map<Type, ContentHandlerWithResult.Factory<?>> handlerFactories =
        new LinkedHashMap<Type, ContentHandlerWithResult.Factory<?>>();

    /**
     * Will call {@link Constructor#newInstance(Object...)} on {@code handlerClass} for each content
     * stream. <p/> <h3>Note</h3> <br/> While this is costly vs {@code new}, it may not affect real
     * performance due to the high cost of reading streams.
     *
     * @throws IllegalArgumentException if there's no no-arg constructor on {@code handlerClass}.
     */
    public <T extends ContentHandlerWithResult<?>> Builder registerContentHandler(
        Class<T> handlerClass) {
      Type
          type =
          resolveLastTypeParameter(checkNotNull(handlerClass, "handlerClass"),
                                   ContentHandlerWithResult.class);
      return registerContentHandler(type,
                                    new NewInstanceContentHandlerWithResultFactory(handlerClass));
    }

    /**
     * Will call {@link ContentHandlerWithResult.Factory#create()} on {@code handler} for each
     * content stream. The {@code handler} is expected to have a generic parameter of {@code type}.
     */
    public Builder registerContentHandler(Type type, ContentHandlerWithResult.Factory<?> handler) {
      this.handlerFactories.put(checkNotNull(type, "type"), checkNotNull(handler, "handler"));
      return this;
    }

    public SAXDecoder build() {
      return new SAXDecoder(handlerFactories);
    }

    private static class NewInstanceContentHandlerWithResultFactory<T>
        implements ContentHandlerWithResult.Factory<T> {

      private final Constructor<ContentHandlerWithResult<T>> ctor;

      private NewInstanceContentHandlerWithResultFactory(Class<ContentHandlerWithResult<T>> clazz) {
        try {
          this.ctor = clazz.getDeclaredConstructor();
          // allow private or package protected ctors
          ctor.setAccessible(true);
        } catch (NoSuchMethodException e) {
          throw new IllegalArgumentException("ensure " + clazz + " has a no-args constructor", e);
        }
      }

      @Override
      public ContentHandlerWithResult<T> create() {
        try {
          return ctor.newInstance();
        } catch (Exception e) {
          throw new IllegalArgumentException("exception attempting to instantiate " + ctor, e);
        }
      }
    }
  }
}
