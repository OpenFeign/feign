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

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.inject.Provider;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

import static feign.Util.checkNotNull;
import static feign.Util.checkState;

public class SAXDecoder<T> implements Decoder.TextStream<T> {
  /* Implementations are not intended to be shared across requests. */
  public interface ContentHandlerWithResult<T> extends ContentHandler {
    /*
     * expected to be set following a call to {@link
     * XMLReader#parse(InputSource)}
     */
    T result();
  }

  private final Provider<? extends ContentHandlerWithResult<T>> handlers;

  /**
   * You must subclass this, in order to prevent type erasure on {@code T}. In
   * addition to making a concrete type, you can also use the following form.
   * <p/>
   * <br>
   * <p/>
   * <pre>
   * new SaxDecoder&lt;Foo&gt;(fooHandlers) {
   * }; // note the curly braces ensures no type erasure!
   * </pre>
   */
  protected SAXDecoder(Provider<? extends ContentHandlerWithResult<T>> handlers) {
    this.handlers = checkNotNull(handlers, "handlers");
  }

  @Override
  public T decode(Reader reader, Type type) throws IOException, DecodeException {
    ContentHandlerWithResult<T> handler = handlers.get();
    checkState(handler != null, "%s returned null for type %s", this, type);
    try {
      XMLReader xmlReader = XMLReaderFactory.createXMLReader();
      xmlReader.setFeature("http://xml.org/sax/features/namespaces", false);
      xmlReader.setFeature("http://xml.org/sax/features/validation", false);
      xmlReader.setContentHandler(handler);
      xmlReader.parse(new InputSource(reader));
      return handler.result();
    } catch (SAXException e) {
      throw new DecodeException(e.getMessage(), e);
    }
  }
}
