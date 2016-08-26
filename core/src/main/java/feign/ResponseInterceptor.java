/*
 * Copyright 2016 Netflix, Inc.
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
package feign;

/**
 * Zero or more {@code ResponseInterceptors} may be configured for purposes such as publishing stats to external
 * monitoring systems. No guarantees are give with regards to the order that interceptors are applied.
 */
public interface ResponseInterceptor {

    /**
     * Called for every response received per request.
     */
    void apply(Request request, Response response, MethodMetadata methodMetadata);
}
