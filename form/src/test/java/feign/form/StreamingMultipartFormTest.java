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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import feign.Feign;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Util;
import feign.form.multipart.FormData;
import feign.jackson.JacksonEncoder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.Cleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;

@WireMockTest
public class StreamingMultipartFormTest {
  private static final String REQUEST_PATH = "/";
  private static final String REQUEST_LINE = "POST " + REQUEST_PATH;
  private static final String MULTIPART_FORM_DATA_HEADER =
      Util.CONTENT_TYPE + ": " + MediaType.MULTIPART_FORM_DATA_VALUE;
  private static final String PARAM_NAME = "data";

  private MultipartFormTestClient testClient;

  @BeforeEach
  public void setup(WireMockRuntimeInfo wmRuntimeInfo) {
    testClient =
        Feign.builder()
            .encoder(
                MultipartFormEncoder.builder()
                    .partBodyEncoders(List.of(new JacksonEncoder()))
                    .build())
            .target(MultipartFormTestClient.class, wmRuntimeInfo.getHttpBaseUrl());

    stubFor(post(REQUEST_PATH).willReturn(ok()));
  }

  @Test
  void shouldSendStringArray() {
    var joker = "Joker";
    var harleyQuinn = "Harley Quinn";
    var catwoman = "Catwoman";
    var villains = new String[] {joker, harleyQuinn, catwoman};

    testClient.sendStringArray(villains);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(Util.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(aMultipart().withName(PARAM_NAME).withBody(equalTo(joker)).build())
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(harleyQuinn)).build())
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(catwoman)).build()));
  }

  @Test
  void shouldSendByteArray() {
    var expected = "Hello, World!";

    testClient.sendByteArray(expected.getBytes(StandardCharsets.UTF_8));

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(Util.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(expected)).build()));
  }

  @Test
  void shouldSendString() {
    var expected = "Hello, World!";

    testClient.sendString(expected);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(Util.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(expected)).build()));
  }

  @Test
  void shouldSendJson() {
    var movie = new Movie("The Dark Knight", "Christopher Nolan");
    var expected =
        """
                {
                  "title": "The Dark Knight",
                  "director": "Christopher Nolan"
                }
                """;

    testClient.sendMovie(new FormData<>(movie).contentType(MediaType.APPLICATION_JSON_VALUE));

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(Util.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withHeader(Util.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                    .withBody(equalToJson(expected))
                    .build()));
  }

  @Test
  void shouldSendFile(@TempDir File tempDir) throws IOException {
    var expected = "Hello, World!";
    var file =
        new File(tempDir, StreamingMultipartFormTest.class.getSimpleName() + "_shouldSendFile.txt");

    Files.writeString(file.toPath(), expected);

    testClient.sendFile(file);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(Util.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(file.getName())
                    .withHeader(Util.CONTENT_TYPE, equalTo(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(expected))
                    .build()));
  }

  @Test
  void shouldSendOctetStreamFile(@TempDir File tempDir) throws IOException {
    var expected = "Hello, World!";
    var file =
        new File(
            tempDir,
            StreamingMultipartFormTest.class.getSimpleName() + "_shouldSendOctetStreamFile");

    Files.writeString(file.toPath(), expected);

    testClient.sendFile(file);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(Util.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(file.getName())
                    .withHeader(
                        Util.CONTENT_TYPE, equalTo(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .withBody(equalTo(expected))
                    .build()));
  }

  @Test
  void shouldSendFileWithOverriddenFilenameAndContentType(@TempDir File tempDir)
      throws IOException {
    var expected = "Hello, World!";
    var filename =
        StreamingMultipartFormTest.class.getSimpleName()
            + "_shouldSendFileWithOverriddenFilenameAndContentType.txt";
    var file =
        new File(
            tempDir,
            StreamingMultipartFormTest.class.getSimpleName()
                + "_shouldSendFileWithOverriddenFilenameAndContentType.md");

    Files.writeString(file.toPath(), expected);

    testClient.sendFileFormData(
        new FormData<>(file).filename(filename).contentType(MediaType.TEXT_PLAIN_VALUE));

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(Util.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(filename)
                    .withHeader(Util.CONTENT_TYPE, equalTo(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(expected))
                    .build()));
  }

  @Test
  void shouldSendInputStream() throws IOException {
    var expected = "Hello, World!";
    @Cleanup var inputStream = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8));

    testClient.sendInputStream(inputStream);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(Util.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(expected)).build()));
  }

  @Test
  void shouldSendStringList() {
    var joker = "Joker";
    var harleyQuinn = "Harley Quinn";
    var catwoman = "Catwoman";
    var villains = List.of(joker, harleyQuinn, catwoman);

    testClient.sendStringIterable(villains);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(Util.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(aMultipart().withName(PARAM_NAME).withBody(equalTo(joker)).build())
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(harleyQuinn)).build())
            .withRequestBodyPart(
                aMultipart().withName(PARAM_NAME).withBody(equalTo(catwoman)).build()));
  }

  @Test
  void shouldSendPath(@TempDir Path tempDir) throws IOException {
    var expected = "Hello, World!";
    var path =
        tempDir.resolve(StreamingMultipartFormTest.class.getSimpleName() + "_shouldSendPath.txt");

    Files.writeString(path, expected);

    testClient.sendPath(path);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(Util.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(PARAM_NAME)
                    .withFileName(path.getFileName().toString())
                    .withHeader(Util.CONTENT_TYPE, equalTo(MediaType.TEXT_PLAIN_VALUE))
                    .withBody(equalTo(expected))
                    .build()));
  }

  private interface MultipartFormTestClient {
    @RequestLine(REQUEST_LINE)
    @Headers(MULTIPART_FORM_DATA_HEADER)
    void sendStringArray(@Param(PARAM_NAME) String[] data);

    @RequestLine(REQUEST_LINE)
    @Headers(MULTIPART_FORM_DATA_HEADER)
    void sendByteArray(@Param(PARAM_NAME) byte[] data);

    @RequestLine(REQUEST_LINE)
    @Headers(MULTIPART_FORM_DATA_HEADER)
    void sendString(@Param(PARAM_NAME) String data);

    @RequestLine(REQUEST_LINE)
    @Headers(MULTIPART_FORM_DATA_HEADER)
    void sendMovie(@Param(PARAM_NAME) FormData<Movie> data);

    @RequestLine(REQUEST_LINE)
    @Headers(MULTIPART_FORM_DATA_HEADER)
    void sendFile(@Param(PARAM_NAME) File data);

    @RequestLine(REQUEST_LINE)
    @Headers(MULTIPART_FORM_DATA_HEADER)
    void sendFileFormData(@Param(PARAM_NAME) FormData<File> data);

    @RequestLine(REQUEST_LINE)
    @Headers(MULTIPART_FORM_DATA_HEADER)
    void sendInputStream(@Param(PARAM_NAME) InputStream data);

    @RequestLine(REQUEST_LINE)
    @Headers(MULTIPART_FORM_DATA_HEADER)
    void sendStringIterable(@Param(PARAM_NAME) Iterable<String> data);

    @RequestLine(REQUEST_LINE)
    @Headers(MULTIPART_FORM_DATA_HEADER)
    void sendPath(@Param(PARAM_NAME) Path data);
  }

  private record Movie(
      @JsonProperty("title") String title, @JsonProperty("director") String director) {}
}
