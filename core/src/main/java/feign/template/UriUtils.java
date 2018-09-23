/**
 * Copyright 2012-2018 The Feign Authors
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
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UriUtils {

  private static final String QUERY_RESERVED_CHARACTERS = "?/,=";
  private static final String PATH_RESERVED_CHARACTERS = "/=@:!$&\'(),;~";
  private static final Pattern PCT_ENCODED_PATTERN = Pattern.compile("%[0-9A-Fa-f][0-9A-Fa-f]");

  /**
   * Determines if the value is already pct-encoded.
   *
   * @param value to check.
   * @return {@literal true} if the value is already pct-encoded
   */
  public static boolean isEncoded(String value) {
    return PCT_ENCODED_PATTERN.matcher(value).matches();
  }

  /**
   * Uri Encode the value, using the default Charset. Already encoded values are skipped.
   *
   * @param value to encode.
   * @return the encoded value.
   */
  public static String encode(String value) {
    return encodeReserved(value, "", Util.UTF_8);
  }

  /**
   * Uri Encode the value. Already encoded values are skipped.
   *
   * @param value to encode.
   * @param charset to use.
   * @return the encoded value.
   */
  public static String encode(String value, Charset charset) {
    return encodeReserved(value, "", charset);
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
   * Uri Encode a Path Fragment.
   *
   * @param path containing the path fragment.
   * @param charset to use.
   * @return the encoded path fragment.
   */
  public static String pathEncode(String path, Charset charset) {
    return encodeReserved(path, PATH_RESERVED_CHARACTERS, charset);
  }

  /**
   * Uri Encode a Query Fragment.
   *
   * @param query containing the query fragment
   * @param charset to use.
   * @return the encoded query fragment.
   */
  public static String queryEncode(String query, Charset charset) {
    return encodeReserved(query, QUERY_RESERVED_CHARACTERS, charset);
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
   * Uri Encode a String using the provided charset.
   *
   * @param value to encode.
   * @param charset to use.
   * @return the encoded value.
   */
  private static String urlEncode(String value, Charset charset) {
    try {
      String encoded = URLEncoder.encode(value, charset.toString());

      /*
       * url encoding is not equivalent to URI encoding, there are few differences, namely dealing
       * with spaces, !, ', (, ), and ~ characters. we will need to manually process those values.
       */
      return encoded.replaceAll("\\+", "%20")
          .replaceAll("\\%21", "!")
          .replaceAll("\\%27", "'")
          .replaceAll("\\%28", "(")
          .replaceAll("\\%29", ")")
          .replaceAll("\\%7E", "~")
          .replaceAll("\\%2B", "+");

    } catch (UnsupportedEncodingException uee) {
      /* since the encoding is not supported, return the original value */
      return value;
    }
  }


  /**
   * Encodes the value, preserving all reserved characters.. Values that are already pct-encoded are
   * ignored.
   *
   * @param value inspect.
   * @param reserved characters to preserve.
   * @param charset to use.
   * @return a new String with the reserved characters preserved.
   */
  public static String encodeReserved(String value, String reserved, Charset charset) {
    /* value is encoded, we need to split it up and skip the parts that are already encoded */
    Matcher matcher = PCT_ENCODED_PATTERN.matcher(value);

    if (!matcher.find()) {
      return encodeChunk(value, reserved, charset);
    }

    int length = value.length();
    StringBuilder encoded = new StringBuilder(length + 8);
    int index = 0;
    do {
      /* split out the value before the encoded value */
      String before = value.substring(index, matcher.start());

      /* encode it */
      encoded.append(encodeChunk(before, reserved, charset));

      /* append the encoded value */
      encoded.append(matcher.group());

      /* update the string search index */
      index = matcher.end();
    } while (matcher.find());

    /* append the rest of the string */
    String tail = value.substring(index, length);
    encoded.append(encodeChunk(tail, reserved, charset));
    return encoded.toString();
  }

  /**
   * Encode a Uri Chunk, ensuring that all reserved characters are also encoded.
   *
   * @param value to encode.
   * @param reserved characters to evaluate.
   * @param charset to use.
   * @return an encoded uri chunk.
   */
  private static String encodeChunk(String value, String reserved, Charset charset) {
    StringBuilder encoded = null;
    int length = value.length();
    int index = 0;
    for (int i = 0; i < length; i++) {
      char character = value.charAt(i);
      if (reserved.indexOf(character) != -1) {
        if (encoded == null) {
          encoded = new StringBuilder(length + 8);
        }

        if (i != index) {
          /* we are in the middle of the value, so we need to encode mid string */
          encoded.append(urlEncode(value.substring(index, i), charset));
        }
        encoded.append(character);
        index = i + 1;
      }
    }

    /* if there are no reserved characters, encode the original value */
    if (encoded == null) {
      return urlEncode(value, charset);
    }

    /* encode the rest of the string */
    if (index < length) {
      encoded.append(urlEncode(value.substring(index, length), charset));
    }
    return encoded.toString();

  }
}
