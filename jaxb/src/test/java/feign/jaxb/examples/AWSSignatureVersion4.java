/*
 * Copyright 2014 Netflix, Inc.
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
package feign.jaxb.examples;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import feign.Request;
import feign.RequestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map.Entry;
import java.util.TimeZone;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.hash.Hashing.sha256;
import static com.google.common.io.BaseEncoding.base16;
import static feign.Util.UTF_8;

// http://docs.aws.amazon.com/general/latest/gr/signature-version-4.html
public class AWSSignatureVersion4 implements Function<RequestTemplate, Request> {

    String region = "us-east-1";
    String service = "iam";
    String accessKey;
    String secretKey;

    public AWSSignatureVersion4(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Override public Request apply(RequestTemplate input) {
        input.header("Host", URI.create(input.url()).getHost());
        TreeMultimap<String, String> sortedLowercaseHeaders = TreeMultimap.create();
        for (String key : input.headers().keySet()) {
            sortedLowercaseHeaders.putAll(trimToLowercase.apply(key),
                    transform(input.headers().get(key), trimToLowercase));
        }

        String timestamp;
        synchronized (iso8601) {
            timestamp = iso8601.format(new Date());
        }

        String credentialScope = Joiner.on('/').join(timestamp.substring(0, 8), region, service, "aws4_request");

        input.query("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        input.query("X-Amz-Credential", accessKey + "/" + credentialScope);
        input.query("X-Amz-Date", timestamp);
        input.query("X-Amz-SignedHeaders", Joiner.on(';').join(sortedLowercaseHeaders.keySet()));

        String canonicalString = canonicalString(input, sortedLowercaseHeaders);
        String toSign = toSign(timestamp, credentialScope, canonicalString);

        byte[] signatureKey = signatureKey(secretKey, timestamp);
        String signature = base16().lowerCase().encode(hmacSHA256(toSign, signatureKey));

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

    static byte[] hmacSHA256(String data, byte[] key) {
        try {
            String algorithm = "HmacSHA256";
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data.getBytes(UTF_8));
        } catch (Exception e) {
            throw propagate(e);
        }
    }

    private static final String EMPTY_STRING_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private String canonicalString(RequestTemplate input, Multimap<String, String> sortedLowercaseHeaders) {
        StringBuilder canonicalRequest = new StringBuilder();
        // HTTPRequestMethod + '\n' +
        canonicalRequest.append(input.method()).append('\n');

        // CanonicalURI + '\n' +
        canonicalRequest.append(URI.create(input.url()).getPath()).append('\n');

        // CanonicalQueryString + '\n' +
        canonicalRequest.append(input.queryLine().substring(1));
        canonicalRequest.append('\n');

        // CanonicalHeaders + '\n' +
        for (Entry<String, Collection<String>> entry : sortedLowercaseHeaders.asMap().entrySet()) {
            canonicalRequest.append(entry.getKey()).append(':').append(Joiner.on(',').join(entry.getValue()))
                    .append('\n');
        }
        canonicalRequest.append('\n');

        // SignedHeaders + '\n' +
        canonicalRequest.append(Joiner.on(',').join(sortedLowercaseHeaders.keySet())).append('\n');

        // HexEncode(Hash(Payload))
        String bodyText =
                input.charset() != null && input.body() != null ? new String(input.body(), input.charset()) : null;
        if (bodyText != null) {
            canonicalRequest.append(base16().lowerCase().encode(sha256().hashString(bodyText, UTF_8).asBytes()));
        } else {
            canonicalRequest.append(EMPTY_STRING_HASH);
        }
        return canonicalRequest.toString();
    }

    private static final Function<String, String> trimToLowercase = new Function<String, String>() {
        public String apply(String in) {
            return in == null ? null : in.toLowerCase().trim();
        }
    };

    private String toSign(String timestamp, String credentialScope, String canonicalRequest) {
        StringBuilder toSign = new StringBuilder();
        // Algorithm + '\n' +
        toSign.append("AWS4-HMAC-SHA256").append('\n');
        // RequestDate + '\n' +
        toSign.append(timestamp).append('\n');
        // CredentialScope + '\n' +
        toSign.append(credentialScope).append('\n');
        // HexEncode(Hash(CanonicalRequest))
        toSign.append(base16().lowerCase().encode(sha256().hashString(canonicalRequest, UTF_8).asBytes()));
        return toSign.toString();
    }

    private static final SimpleDateFormat iso8601 = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");

    static {
        iso8601.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
}
