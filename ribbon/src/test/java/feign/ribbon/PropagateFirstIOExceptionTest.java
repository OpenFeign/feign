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
package feign.ribbon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import com.netflix.client.ClientException;

class PropagateFirstIOExceptionTest {

  @Test
  void propagatesNestedIOE() throws IOException {
    assertThatExceptionOfType(IOException.class).isThrownBy(() -> {

      RibbonClient.propagateFirstIOException(new ClientException(new IOException()));
    });
  }

  @Test
  void propagatesFirstNestedIOE() throws IOException {
    IOException exception = assertThrows(IOException.class, () -> RibbonClient
        .propagateFirstIOException(new ClientException(new IOException(new IOException()))));
    assertThat(exception).hasCauseInstanceOf(IOException.class);
  }

  /**
   * Happened in practice; a blocking observable wrapped the connect exception in a runtime
   * exception
   */
  @Test
  void propagatesDoubleNestedIOE() throws IOException {
    assertThatExceptionOfType(ConnectException.class).isThrownBy(() -> {

      RibbonClient.propagateFirstIOException(
          new ClientException(new RuntimeException(new ConnectException())));
    });
  }

  @Test
  void doesntPropagateWhenNotIOE() throws IOException {
    RibbonClient.propagateFirstIOException(
        new ClientException(new RuntimeException()));
  }
}
