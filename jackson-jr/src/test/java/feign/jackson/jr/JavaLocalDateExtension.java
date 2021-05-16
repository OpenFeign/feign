/**
 * Copyright 2012-2021 The Feign Authors
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
package feign.jackson.jr;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.jr.ob.JacksonJrExtension;
import com.fasterxml.jackson.jr.ob.api.ExtensionContext;
import com.fasterxml.jackson.jr.ob.api.ReaderWriterProvider;
import com.fasterxml.jackson.jr.ob.api.ValueReader;
import com.fasterxml.jackson.jr.ob.api.ValueWriter;
import com.fasterxml.jackson.jr.ob.impl.JSONReader;
import com.fasterxml.jackson.jr.ob.impl.JSONWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Adapted from https://www.andersaaberg.dk/2020/enable-support-for-java-time-with-jackson-jr/ This
 * adds Java Time support to Jackson JR
 */
public class JavaLocalDateExtension extends JacksonJrExtension {
  private static class LocalDateReaderWriterProvider extends ReaderWriterProvider {
    @Override
    public ValueReader findValueReader(JSONReader readContext, Class<?> type) {
      return type == LocalDate.class ? new LocalDateValueReader() : null;
    }

    @Override
    public ValueWriter findValueWriter(JSONWriter writeContext, Class<?> type) {
      return type == LocalDate.class ? new LocalDateValueWriter() : null;
    }
  }

  private static class LocalDateValueReader extends ValueReader {
    protected LocalDateValueReader() {
      super(LocalDate.class);
    }

    @Override
    public Object read(JSONReader reader, JsonParser p) throws IOException {
      return LocalDate.parse(p.getText(), DateTimeFormatter.ISO_LOCAL_DATE);
    }
  }

  private static class LocalDateValueWriter implements ValueWriter {
    @Override
    public void writeValue(JSONWriter context, JsonGenerator g, Object value) throws IOException {
      context.writeValue(((LocalDate) value).format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    @Override
    public Class<?> valueType() {
      return LocalDate.class;
    }
  }

  @Override
  protected void register(ExtensionContext ctxt) {
    ctxt.insertProvider(new LocalDateReaderWriterProvider());
  }
}

