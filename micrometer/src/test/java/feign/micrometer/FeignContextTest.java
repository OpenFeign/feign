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
package feign.micrometer;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import feign.Request;
import feign.RequestTemplate;
import feign.Target;

import static org.assertj.core.api.Assertions.assertThat;

public class FeignContextTest {

    @Test
    public void testRemoteServiceNotNull() {
        Request request = Request.create(Request.HttpMethod.GET, "http://example.com",
            new HashMap<>(), Request.Body.empty(), new RequestTemplate().feignTarget(
                new Target.HardCodedTarget<>(FeignContextTest.class, "RemoteServiceName", "http://example.com")));
        FeignContext feignContext = new FeignContext(request);
        assertThat(feignContext.getRemoteServiceName()).isEqualTo("RemoteServiceName");
        assertThat(feignContext.getRemoteServiceAddress()).isEqualTo("http://example.com:-1");
    }

    @Test
    public void testMalformedRemoteServiceURL() {
        Request request = Request.create(Request.HttpMethod.GET, "http://example.com",
            new HashMap<>(), Request.Body.empty(), new RequestTemplate().feignTarget(
                new Target.HardCodedTarget<>(FeignContextTest.class, "RemoteServiceName", "example.com")));
        FeignContext feignContext = new FeignContext(request);
        assertThat(feignContext.getRemoteServiceAddress()).isNull();
    }

}
