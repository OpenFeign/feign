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

package feign.form.spring.converter;

import static feign.form.util.CharsetUtil.UTF_8;
import static lombok.AccessLevel.PRIVATE;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.experimental.FieldDefaults;
import lombok.val;
import org.apache.commons.fileupload.MultipartStream;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementation of {@link HttpMessageConverter} that can read multipart/form-data HTTP bodies
 * (writing is not handled because that is already supported by {@link FormHttpMessageConverter}).
 * <p>
 * This reader supports an array of {@link MultipartFile} as the mapping return class type - each
 * multipart body is read into an underlying byte array (in memory) implemented via
 * {@link ByteArrayMultipartFile}.
 */
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class SpringManyMultipartFilesReader extends AbstractHttpMessageConverter<MultipartFile[]> {

  private static final Pattern NEWLINES_PATTERN = Pattern.compile("\\R");

  private static final Pattern COLON_PATTERN = Pattern.compile(":");

  private static final Pattern SEMICOLON_PATTERN = Pattern.compile(";");

  private static final Pattern EQUALITY_SIGN_PATTERN = Pattern.compile("=");

  int bufSize;

  /**
   * Construct an {@code AbstractHttpMessageConverter} that can read mulitpart/form-data.
   *
   * @param bufSize The size of the buffer (in bytes) to read the HTTP multipart body.
   */
  public SpringManyMultipartFilesReader (int bufSize) {
    super(MULTIPART_FORM_DATA);
    this.bufSize = bufSize;
  }

  @Override
  protected boolean canWrite (MediaType mediaType) {
    return false; // Class NOT meant for writing multipart/form-data HTTP bodies
  }

  @Override
  protected boolean supports (Class<?> clazz) {
    return MultipartFile[].class == clazz;
  }

  @Override
  protected MultipartFile[] readInternal (Class<? extends MultipartFile[]> clazz, HttpInputMessage inputMessage
  ) throws IOException {
    val headers = inputMessage.getHeaders();
    if (headers == null) {
      throw new HttpMessageNotReadableException("There are no headers at all.", inputMessage);
    }

    MediaType contentType = headers.getContentType();
    if (contentType == null) {
      throw new HttpMessageNotReadableException("Content-Type is missing.", inputMessage);
    }

    val boundaryBytes = getMultiPartBoundary(contentType);
    MultipartStream multipartStream = new MultipartStream(inputMessage.getBody(), boundaryBytes, bufSize, null);

    val multiparts = new LinkedList<ByteArrayMultipartFile>();
    for (boolean nextPart = multipartStream.skipPreamble(); nextPart; nextPart = multipartStream.readBoundary()) {
      ByteArrayMultipartFile multiPart;
      try {
        multiPart = readMultiPart(multipartStream);
      } catch (Exception e) {
        throw new HttpMessageNotReadableException("Multipart body could not be read.", e, inputMessage);
      }
      multiparts.add(multiPart);
    }
    return multiparts.toArray(new ByteArrayMultipartFile[0]);
  }

  @Override
  protected void writeInternal (MultipartFile[] byteArrayMultipartFiles, HttpOutputMessage outputMessage) {
    throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support writing to HTTP body.");
  }

  private byte[] getMultiPartBoundary (MediaType contentType) {
    val boundaryString = unquote(contentType.getParameter("boundary"));
    if (StringUtils.isEmpty(boundaryString)) {
      throw new HttpMessageConversionException("Content-Type missing boundary information.");
    }
    return boundaryString.getBytes(UTF_8);
  }

  private ByteArrayMultipartFile readMultiPart (MultipartStream multipartStream) throws IOException {
    val multiPartHeaders = splitIntoKeyValuePairs(
        multipartStream.readHeaders(),
        NEWLINES_PATTERN,
        COLON_PATTERN,
        false
    );

    val contentDisposition = splitIntoKeyValuePairs(
        multiPartHeaders.get(CONTENT_DISPOSITION),
        SEMICOLON_PATTERN,
        EQUALITY_SIGN_PATTERN,
        true
    );

    if (!contentDisposition.containsKey("form-data")) {
      throw new HttpMessageConversionException("Content-Disposition is not of type form-data.");
    }

    val bodyStream = new ByteArrayOutputStream();
    multipartStream.readBodyData(bodyStream);
    return new ByteArrayMultipartFile(
        contentDisposition.get("name"),
        contentDisposition.get("filename"),
        multiPartHeaders.get(CONTENT_TYPE),
        bodyStream.toByteArray()
    );
  }

  private Map<String, String> splitIntoKeyValuePairs (String str, Pattern entriesSeparatorPattern,
                                                      Pattern keyValueSeparatorPattern, boolean unquoteValue
  ) {
    val keyValuePairs = new IgnoreKeyCaseMap();
    if (!StringUtils.isEmpty(str)) {
      val tokens = entriesSeparatorPattern.split(str);
      for (val token : tokens) {
        val pair = keyValueSeparatorPattern.split(token.trim(), 2);
        val key = pair[0].trim();
        val value = pair.length > 1
                    ? pair[1].trim()
                    : "";

        keyValuePairs.put(key, unquoteValue
                               ? unquote(value)
                               : value);
      }
    }
    return keyValuePairs;
  }

  private String unquote (String value) {
    if (value == null) {
      return null;
    }
    return isSurroundedBy(value, "\"") || isSurroundedBy(value, "'")
         ? value.substring(1, value.length() - 1)
         : value;
  }

  private boolean isSurroundedBy (String value, String preSuffix) {
    return value.length() > 1 && value.startsWith(preSuffix) && value.endsWith(preSuffix);
  }
}
