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
package feign.okhttp;

import feign.Client;
import feign.client.AbstractClientTest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import feign.Feign;
import feign.FeignException;
import feign.Headers;
import feign.Logger;
import feign.RequestLine;
import feign.Response;

import static feign.Util.UTF_8;
import static feign.assertj.MockWebServerAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

/** Tests client-specific behavior, such as ensuring Content-Length is sent when specified. */
public class OkHttpClientTest extends AbstractClientTest {

  @Override
  public Client getClient() {
    return new OkHttpClient();
  }

}
