/**
 * Copyright 2012-2019 The Feign Authors
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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UriUtils {


  // private static final String QUERY_RESERVED_CHARACTERS = "=";
  // private static final String PATH_RESERVED_CHARACTERS = "/=@:!$&\'(),;~";
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
    return encodeReserved(value, FragmentType.URI, Util.UTF_8);
  }

  /**
   * Uri Encode the value. Already encoded values are skipped.
   *
   * @param value to encode.
   * @param charset to use.
   * @return the encoded value.
   */
  public static String encode(String value, Charset charset) {
    return encodeReserved(value, FragmentType.URI, charset);
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
    return encodeReserved(path, FragmentType.PATH_SEGMENT, charset);

    /*
     * path encoding is not equivalent to query encoding, there are few differences, namely dealing
     * with spaces, !, ', (, ), and ~ characters. we will need to manually process those values.
     */
    // return encoded.replaceAll("\\+", "%20");
  }

  /**
   * Uri Encode a Query Fragment.
   *
   * @param query containing the query fragment
   * @param charset to use.
   * @return the encoded query fragment.
   */
  public static String queryEncode(String query, Charset charset) {
    return encodeReserved(query, FragmentType.QUERY, charset);

    /* spaces will be encoded as 'plus' symbols here, we want them pct-encoded */
    // return encoded.replaceAll("\\+", "%20");
  }

  /**
   * Uri Encode a Query Parameter name or value.
   *
   * @param queryParam containing the query parameter.
   * @param charset to use.
   * @return the encoded query fragment.
   */
  public static String queryParamEncode(String queryParam, Charset charset) {
    return encodeReserved(queryParam, FragmentType.QUERY_PARAM, charset);
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
   * @param type identifying which uri fragment rules to apply.
   * @param charset to use.
   * @return a new String with the reserved characters preserved.
   */
  public static String encodeReserved(String value, FragmentType type, Charset charset) {
    /* value is encoded, we need to split it up and skip the parts that are already encoded */
    Matcher matcher = PCT_ENCODED_PATTERN.matcher(value);

    if (!matcher.find()) {
      return encodeChunk(value, type, charset);
    }

    int length = value.length();
    StringBuilder encoded = new StringBuilder(length + 8);
    int index = 0;
    do {
      /* split out the value before the encoded value */
      String before = value.substring(index, matcher.start());

      /* encode it */
      encoded.append(encodeChunk(before, type, charset));

      /* append the encoded value */
      encoded.append(matcher.group());

      /* update the string search index */
      index = matcher.end();
    } while (matcher.find());

    /* append the rest of the string */
    String tail = value.substring(index, length);
    encoded.append(encodeChunk(tail, type, charset));
    return encoded.toString();
  }

  /**
   * Encode a Uri Chunk, ensuring that all reserved characters are also encoded.
   *
   * @param value to encode.
   * @param type identifying which uri fragment rules to apply.
   * @param charset to use.
   * @return an encoded uri chunk.
   */
  private static String encodeChunk(String value, FragmentType type, Charset charset) {
    byte[] data = value.getBytes(charset);
    ByteArrayOutputStream encoded = new ByteArrayOutputStream();

    for (byte b : data) {
      if (type.isAllowed(b)) {
        encoded.write(b);
      } else {
        /* percent encode the byte */
        pctEncode(b, encoded);
      }
    }
    return new String(encoded.toByteArray());
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

  enum FragmentType {

    URI {
      @Override
      boolean isAllowed(int c) {
        return isUnreserved(c);
      }
    },
    RESERVED {
      @Override
      boolean isAllowed(int c) {
        return isUnreserved(c) || isReserved(c);
      }
    },
    PATH_SEGMENT {
      @Override
      boolean isAllowed(int c) {
        return this.isPchar(c) || (c == '/');
      }
    },
    QUERY {
      @Override
      boolean isAllowed(int c) {
        /* although plus signs are allowed, their use is inconsistent. force encoding */
        if (c == '+') {
          return false;
        }

        return this.isPchar(c) || c == '/' || c == '?';
      }
    },
    QUERY_PARAM {
      @Override
      boolean isAllowed(int c) {
        /* explicitly encode equals, ampersands, questions */
        if (c == '=' || c == '&' || c == '?') {
          return false;
        }
        return QUERY.isAllowed(c);
      }
    };

    abstract boolean isAllowed(int c);

    protected boolean isAlpha(int c) {
      return (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z');
    }

    protected boolean isDigit(int c) {
      return (c >= '0' && c <= '9');
    }

    protected boolean isGenericDelimiter(int c) {
      return (c == ':') || (c == '/') || (c == '?') || (c == '#') || (c == '[') || (c == ']')
          || (c == '@');
    }

    protected boolean isSubDelimiter(int c) {
      return (c == '!') || (c == '$') || (c == '&') || (c == '\'') || (c == '(') || (c == ')')
          || (c == '*') || (c == '+') || (c == ',') || (c == ';') || (c == '=');
    }

    protected boolean isUnreserved(int c) {
      return this.isAlpha(c) || this.isDigit(c) || c == '-' || c == '.' || c == '_' || c == '~';
    }

    protected boolean isReserved(int c) {
      return this.isGenericDelimiter(c) || this.isSubDelimiter(c);
    }

    protected boolean isPchar(int c) {
      return this.isUnreserved(c) || this.isSubDelimiter(c) || c == ':' || c == '@';
    }

  }
}
