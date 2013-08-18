/*
 * Copyright 2013 Netflix, Inc.
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
package feign.codec;

import feign.Observer;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;

public class StringIncrementalDecoder implements IncrementalDecoder.TextStream<String> {
  private static final StringDecoder STRING_DECODER = new StringDecoder();

  @Override
  public void decode(
      Reader reader, Type type, Observer<? super String> observer, AtomicBoolean subscribed)
      throws IOException {
    observer.onNext(STRING_DECODER.decode(reader, type));
  }
}
