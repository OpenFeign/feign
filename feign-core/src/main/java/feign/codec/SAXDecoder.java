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

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import static feign.Util.checkNotNull;
import static feign.Util.checkState;

public abstract class SAXDecoder extends Decoder {
  /* Implementations are not intended to be shared across requests. */
  public interface ContentHandlerWithResult extends ContentHandler {
    /* expected to be set following a call to {@link XMLReader#parse(InputSource)} */
    Object getResult();
  }

  private final SAXParserFactory factory;

  protected SAXDecoder() {
    this(SAXParserFactory.newInstance());
    factory.setNamespaceAware(false);
    factory.setValidating(false);
  }

  protected SAXDecoder(SAXParserFactory factory) {
    this.factory = checkNotNull(factory, "factory");
  }

  @Override
  public Object decode(Reader reader, Type type) throws IOException, SAXException, ParserConfigurationException {
    ContentHandlerWithResult handler = typeToNewHandler(type);
    checkState(handler != null, "%s returned null for type %s", this, type);
    XMLReader xmlReader = factory.newSAXParser().getXMLReader();
    xmlReader.setContentHandler(handler);
    InputSource source = new InputSource(reader);
    xmlReader.parse(source);
    return handler.getResult();
  }

  protected abstract ContentHandlerWithResult typeToNewHandler(Type type);
}
