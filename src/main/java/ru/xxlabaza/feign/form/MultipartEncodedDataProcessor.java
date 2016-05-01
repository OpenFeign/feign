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
package ru.xxlabaza.feign.form;

import feign.RequestTemplate;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.val;

import static feign.Util.UTF_8;

/**
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since 30.04.2016
 */
class MultipartEncodedDataProcessor implements FormDataProcessor {

    public static final String CONTENT_TYPE;

    private static final String CRLF;

    static {
        CONTENT_TYPE = "multipart/form-data";
        CRLF = "\r\n";
    }

    @Override
    public void process (Map<String, Object> data, RequestTemplate template) {
        val boundary = createBoundary();
        val outputStream = new ByteArrayOutputStream();
        val writer = new PrintWriter(outputStream);

        data.entrySet().stream().forEach(it -> {
            writer.append("--" + boundary).append(CRLF);
            if (isFile(it.getValue())) {
                writeFile(outputStream, writer, it.getKey(), it.getValue());
            } else {
                writeParameter(writer, it.getKey(), it.getValue().toString());
            }
            writer.append(CRLF).flush();
        });

        writer.append("--" + boundary + "--").append(CRLF).flush();

        val contentType = new StringBuilder()
                .append(CONTENT_TYPE)
                .append("; boundary=")
                .append(boundary)
                .toString();

        template.header("Content-Type", contentType);
        template.body(outputStream.toByteArray(), UTF_8);
    }

    @Override
    public String getSupportetContentType () {
        return CONTENT_TYPE;
    }

    private String createBoundary () {
        return Long.toHexString(System.currentTimeMillis());
    }

    private boolean isFile (Object value) {
        return value != null && (value instanceof Path || value instanceof File || value instanceof byte[]);
    }

    private void writeParameter (PrintWriter writer, String name, String value) {
        writer.append("Content-Disposition: form-data; name=\"" + name + "\"").append(CRLF);
        writer.append("Content-Type: text/plain; charset=UTF-8").append(CRLF);
        writer.append(CRLF).append(value);
    }

    private void writeFile (OutputStream output, PrintWriter writer, String name, Object value) {
        if (value instanceof byte[]) {
            writeFile(output, writer, name, (byte[]) value);
            return;
        }

        Path pathValue = value instanceof Path
                         ? (Path) value
                         : ((File) value).toPath();

        writeFile(output, writer, name, pathValue);
    }

    @SneakyThrows
    private void writeFile (OutputStream output, PrintWriter writer, String name, Path value) {
        writeFileMeta(writer, name, value.getFileName().toString());
        Files.copy(value, output);
        output.flush();
    }

    @SneakyThrows
    private void writeFile (OutputStream output, PrintWriter writer, String name, byte[] bytes) {
        writeFileMeta(writer, name, "");
        output.write(bytes);
        output.flush();
    }

    private void writeFileMeta (PrintWriter writer, String name, String fileName) {
        val contentDesposition = new StringBuilder()
                .append("Content-Disposition: form-data; name=\"").append(name).append("\"; ")
                .append("filename=\"").append(fileName).append("\"")
                .toString();
        val contentType = new StringBuilder()
                .append("Content-Type: ")
                .append(URLConnection.guessContentTypeFromName(fileName))
                .toString();

        writer.append(contentDesposition).append(CRLF);
        writer.append(contentType).append(CRLF);
        writer.append("Content-Transfer-Encoding: binary").append(CRLF);
        writer.append(CRLF).flush();
    }
}
