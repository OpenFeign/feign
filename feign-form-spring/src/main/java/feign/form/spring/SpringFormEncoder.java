/*
 * Copyright 2017 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import feign.form.FormEncoder;
import feign.form.MultipartFormContentProcessor;
import java.lang.reflect.Type;
import lombok.val;
import org.springframework.web.multipart.MultipartFile;

/**
 * Adds support for {@link MultipartFile} type to {@link FormEncoder}.
 *
 * @author Tomasz Juchniewicz <tjuchniewicz@gmail.com>
 * @since 14.09.2016
 */
public class SpringFormEncoder extends FormEncoder {

  /**
   * Constructor with the default Feign's encoder as a delegate.
   */
  public SpringFormEncoder () {
    this(new Encoder.Default());
  }

  /**
   * Constructor with specified delegate encoder.
   *
   * @param delegate  delegate encoder, if this encoder couldn't encode object.
   */
  public SpringFormEncoder (Encoder delegate) {
    super(delegate);

    val processor = (MultipartFormContentProcessor) getContentProcessor(MULTIPART);
    processor.addWriter(new SpringSingleMultipartFileWriter());
    processor.addWriter(new SpringManyMultipartFilesWriter());
  }

  @Override
  public void encode (Object object, Type bodyType, RequestTemplate template) throws EncodeException {
    if (!bodyType.equals(MultipartFile.class)) {
      super.encode(object, bodyType, template);
      return;
    }

    val file = (MultipartFile) object;
    val data = singletonMap(file.getName(), object);
    super.encode(data, MAP_STRING_WILDCARD, template);
  }
}
