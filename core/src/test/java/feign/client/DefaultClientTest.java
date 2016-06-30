/*
 * Copyright 2015 Netflix, Inc.
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
package feign.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ProtocolException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.junit.Test;

import feign.Client;
import feign.Feign;
import feign.Response;
import feign.assertj.MockWebServerAssertions;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.SocketPolicy;

import static java.util.Arrays.asList;

import static org.hamcrest.core.Is.isA;
import static org.junit.Assert.assertEquals;

import static feign.Util.UTF_8;
import static feign.assertj.MockWebServerAssertions.assertThat;

/**
 * Tests client-specific behavior, such as ensuring Content-Length is sent when specified.
 */
public class DefaultClientTest extends AbstractClientTest {

    Client disableHostnameVerification =
            new Client.Default(TrustingSSLSocketFactory.get(), new HostnameVerifier() {
                @Override
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            });

    @Override
    public Client getClient() {
        return new Client.Default(TrustingSSLSocketFactory.get(), null);
    }

    @Test
    public void retriesFailedHandshake() throws IOException, InterruptedException {
        server.useHttps(TrustingSSLSocketFactory.get("localhost"), false);
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));
        server.enqueue(new MockResponse());

        TestInterface api = Feign.builder()
                .client(getClient())
                .target(TestInterface.class, "https://localhost:" + server.getPort());

        api.post("foo");
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void canOverrideSSLSocketFactory() throws IOException, InterruptedException {
        server.useHttps(TrustingSSLSocketFactory.get("localhost"), false);
        server.enqueue(new MockResponse());

        TestInterface api = Feign.builder()
                .client(getClient())
                .target(TestInterface.class, "https://localhost:" + server.getPort());

        api.post("foo");
    }

    @Test
    public void parsesRequestAndResponseDefault() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setBody("foo").addHeader("Foo: Bar"));

        TestInterface api =
                Feign.builder().target(TestInterface.class, "http://localhost:" + server.getPort());

        Response response = api.post("foo");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.reason()).isEqualTo("OK");
        assertThat(response.headers())
                .containsEntry("Content-Length", asList("3"))
                .containsEntry("Foo", asList("Bar"));
        assertThat(response.body().asInputStream())
                .hasContentEqualTo(new ByteArrayInputStream("foo".getBytes(UTF_8)));

        MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("POST")
                .hasPath("/?foo=bar&foo=baz&qux=")
                .hasHeaders("Foo: Bar", "Foo: Baz", "Qux: ", "Accept: */*", "Content-Length: 3")
                .hasBody("foo");
    }

    /**
     * We currently don't include the <a href="http://java.net/jira/browse/JERSEY-639">60-line
     * workaround</a> jersey uses to overcome the lack of support for PATCH. For now, prefer okhttp.
     *
     * @see java.net.HttpURLConnection#setRequestMethod
     */
    @Test
    @Override
    public void testPatch() throws IOException, InterruptedException {
        thrown.expectCause(isA(ProtocolException.class));

        TestInterface
                api =
                Feign.builder().target(TestInterface.class, "http://localhost:" + server.getPort());

        api.patch("foo");
    }


    @Test
    public void canOverrideHostnameVerifier() throws IOException, InterruptedException {
        server.useHttps(TrustingSSLSocketFactory.get("bad.example.com"), false);
        server.enqueue(new MockResponse());

        TestInterface api = Feign.builder()
                .client(disableHostnameVerification)
                .target(TestInterface.class, "https://localhost:" + server.getPort());

        api.post("foo");
    }

}
