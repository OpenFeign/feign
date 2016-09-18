/*
 * Copyright 2016 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.form.spring;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.springframework.web.multipart.MultipartFile;

import feign.codec.EncodeException;
import feign.form.MultipartEncodedDataProcessor;

/**
 * Adds support for {@link MultipartFile} type to {@link MultipartEncodedDataProcessor}.
 * 
 * @author Tomasz Juchniewicz <tjuchniewicz@gmail.com>
 * @since 14.09.2016
 */
public class SpringMultipartEncodedDataProcessor extends MultipartEncodedDataProcessor {

    
    @Override
    protected boolean isFile (Object value) {
        return super.isFile(value) || value instanceof MultipartFile;
    }

    @Override
    protected void writeFile (OutputStream output, PrintWriter writer, String name, Object value) {
        if (value instanceof MultipartFile) {
            try {
                writeFile(output, writer, name, ((MultipartFile) value).getBytes());
            } catch (IOException e) {
                throw new EncodeException("Can't encode MultipartFile", e);
            }
            return;
        }

        super.writeFile(output, writer, name, value);
    }
}
