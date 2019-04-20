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

package feign.form.multipart;

import static lombok.AccessLevel.PRIVATE;

import feign.codec.EncodeException;
import lombok.experimental.FieldDefaults;
import lombok.val;

/**
 *
 * @author Artem Labazin
 */
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class ManyParametersWriter extends AbstractWriter {

  SingleParameterWriter parameterWriter = new SingleParameterWriter();

  @Override
  public boolean isApplicable (Object value) {
    if (value.getClass().isArray()) {
      Object[] values = (Object[]) value;
      return values.length > 0 && parameterWriter.isApplicable(values[0]);
    }
    if (!(value instanceof Iterable)) {
      return false;
    }
    val iterable = (Iterable<?>) value;
    val iterator = iterable.iterator();
    return iterator.hasNext() && parameterWriter.isApplicable(iterator.next());
  }

  @Override
  public void write (Output output, String boundary, String key, Object value) throws EncodeException {
    if (value.getClass().isArray()) {
      val objects = (Object[]) value;
      for (val object : objects) {
        parameterWriter.write(output, boundary, key, object);
      }
    } else if (value instanceof Iterable) {
      val iterable = (Iterable<?>) value;
      for (val object : iterable) {
        parameterWriter.write(output, boundary, key, object);
      }
    }
  }
}
