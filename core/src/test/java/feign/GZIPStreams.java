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
package feign;

import com.google.common.io.InputSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

class GZIPStreams {
  static InputSupplier<GZIPInputStream> newInputStreamSupplier(InputSupplier<? extends InputStream> supplier) {
    return new GZIPInputStreamSupplier(supplier);
  }

  private static class GZIPInputStreamSupplier implements InputSupplier<GZIPInputStream> {
    private final InputSupplier<? extends InputStream> supplier;

    GZIPInputStreamSupplier(InputSupplier<? extends InputStream> supplier) {
      this.supplier = supplier;
    }

    @Override
    public GZIPInputStream getInput() throws IOException {
      return new GZIPInputStream(supplier.getInput());
    }
  }
}
