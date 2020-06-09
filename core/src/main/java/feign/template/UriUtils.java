/**
 * Copyright 2012-2020 The Feign Authors
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
package feign.template;

import feign.Util;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UriUtils {

  private static final Pattern PCT_ENCODED_PATTERN = Pattern.compile("%[0-9A-Fa-f][0-9A-Fa-f]");

  /**
   * Determines if the value is already pct-encoded.
   *
   * @param value to check.
   * @return {@literal true} if the value is already pct-encoded
   */
  public static boolean isEncoded(String value, Charset charset) {
    for (byte b : value.getBytes(charset)) {
      if (!isUnreserved((char) b) && b != '%') {
        /* break if there are any unreserved character */
        return false;
      }
    }
    return PCT_ENCODED_PATTERN.matcher(value).find();
  }

  /**
   * Uri Encode the value, using the default Charset. Already encoded values are skipped.
   *
   * @param value to encode.
   * @return the encoded value.
   */
  public static String encode(String value) {
    return encodeChunk(value, Util.UTF_8, false);
  }

  /**
   * Uri Encode the value. Already encoded values are skipped.
   *
   * @param value to encode.
   * @param charset to use.
   * @return the encoded value.
   */
  public static String encode(String value, Charset charset) {
    return encodeChunk(value, charset, false);
  }

  public static String encode(String value, boolean allowReservedCharacters) {
    return encodeInternal(value, Util.UTF_8, allowReservedCharacters);
  }

  public static String encode(String value, Charset charset, boolean allowReservedCharacters) {
    return encodeInternal(value, charset, allowReservedCharacters);
  }

  /**
   * Uri Decode the value.
   *
   * @param value to decode
   * @param charset to use.
   * @return the decoded value.
   */
  public static String decode(String value, Charset charset) {
    try {
      /* there is nothing special between uri and url decoding */
      return URLDecoder.decode(value, charset.name());
    } catch (UnsupportedEncodingException uee) {
      /* since the encoding is not supported, return the original value */
      return value;
    }
  }


  /**
   * Determines if the provided uri is an absolute uri.
   *
   * @param uri to evaluate.
   * @return true if the uri is absolute.
   */
  public static boolean isAbsolute(String uri) {
    return uri != null && !uri.isEmpty() && uri.startsWith("http");
  }


  /**
   * Encodes the value, preserving all reserved characters.. Values that are already pct-encoded are
   * ignored.
   *
   * @param value inspect.
   * @param charset to use.
   * @return a new String with the reserved characters preserved.
   */
  public static String encodeInternal(String value,
                                      Charset charset,
                                      boolean allowReservedCharacters) {
    /* value is encoded, we need to split it up and skip the parts that are already encoded */
    Matcher matcher = PCT_ENCODED_PATTERN.matcher(value);

    if (!matcher.find()) {
      return encodeChunk(value, charset, true);
    }

    int length = value.length();
    StringBuilder encoded = new StringBuilder(length + 8);
    int index = 0;
    do {
      /* split out the value before the encoded value */
      String before = value.substring(index, matcher.start());

      /* encode it */
      encoded.append(encodeChunk(before, charset, allowReservedCharacters));

      /* append the encoded value */
      encoded.append(matcher.group());

      /* update the string search index */
      index = matcher.end();
    } while (matcher.find());

    /* append the rest of the string */
    String tail = value.substring(index, length);
    encoded.append(encodeChunk(tail, charset, allowReservedCharacters));
    return encoded.toString();
  }

  /**
   * Encode a Uri Chunk, ensuring that all reserved characters are also encoded.
   *
   * @param value to encode.
   * @param charset to use.
   * @return an encoded uri chunk.
   */
  private static String encodeChunk(String value, Charset charset, boolean allowReserved) {
    if (isEncoded(value, charset)) {
      return value;
    }

    byte[] data = value.getBytes(charset);
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      for (byte b : data) {
        if (isUnreserved((char) b)) {
          bos.write(b);
        } else if (isReserved((char) b) && allowReserved) {
          bos.write(b);
        } else {
          pctEncode(b, bos);
        }
      }
      return new String(bos.toByteArray(), charset);
    } catch (IOException ioe) {
      throw new IllegalStateException("Error occurred during encoding of the uri: "
          + ioe.getMessage(), ioe);
    }
  }

  /**
   * Percent Encode the provided byte.
   *
   * @param data to encode
   * @param bos with the output stream to use.
   */
  private static void pctEncode(byte data, ByteArrayOutputStream bos) {
    bos.write('%');
    char hex1 = Character.toUpperCase(Character.forDigit((data >> 4) & 0xF, 16));
    char hex2 = Character.toUpperCase(Character.forDigit(data & 0xF, 16));
    bos.write(hex1);
    bos.write(hex2);
  }



  private static boolean isAlpha(int c) {
    return (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z');
  }

  private static boolean isDigit(int c) {
    return (c >= '0' && c <= '9');
  }

  private static boolean isGenericDelimiter(int c) {
    return (c == ':') || (c == '/') || (c == '?') || (c == '#') || (c == '[') || (c == ']')
        || (c == '@');
  }

  private static boolean isSubDelimiter(int c) {
    return (c == '!') || (c == '$') || (c == '&') || (c == '\'') || (c == '(') || (c == ')')
        || (c == '*') || (c == '+') || (c == ',') || (c == ';') || (c == '=');
  }

  private static boolean isUnreserved(int c) {
    return isAlpha(c) || isDigit(c) || c == '-' || c == '.' || c == '_' || c == '~';
  }

  private static boolean isReserved(int c) {
    return isGenericDelimiter(c) || isSubDelimiter(c);
  }

  private boolean isPchar(int c) {
    return isUnreserved(c) || isSubDelimiter(c) || c == ':' || c == '@';
  }

}
