/*
 * Copyright 2013 Netflix, Inc.
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
package feign.sax.examples;

import java.net.URI;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import feign.Request;
import feign.RequestTemplate;

import static feign.Util.UTF_8;

// http://docs.aws.amazon.com/general/latest/gr/signature-version-4.html
public class AWSSignatureVersion4 {

  private static final String
      EMPTY_STRING_HASH =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
  private static final SimpleDateFormat iso8601 = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
  static {
    iso8601.setTimeZone(TimeZone.getTimeZone("GMT"));
  }
  String region = "us-east-1";
  String service = "iam";
  String accessKey;
  String secretKey;

  public AWSSignatureVersion4(String accessKey, String secretKey) {
    this.accessKey = accessKey;
    this.secretKey = secretKey;
  }

  static byte[] hmacSHA256(String data, byte[] key) {
    try {
      String algorithm = "HmacSHA256";
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(key, algorithm));
      return mac.doFinal(data.getBytes(UTF_8));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String canonicalString(RequestTemplate input, String host) {
    StringBuilder canonicalRequest = new StringBuilder();
    // HTTPRequestMethod + '\n' +
    canonicalRequest.append(input.method()).append('\n');

    // CanonicalURI + '\n' +
    canonicalRequest.append(URI.create(input.url()).getPath()).append('\n');

    // CanonicalQueryString + '\n' +
    canonicalRequest.append(input.queryLine().substring(1));
    canonicalRequest.append('\n');

    // CanonicalHeaders + '\n' +
    canonicalRequest.append("host:").append(host).append('\n');

    canonicalRequest.append('\n');

    // SignedHeaders + '\n' +
    canonicalRequest.append("host").append('\n');

    // HexEncode(Hash(Payload))
    String bodyText =
        input.charset() != null && input.body() != null ? new String(input.body(), input.charset())
                                                        : null;
    if (bodyText != null) {
      canonicalRequest.append(hex(sha256(bodyText)));
    } else {
      canonicalRequest.append(EMPTY_STRING_HASH);
    }
    return canonicalRequest.toString();
  }

  private static String toSign(String timestamp, String credentialScope, String canonicalRequest) {
    StringBuilder toSign = new StringBuilder();
    // Algorithm + '\n' +
    toSign.append("AWS4-HMAC-SHA256").append('\n');
    // RequestDate + '\n' +
    toSign.append(timestamp).append('\n');
    // CredentialScope + '\n' +
    toSign.append(credentialScope).append('\n');
    // HexEncode(Hash(CanonicalRequest))
    toSign.append(hex(sha256(canonicalRequest)));
    return toSign.toString();
  }


  private static String hex(byte[] data) {
    StringBuilder result = new StringBuilder(data.length * 2);
    for (byte b : data) {
      result.append(String.format("%02x", b & 0xff));
    }
    return result.toString();
  }

  static byte[] sha256(String data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(data.getBytes(UTF_8));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public Request apply(RequestTemplate input) {
    if (!input.headers().isEmpty()) {
      throw new UnsupportedOperationException("headers not supported");
    }
    if (input.body() != null) {
      throw new UnsupportedOperationException("body not supported");
    }

    String host = URI.create(input.url()).getHost();

    String timestamp;
    synchronized (iso8601) {
      timestamp = iso8601.format(new Date());
    }

    String
        credentialScope =
        String.format("%s/%s/%s/%s", timestamp.substring(0, 8), region, service, "aws4_request");

    input.query("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
    input.query("X-Amz-Credential", accessKey + "/" + credentialScope);
    input.query("X-Amz-Date", timestamp);
    input.query("X-Amz-SignedHeaders", "host");
    input.header("Host", host);

    String canonicalString = canonicalString(input, host);
    String toSign = toSign(timestamp, credentialScope, canonicalString);

    byte[] signatureKey = signatureKey(secretKey, timestamp);
    String signature = hex(hmacSHA256(toSign, signatureKey));

    input.query("X-Amz-Signature", signature);

    return input.request();
  }

  byte[] signatureKey(String secretKey, String timestamp) {
    byte[] kSecret = ("AWS4" + secretKey).getBytes(UTF_8);
    byte[] kDate = hmacSHA256(timestamp.substring(0, 8), kSecret);
    byte[] kRegion = hmacSHA256(region, kDate);
    byte[] kService = hmacSHA256(service, kRegion);
    byte[] kSigning = hmacSHA256("aws4_request", kService);
    return kSigning;
  }
}
