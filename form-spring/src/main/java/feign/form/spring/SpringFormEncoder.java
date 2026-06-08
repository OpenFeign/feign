/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.form.spring;

import static feign.form.ContentType.MULTIPART;
import static java.util.Collections.singletonMap;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.core.codec.DefaultEncoder;
import feign.form.FormEncoder;
import feign.form.MultipartFormContentProcessor;
import java.lang.reflect.Type;
import java.util.HashMap;
import org.springframework.web.multipart.MultipartFile;

/**
 * Adds support for {@link MultipartFile} type to {@link FormEncoder}.
 *
 * @since 14.09.2016
 * @author Tomasz Juchniewicz &lt;tjuchniewicz@gmail.com&gt;
 */
public class SpringFormEncoder extends FormEncoder {

  /** Constructor with the default Feign's encoder as a delegate. */
  public SpringFormEncoder() {
    this(new DefaultEncoder());
  }

  /**
   * Constructor with specified delegate encoder.
   *
   * @param delegate delegate encoder, if this encoder couldn't encode object.
   */
  public SpringFormEncoder(Encoder delegate) {
    super(delegate);

    final var processor = (MultipartFormContentProcessor) getContentProcessor(MULTIPART);
    processor.addFirstWriter(new SpringSingleMultipartFileWriter());
    processor.addFirstWriter(new SpringManyMultipartFilesWriter());
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    if (bodyType.equals(MultipartFile[].class)) {
      final var files = (MultipartFile[]) object;
      final var data = new HashMap<String, Object>(files.length, 1.F);
      for (var file : files) {
        data.put(file.getName(), file);
      }
      super.encode(data, MAP_STRING_WILDCARD, template);
    } else if (bodyType.equals(MultipartFile.class)) {
      final var file = (MultipartFile) object;
      final var data = singletonMap(file.getName(), object);
      super.encode(data, MAP_STRING_WILDCARD, template);
    } else if (isMultipartFileCollection(object)) {
      final var iterable = (Iterable<?>) object;
      final var data = new HashMap<String, Object>();
      for (var item : iterable) {
        final var file = (MultipartFile) item;
        data.put(file.getName(), file);
      }
      super.encode(data, MAP_STRING_WILDCARD, template);
    } else {
      super.encode(object, bodyType, template);
    }
  }

  private boolean isMultipartFileCollection(Object object) {
    if (!(object instanceof Iterable)) {
      return false;
    }
    final var iterable = (Iterable<?>) object;
    final var iterator = iterable.iterator();
    return iterator.hasNext() && iterator.next() instanceof MultipartFile;
  }
}
