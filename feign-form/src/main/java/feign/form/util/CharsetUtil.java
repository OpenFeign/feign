/*
 * Copyright 2019 the original author or authors.
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

package feign.form.util;

import java.nio.charset.Charset;
import java.rmi.UnexpectedException;

/**
 *
 * @author Artem Labazin
 */
public final class CharsetUtil {

  public static final Charset UTF_8 = Charset.forName("UTF-8");

  private CharsetUtil () throws UnexpectedException {
    throw new UnexpectedException("It is not allowed to instantiate this class");
  }
}
