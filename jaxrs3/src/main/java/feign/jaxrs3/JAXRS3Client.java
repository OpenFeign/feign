/*
 * Copyright 2012-2023 The Feign Authors
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
package feign.jaxrs3;

import feign.jaxrs2.JAXRSClient;
import jakarta.ws.rs.client.ClientBuilder;

/**
 * This module directs Feign's http requests to jakarta.ws.rs.client.Client . Ex:
 *
 * <pre>
 * GitHub github =
 *     Feign.builder().client(new JaxRSClient()).target(GitHub.class, "https://api.github.com");
 * </pre>
 */
public class JAXRS3Client extends JAXRSClient {

  public JAXRS3Client() {
    this(ClientBuilder.newBuilder());
  }

  public JAXRS3Client(ClientBuilder clientBuilder) {
    super(clientBuilder);
  }

}

