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
package feign.form;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import feign.Feign;
import feign.Param;
import feign.Part;
import feign.Request;
import feign.RequestLine;
import feign.codec.EncodeException;
import feign.core.codec.DefaultEncoder;
import feign.form.multipart.ConditionalEncoder;
import feign.form.multipart.EncoderPredicate;
import feign.form.multipart.PartBodyFactory;
import feign.jackson.JacksonEncoder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import lombok.Cleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@WireMockTest
public class StreamingMultipartFormTest {
  private static final String CLASSNAME = StreamingMultipartFormTest.class.getSimpleName();
  private static final String REQUEST_PATH = "/";
  private static final String REQUEST_LINE = "POST " + REQUEST_PATH;
  private static final String PARAM_NAME = "data";

  private MultipartFormTestClient testClient;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    testClient =
        Feign.builder()
            .encoder(
                MultipartFormEncoder.builder()
                    .partBodyFactory(
                        new PartBodyFactory(
                            List.of(
                                new ConditionalEncoder(
                                    new JacksonEncoder(),
                                    EncoderPredicate.forContentType(
                                        MediaType.APPLICATION_JSON_VALUE)),
                                new DefaultEncoder())))
                    .build())
            .target(MultipartFormTestClient.class, wmRuntimeInfo.getHttpBaseUrl());

