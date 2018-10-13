/**
 * Copyright 2012-2018 The Feign Authors
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

package feign.reactive;

import feign.Contract;
import feign.Feign;
import feign.InvocationHandlerFactory;

abstract class ReactiveFeign {



  public static class Builder extends Feign.Builder {

    private Contract contract = new Contract.Default();

    /**
     * Extend the current contract to support Reactive Stream return types.
     *
     * @param contract to extend.
     * @return a Builder for chaining.
     */
    @Override
    public Builder contract(Contract contract) {
      this.contract = contract;
      return this;
    }

    /**
     * Build the Feign instance.
     *
     * @return a new Feign Instance.
     */
    @Override
    public Feign build() {
      if (!(this.contract instanceof ReactiveDelegatingContract)) {
        super.contract(new ReactiveDelegatingContract(this.contract));
      } else {
        super.contract(this.contract);
      }
      return super.build();
    }

    @Override
    public Feign.Builder doNotCloseAfterDecode() {
      throw new UnsupportedOperationException("Streaming Decoding is not supported.");
    }
  }
}
