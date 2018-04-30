/**
 * Copyright (C) 2016 Marvin Herman Froeder (marvin@marvinformatics.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.mock;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import feign.Request;
import feign.Util;

public class RequestKey {

    public static class Builder {

        private final HttpMethod method;

        private final String url;

        private Map<String, Collection<String>> headers;

        private Charset charset;

        private byte[] body;

        private Builder(HttpMethod method, String url) {
            this.method = method;
            this.url = url;
        }

        public Builder headers(Map<String, Collection<String>> headers) {
            this.headers = headers;
            return this;
        }

        public Builder charset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder body(String body) {
            return body(body.getBytes(StandardCharsets.UTF_8));
        }

        public Builder body(byte[] body) {
            this.body = body;
            return this;
        }

        public RequestKey build() {
            return new RequestKey(this);
        }

    }

    public static Builder builder(HttpMethod method, String url) {
        return new Builder(method, url);
    }

    public static RequestKey create(Request request) {
        return new RequestKey(request);
    }

    private static String buildUrl(Request request) {
        try {
            return URLDecoder.decode(request.url(), Util.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private final HttpMethod method;

    private final String url;

    private final Map<String, Collection<String>> headers;

    private final Charset charset;

    private final byte[] body;

    private RequestKey(Builder builder) {
        this.method = builder.method;
        this.url = builder.url;
        this.headers = builder.headers;
        this.charset = builder.charset;
        this.body = builder.body;
    }

    private RequestKey(Request request) {
        this.method = HttpMethod.valueOf(request.method());
        this.url = buildUrl(request);
        this.headers = request.headers();
        this.charset = request.charset();
        this.body = request.body();
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, Collection<String>> getHeaders() {
        return headers;
    }

    public Charset getCharset() {
        return charset;
    }

    public byte[] getBody() {
        return body;
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, url);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        RequestKey other = (RequestKey) obj;
        return Objects.equals(other.method, method) && Objects.equals(other.url, url);
    }

    public boolean equalsExtended(Object obj) {
        if (equals(obj)) {
            RequestKey other = (RequestKey) obj;
            boolean headersEqual = other.headers == null || headers == null || Objects.equals(other.headers, headers);
            boolean charsetEqual = other.charset == null || charset == null || Objects.equals(other.charset, charset);
            boolean bodyEqual = other.body == null || body == null || Arrays.equals(other.body, body);
            return headersEqual && charsetEqual && bodyEqual;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("Request [%s %s: %s headers and %s]",
                method, url,
                headers == null ? "without" : "with " + headers.size(),
                charset == null ? "no charset" : "charset " + charset);
    }

}