    stubFor(post(REQUEST_PATH).willReturn(ok()));
  }

  @Test
  void shouldSendShorthandString() {
    var body = "Hello, World!";

    testClient.sendShorthandString(body);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(body)).build()));
  }

  @Test
  void shouldSendShorthandByteArray() {
    var body = "Hello, World!";

    testClient.sendShorthandByteArray(body.getBytes(StandardCharsets.UTF_8));

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(body)).build()));
  }

  @Test
  void shouldSendShorthandFile(@TempDir File tempDir) throws IOException {
    var body = "Hello, World!";
    var file = new File(tempDir, CLASSNAME + "_shouldSendShorthandFile.txt");

    Files.writeString(file.toPath(), body);

    testClient.sendShorthandFile(file);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(file.getName())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendShorthandPath(@TempDir File tempDir) throws IOException {
    var body = "Hello, World!";
    var file = new File(tempDir, CLASSNAME + "_shouldSendShorthandPath.txt");

    Files.writeString(file.toPath(), body);

    testClient.sendShorthandPath(file.toPath());

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(file.getName())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendShorthandInputStream() throws IOException {
    var body = "Hello, World!";
    @Cleanup var inputStream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));

    testClient.sendShorthandInputStream(inputStream);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(body)).build()));
  }

  @Test
  void shouldSendShorthandRequestBody() {
    var body = "Hello, World!";

    testClient.sendShorthandRequestBody(Request.Body.of(body));

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(body)).build()));
  }

  @Test
  void shouldSendShorthandStringCollection() {
    var joker = "Joker";
    var harleyQuinn = "Harley Quinn";
    var catwoman = "Catwoman";
    var villains = List.of(joker, harleyQuinn, catwoman);

    testClient.sendShorthandStringCollection(villains);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(aMultipart().withName(PARAM_NAME).withBody(equalTo(joker)).build())
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(harleyQuinn)).build())
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(catwoman)).build()));
  }

  @Test
  void shouldSendShorthandFileArray(@TempDir File tempDir) throws IOException {
    var joker = "Joker";
    var harleyQuinn = "Harley Quinn";
    var file1 = new File(tempDir, CLASSNAME + "_shouldSendShorthandFileArray_1.txt");
    var file2 = new File(tempDir, CLASSNAME + "_shouldSendShorthandFileArray_2.txt");

    Files.writeString(file1.toPath(), joker);
    Files.writeString(file2.toPath(), harleyQuinn);

    testClient.sendShorthandFileArray(new File[] {file1, file2});

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(file1.getName())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(joker))
                    .build())
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(file2.getName())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(harleyQuinn))
                    .build()));
  }

  @Test
  void shouldSendShorthandFileCollection(@TempDir File tempDir) throws IOException {
    var joker = "Joker";
    var harleyQuinn = "Harley Quinn";
    var file1 = new File(tempDir, CLASSNAME + "_shouldSendShorthandFileCollection_1.txt");
    var file2 = new File(tempDir, CLASSNAME + "_shouldSendShorthandFileCollection_2.txt");

    Files.writeString(file1.toPath(), joker);
    Files.writeString(file2.toPath(), harleyQuinn);

    testClient.sendShorthandFileCollection(List.of(file1, file2));

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(file1.getName())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(joker))
                    .build())
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(file2.getName())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(harleyQuinn))
                    .build()));
  }

  @Test
  void shouldSendShorthandPathArray(@TempDir Path tempDir) throws IOException {
    var joker = "Joker";
    var harleyQuinn = "Harley Quinn";
    var path1 = tempDir.resolve(CLASSNAME + "_shouldSendShorthandPathArray_1.txt");
    var path2 = tempDir.resolve(CLASSNAME + "_shouldSendShorthandPathArray_2.txt");

    Files.writeString(path1, joker);
    Files.writeString(path2, harleyQuinn);

    testClient.sendShorthandPathArray(new Path[] {path1, path2});

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(path1.getFileName().toString())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(joker))
                    .build())
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(path2.getFileName().toString())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(harleyQuinn))
                    .build()));
  }

  @Test
  void shouldSendShorthandPathCollection(@TempDir Path tempDir) throws IOException {
    var joker = "Joker";
    var harleyQuinn = "Harley Quinn";
    var path1 = tempDir.resolve(CLASSNAME + "_shouldSendShorthandPathCollection_1.txt");
    var path2 = tempDir.resolve(CLASSNAME + "_shouldSendShorthandPathCollection_2.txt");

    Files.writeString(path1, joker);
    Files.writeString(path2, harleyQuinn);

    testClient.sendShorthandPathCollection(List.of(path1, path2));

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(path1.getFileName().toString())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(joker))
                    .build())
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(path2.getFileName().toString())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(harleyQuinn))
                    .build()));
  }

  @Test
  void shouldSendStringWithFullContentDispositionHeader() {
    var body = "Hello, World!";

    testClient.sendStringWithFullContentDispositionHeader(body);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(body)).build()));
  }

  @Test
  void shouldSendShorthandStringArray() {
    var joker = "Joker";
    var harleyQuinn = "Harley Quinn";
    var catwoman = "Catwoman";
    var villains = new String[] {joker, harleyQuinn, catwoman};

    testClient.sendShorthandStringArray(villains);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(aMultipart().withName(PARAM_NAME).withBody(equalTo(joker)).build())
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(harleyQuinn)).build())
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(catwoman)).build()));
  }

  @Test
  void shouldSendStringWithFullContentDispositionAndContentTypeHeaders() {
    var body = "## Hello, World!";

    testClient.sendStringWithFullContentDispositionAndContentTypeHeaders(body);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_MARKDOWN_VALUE))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendStringWithFullContentDispositionFilenameAndContentTypeHeaders() {
    var body = "## Hello, World!";

    testClient.sendStringWithFullContentDispositionFilenameAndContentTypeHeaders(body);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName("file.md")
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_MARKDOWN_VALUE))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendFileWithFullContentDispositionHeader(@TempDir File tempDir) throws IOException {
    var body = "Hello, World!";
    var file = new File(tempDir, CLASSNAME + "_shouldSendFileWithFullContentDispositionHeader.txt");

    Files.writeString(file.toPath(), body);

    testClient.sendFileWithFullContentDispositionHeader(file);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(file.getName())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendPathWithFullContentDispositionHeader(@TempDir Path tempDir) throws IOException {
    var body = "Hello, World!";
    var path = tempDir.resolve(CLASSNAME + "_shouldSendPathWithFullContentDispositionHeader.txt");

    Files.writeString(path, body);

    testClient.sendPathWithFullContentDispositionHeader(path);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(path.getFileName().toString())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendFileWithFullContentDispositionFilename(@TempDir File tempDir) throws IOException {
    var body = "## Hello, World!";
    var file =
        new File(tempDir, CLASSNAME + "_shouldSendFileWithFullContentDispositionFilename.txt");

    Files.writeString(file.toPath(), body);

    testClient.sendFileWithFullContentDispositionAndFilename(file);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName("file.md")
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_MARKDOWN_VALUE))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendPathWithFullContentDispositionFilename(@TempDir Path tempDir) throws IOException {
    var body = "## Hello, World!";
    var path = tempDir.resolve(CLASSNAME + "_shouldSendPathWithFullContentDispositionFilename.txt");

    Files.writeString(path, body);

    testClient.sendPathWithFullContentDispositionAndFilename(path);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName("file.md")
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_MARKDOWN_VALUE))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendFileWithFullContentDispositionAndContentTypeHeaders(@TempDir File tempDir)
      throws IOException {
    var body = "## Hello, World!";
    var file =
        new File(
            tempDir,
            CLASSNAME + "_shouldSendFileWithFullContentDispositionAndContentTypeHeaders.txt");

    Files.writeString(file.toPath(), body);

    testClient.sendFileWithFullContentDispositionAndContentTypeHeaders(file);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(file.getName())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_MARKDOWN_VALUE))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendPathWithFullContentDispositionAndContentTypeHeaders(@TempDir Path tempDir)
      throws IOException {
    var body = "## Hello, World!";
    var file =
        tempDir.resolve(
            CLASSNAME + "_shouldSendPathWithFullContentDispositionAndContentTypeHeaders.txt");

    Files.writeString(file, body);

    testClient.sendPathWithFullContentDispositionAndContentTypeHeaders(file);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(file.getFileName().toString())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.TEXT_MARKDOWN_VALUE))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendShorthandFileUnknownContentType(@TempDir File tempDir) throws IOException {
    var body = "Hello, World!";
    var file = new File(tempDir, CLASSNAME + "_shouldSendShorthandFileUnknownContentType.unknown");

    Files.writeString(file.toPath(), body);

    testClient.sendShorthandFile(file);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(file.getName())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing("application/octet-stream"))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendShorthandPathUnknownContentType(@TempDir Path tempDir) throws IOException {
    var body = "Hello, World!";
    var path = tempDir.resolve(CLASSNAME + "_shouldSendShorthandPathUnknownContentType.unknown");

    Files.writeString(path, body);

    testClient.sendShorthandPath(path);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(path.getFileName().toString())
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing("application/octet-stream"))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendWithParameterizedHeaders() {
    var body = "## Hello, World!";
    var name = "data";
    var filename = "file.md";
    var contentType = MediaType.TEXT_MARKDOWN_VALUE;

    testClient.sendWithParameterizedHeaders(body, name, filename, contentType);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(name)
                    .withFileName(filename)
                    .withHeader(HttpHeaders.CONTENT_TYPE, containing(contentType))
                    .withBody(equalTo(body))
                    .build()));
  }

  @Test
  void shouldSendJsonMovie() {
    var movie = new Movie("Inception", "Christopher Nolan");
    var expected =
        """
                       {
                        "title": "Inception",
                        "director": "Christopher Nolan"
                       }
                       """;

    testClient.sendJsonMovie(movie);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withHeader(
                        HttpHeaders.CONTENT_TYPE, containing(MediaType.APPLICATION_JSON_VALUE))
                    .withBody(equalToJson(expected))
                    .build()));
  }

  @Test
  void shouldFailToSendXmlMovie() {
    var movie = new Movie("Inception", "Christopher Nolan");

    assertThrows(EncodeException.class, () -> testClient.sendXmlMovie(movie));
  }

  private interface MultipartFormTestClient {
    @RequestLine(REQUEST_LINE)
    void sendShorthandString(@Part(PARAM_NAME) String data);

    @RequestLine(REQUEST_LINE)
    void sendShorthandByteArray(@Part(PARAM_NAME) byte[] data);

    @RequestLine(REQUEST_LINE)
    void sendShorthandFile(@Part(PARAM_NAME) File data);

    @RequestLine(REQUEST_LINE)
    void sendShorthandPath(@Part(PARAM_NAME) Path data);

    @RequestLine(REQUEST_LINE)
    void sendShorthandInputStream(@Part(PARAM_NAME) InputStream data);

    @RequestLine(REQUEST_LINE)
    void sendShorthandRequestBody(@Part(PARAM_NAME) Request.Body data);

    @RequestLine(REQUEST_LINE)
    void sendShorthandStringCollection(@Part(PARAM_NAME) Collection<String> data);

    @RequestLine(REQUEST_LINE)
    void sendShorthandStringArray(@Part(PARAM_NAME) String[] data);

    @RequestLine(REQUEST_LINE)
    void sendShorthandFileArray(@Part(PARAM_NAME) File[] data);

    @RequestLine(REQUEST_LINE)
    void sendShorthandFileCollection(@Part(PARAM_NAME) Collection<File> data);

    @RequestLine(REQUEST_LINE)
    void sendShorthandPathArray(@Part(PARAM_NAME) Path[] data);

    @RequestLine(REQUEST_LINE)
    void sendShorthandPathCollection(@Part(PARAM_NAME) Collection<Path> data);

    @RequestLine(REQUEST_LINE)
    void sendStringWithFullContentDispositionHeader(
        @Part("Content-Disposition: form-data; name=\"data\"") String data);

    @RequestLine(REQUEST_LINE)
    void sendStringWithFullContentDispositionAndContentTypeHeaders(
        @Part({"Content-Disposition: form-data; name=\"data\"", "Content-Type: text/markdown"})
            String data);

    @RequestLine(REQUEST_LINE)
    void sendStringWithFullContentDispositionFilenameAndContentTypeHeaders(
        @Part({
              "Content-Disposition: form-data; name=\"data\"; filename=\"file.md\"",
              "Content-Type: text/markdown"
            })
            String data);

    @RequestLine(REQUEST_LINE)
    void sendFileWithFullContentDispositionHeader(
        @Part("Content-Disposition: form-data; name=\"data\"") File data);

    @RequestLine(REQUEST_LINE)
    void sendPathWithFullContentDispositionHeader(
        @Part("Content-Disposition: form-data; name=\"data\"") Path data);

    @RequestLine(REQUEST_LINE)
    void sendFileWithFullContentDispositionAndFilename(
        @Part("Content-Disposition: form-data; name=\"data\"; filename=\"file.md\"") File data);

    @RequestLine(REQUEST_LINE)
    void sendPathWithFullContentDispositionAndFilename(
        @Part("Content-Disposition: form-data; name=\"data\"; filename=\"file.md\"") Path data);

    @RequestLine(REQUEST_LINE)
    void sendFileWithFullContentDispositionAndContentTypeHeaders(
        @Part({"Content-Disposition: form-data; name=\"data\"", "Content-Type: text/markdown"})
            File data);

    @RequestLine(REQUEST_LINE)
    void sendPathWithFullContentDispositionAndContentTypeHeaders(
        @Part({"Content-Disposition: form-data; name=\"data\"", "Content-Type: text/markdown"})
            Path data);

    @RequestLine(REQUEST_LINE)
    void sendWithParameterizedHeaders(
        @Part({
              "Content-Disposition: form-data; name=\"{name}\"; filename=\"{filename}\"",
              "Content-Type: {contentType}"
            })
            String data,
        @Param("name") String name,
        @Param("filename") String filename,
        @Param("contentType") String contentType);

    @RequestLine(REQUEST_LINE)
    void sendJsonMovie(
        @Part({"Content-Disposition: form-data; name=\"data\"", "Content-Type: application/json"})
            Movie movie);

    @RequestLine(REQUEST_LINE)
    void sendXmlMovie(
        @Part({"Content-Disposition: form-data; name=\"data\"", "Content-Type: application/xml"})
            Movie movie);
  }

  private record Movie(String title, String director) {}
}
