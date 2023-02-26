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
package feign.utils;

import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionUtilsTest {
  @Test
  public void rootCauseOfNullIsNull() {
    Throwable e = null;
    Throwable rootCause = ExceptionUtils.getRootCause(e);
    assertThat(rootCause).isNull();
  }

  @Test
  public void rootCauseIsSelf() {
    Throwable e = new Exception();
    Throwable rootCause = ExceptionUtils.getRootCause(e);
    assertThat(rootCause).isSameAs(e);
  }

  @Test
  public void rootCauseIsDifferent() {
    Throwable rootCause = new Exception();
    Throwable e = new Exception(rootCause);
    Throwable actualRootCause = ExceptionUtils.getRootCause(e);
    assertThat(actualRootCause).isSameAs(rootCause);
  }
}
