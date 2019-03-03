/*
 * Copyright 2019 the original author or authors.
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

package feign.form;

import static feign.form.ContentType.MULTIPART;
import static lombok.AccessLevel.PRIVATE;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;

import feign.Request;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.form.multipart.ByteArrayWriter;
import feign.form.multipart.DelegateWriter;
import feign.form.multipart.FormDataWriter;
import feign.form.multipart.ManyFilesWriter;
import feign.form.multipart.ManyParametersWriter;
import feign.form.multipart.Output;
import feign.form.multipart.PojoWriter;
import feign.form.multipart.SingleFileWriter;
import feign.form.multipart.SingleParameterWriter;
import feign.form.multipart.Writer;

import lombok.experimental.FieldDefaults;
import lombok.val;

/**
 *
 * @author Artem Labazin
 */
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class MultipartFormContentProcessor implements ContentProcessor {

  Deque<Writer> writers;

  Writer defaultPerocessor;

  /**
   * Constructor with specific delegate encoder.
   *
   * @param delegate specific delegate encoder for cases, when this processor couldn't handle request parameter.
   */
  public MultipartFormContentProcessor (Encoder delegate) {
    writers = new LinkedList<Writer>();
    addWriter(new ByteArrayWriter());
    addWriter(new FormDataWriter());
    addWriter(new SingleFileWriter());
    addWriter(new ManyFilesWriter());
    addWriter(new SingleParameterWriter());
    addWriter(new ManyParametersWriter());
    addWriter(new PojoWriter(writers));

    defaultPerocessor = new DelegateWriter(delegate);
  }

  @Override
  public void process (RequestTemplate template, Charset charset, Map<String, Object> data) throws EncodeException {
    val boundary = Long.toHexString(System.currentTimeMillis());
    val output = new Output(charset);

    for (val entry : data.entrySet()) {
      if (entry == null || entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      val writer = findApplicableWriter(entry.getValue());
      writer.write(output, boundary, entry.getKey(), entry.getValue());
    }

    output.write("--").write(boundary).write("--").write(CRLF);

    val contentTypeHeaderValue = new StringBuilder()
        .append(getSupportedContentType().getHeader())
        .append("; charset=").append(charset.name())
        .append("; boundary=").append(boundary)
        .toString();

    template.header(CONTENT_TYPE_HEADER, Collections.<String>emptyList()); // reset header
    template.header(CONTENT_TYPE_HEADER, contentTypeHeaderValue);

    // Feign's clients try to determine binary/string content by charset presence
    // so, I set it to null (in spite of availability charset) for backward compatibility.
    val bytes = output.toByteArray();
    val body = Request.Body.encoded(bytes, null);
    template.body(body);

    try {
      output.close();
    } catch (IOException ex) {
      throw new EncodeException("Output closing error", ex);
    }
  }

  @Override
  public ContentType getSupportedContentType () {
    return MULTIPART;
  }

  /**
   * Adds {@link Writer} instance in runtime.
   *
   * @param writer additional writer.
   */
  public final void addWriter (Writer writer) {
    writers.add(writer);
  }

  /**
   * Adds {@link Writer} instance in runtime
   * at the beginning of writers list.
   *
   * @param writer additional writer.
   */
  public final void addFirstWriter (Writer writer) {
    writers.addFirst(writer);
  }

  /**
   * Adds {@link Writer} instance in runtime
   * at the end of writers list.
   *
   * @param writer additional writer.
   */
  public final void addLastWriter (Writer writer) {
    writers.addLast(writer);
  }

  /**
   * Returns the <b>unmodifiable</b> collection of all writers.
   *
   * @return writers collection.
   */
  public final Collection<Writer> getWriters () {
    return Collections.unmodifiableCollection(writers);
  }

  private Writer findApplicableWriter (Object value) {
    for (val writer : writers) {
      if (writer.isApplicable(value)) {
        return writer;
      }
    }
    return defaultPerocessor;
  }
}
