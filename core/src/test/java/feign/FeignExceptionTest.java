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
package feign;

import org.junit.Test;

public class FeignExceptionTest {

    @Test(expected = NullPointerException.class)
    public void nullRequestShouldThrowNPEwThrowable() {
        new Derived(404, "message", null, new Throwable());
    }

    @Test(expected = NullPointerException.class)
    public void nullRequestShouldThrowNPEwThrowableAndBytes() {
        new Derived(404, "message", null, new Throwable(), new byte[1]);
    }

    @Test(expected = NullPointerException.class)
    public void nullRequestShouldThrowNPE() {
        new Derived(404, "message", null);
    }

    @Test(expected = NullPointerException.class)
    public void nullRequestShouldThrowNPEwBytes() {
        new Derived(404, "message", null, new byte[1]);
    }

    static class Derived extends FeignException {

        public Derived(int status, String message, Request request, Throwable cause) {
            super(status, message, request, cause);
        }

        public Derived(int status, String message, Request request, Throwable cause, byte[] content) {
            super(status, message, request, cause, content);
        }

        public Derived(int status, String message, Request request) {
            super(status, message, request);
        }

        public Derived(int status, String message, Request request, byte[] content) {
            super(status, message, request, content);
        }
    }

}