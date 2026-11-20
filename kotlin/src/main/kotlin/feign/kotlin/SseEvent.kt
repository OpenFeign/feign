/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.kotlin

import java.time.Duration

/**
 * A single Server-Sent Event as dispatched by the server.
 *
 * @property data payload of the event's `data` field, joined across multiple `data` lines the way
 *   the SSE spec prescribes; null when the frame carried no data at all, which happens for
 *   comment-only keep-alive frames.
 * @property event value of the `event` field, or null when the server did not name the event.
 * @property id value of the `id` field, or null when the server did not send one.
 * @property retry reconnection hint sent by the server in milliseconds, or null.
 * @property comment text of the frame's comment lines, or null.
 */
data class SseEvent(
    val data: String?,
    val event: String? = null,
    val id: String? = null,
    val retry: Duration? = null,
    val comment: String? = null,
)
