/**
 * Copyright 2012-2018 Dariusz Wawer
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

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public class TestUtil {

  public static Matcher<?> bodyMatcher(String string) {
    return new BaseMatcher<Exception>() {
      @Override
      public boolean matches(Object item) {
        if (item instanceof FeignException) {
          FeignException e = (FeignException) item;
          return string.equals(e.body());
        }
        return false;
      }

      @Override
      public void describeTo(Description description) {
        // empty on purpose
      }
    };
  }

}
