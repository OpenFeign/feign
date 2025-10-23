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
package feign;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ConfidentialLoggerTest {

  @Test
  void testFilterHeaders() {

    /* Given */
    Map<String, Collection<String>> headers =
        Collections.singletonMap(
            "Authorization",
            Collections.singleton(
                "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ0dlhpald3X0tWSnZHY1B5N25mUlg4SjBpRDcxSTZEUWVFR0VjczJwbWxRIn0.eyJleHAiOjE3NDA1MDA0MzgsImlhdCI6MTc0MDUwMDQyOCwianRpIjoiMzljOTVhNzAtNDk4Yi00MWFjLTlmMTctOTA3Yjk4MmIzNDNkIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDozMjc5MC9yZWFsbXMvbWFzdGVyIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6ImMzZjU5ZjA3LWE4NTItNGY1Ny1hZjEwLTI0OGJjNjg4YWMxYSIsInR5cCI6IkJlYXJlciIsImF6cCI6ImZlaWduLWNsaWVudC1zZWNyZXQiLCJhY3IiOiIxIiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImRlZmF1bHQtcm9sZXMtbWFzdGVyIiwib2ZmbGluZV9hY2Nlc3MiLCJ1bWFfYXV0aG9yaXphdGlvbiJdfSwicmVzb3VyY2VfYWNjZXNzIjp7ImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoicHJvZmlsZSBlbWFpbCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwiY2xpZW50SG9zdCI6IjE3Mi4xNy4wLjEiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJzZXJ2aWNlLWFjY291bnQtZmVpZ24tY2xpZW50LXNlY3JldCIsImNsaWVudEFkZHJlc3MiOiIxNzIuMTcuMC4xIiwiY2xpZW50X2lkIjoiZmVpZ24tY2xpZW50LXNlY3JldCJ9.mcRl0ex7p4bPd-Kk1KwJoFOWYfkxwtEmAO9X9kdgGq4iCY6UUWGINqYXwI_D0QObclJ9J2ka9qCxo225MfV-zmza60IC3w6tfgsm7mnEZgec47GSoQjUqTLna4pDGdLq4c9QIedzkrhLqI9_qJi1V6iGYd6CNb6Y1u0G0QBoLejzHGVf5avxrlrRHTkGMUvphe7N0WAq5N9JjFrB6pqFsL1a9gMBkyThM6SpOwe1O2rXA07J7IgcL50AHU-4MxXRroz779GYObhm7o9RY7iPgs0BlBjVKxj75R8R57YNJo0LEPqBuCn5tAD7VJRPgCrM91Jfdv4X7mrg39JIndsyAw"));

    /* When */
    Map<String, Collection<String>> filteredHeaders = ConfidentialLogger.filterHeaders(headers);

    /* Then */
    assertThat(filteredHeaders).isNotNull().isNotEmpty().hasSize(1).containsKey("Authorization");
    assertThat(filteredHeaders.get("Authorization"))
        .isNotNull()
        .isNotEmpty()
        .hasSize(1)
        .contains("Bearer <access token>");
  }
}
