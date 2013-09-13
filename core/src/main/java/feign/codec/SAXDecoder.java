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
package feign.codec;

import feign.Response;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.inject.Provider;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import static feign.Util.checkNotNull;
import static feign.Util.checkState;
import static feign.Util.ensureClosed;
import static feign.Util.resolveLastTypeParameter;

/**
 * Decodes responses using SAX. Configure using the {@link SAXDecoder.Builder
 * builder}.
 * <p/>
 * 
 * <pre>
 * &#064;Provides
 * Decoder saxDecoder(Provider&lt;ContentHandlerForFoo&gt; foo, //
 *         Provider&lt;ContentHandlerForBar&gt; bar) {
 *     return SAXDecoder.builder() //
 *             .addContentHandler(foo) //
 *             .addContentHandler(bar) //
 *             .build();
 * }
 * </pre>
 */
public class SAXDecoder implements Decoder {

  public static Builder builder() {
    return new Builder();
  }

  // builder as dagger doesn't support wildcard bindings, map bindings, or set bindings of providers.
  public static class Builder {
    private final Map<Type, Provider<? extends ContentHandlerWithResult<?>>> handlerProviders =
        new LinkedHashMap<Type, Provider<? extends ContentHandlerWithResult<?>>>();

    public Builder addContentHandler(Provider<? extends ContentHandlerWithResult<?>> handler) {
      Type type = resolveLastTypeParameter(checkNotNull(handler, "handler").getClass(), Provider.class);
      type = resolveLastTypeParameter(type, ContentHandlerWithResult.class);
      this.handlerProviders.put(type, handler);
      return this;
    }

    public SAXDecoder build() {
      return new SAXDecoder(handlerProviders);
    }
  }

  /* Implementations are not intended to be shared across requests. */
  public interface ContentHandlerWithResult<T> extends ContentHandler {
    /*
     * expected to be set following a call to {@link
     * XMLReader#parse(InputSource)}
     */
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
    checkState(handlerProvider != null, "type %s not in configured handlers %s", type, handlerProviders.keySet());
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
