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

import feign.Request;
import feign.RequestTemplate;
import feign.form.multipart.ConditionalEncoder;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import lombok.NonNull;
import org.springframework.web.multipart.MultipartFile;

/** Encoder for {@link MultipartFile} instances. */
public class MultipartFileEncoder extends ConditionalEncoder {
  /**
   * Creates a new instance of {@link MultipartFileEncoder} that encodes {@link MultipartFile}
   * instances.
   */
  public MultipartFileEncoder() {
    super(MultipartFileEncoder::doEncode, MultipartFileEncoder::isMultipartFile);
  }

  private static void doEncode(Object object, Type bodyType, RequestTemplate template) {
    template.body(new MultipartFileBody((MultipartFile) object));
  }

  private static boolean isMultipartFile(Object object, Type bodyType, RequestTemplate template) {
    return object instanceof MultipartFile;
  }

  private record MultipartFileBody(@NonNull MultipartFile multipartFile) implements Request.Body {
    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
      multipartFile.getInputStream().transferTo(outputStream);
    }

    @Override
    public long contentLength() {
      return multipartFile.getSize();
    }

    @Override
    public String toString() {
      return "[Binary data (" + contentLength() + " bytes)]";
    }
  }
}
