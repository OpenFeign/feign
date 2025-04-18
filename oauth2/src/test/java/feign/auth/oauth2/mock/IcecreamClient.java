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
package feign.auth.oauth2.mock;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.auth.oauth2.mock.domain.Bill;
import feign.auth.oauth2.mock.domain.Flavor;
import feign.auth.oauth2.mock.domain.IceCreamOrder;
import feign.auth.oauth2.mock.domain.Mixin;
import java.util.Collection;

@Headers({"Accept: application/json"})
public interface IcecreamClient {

  @RequestLine("GET /icecream/flavors")
  Collection<Flavor> getAvailableFlavors();

  @RequestLine("GET /icecream/mixins")
  Collection<Mixin> getAvailableMixins();

  @RequestLine("POST /icecream/orders")
  @Headers("Content-Type: application/json")
  Bill makeOrder(IceCreamOrder order);

  @RequestLine("GET /icecream/orders/{orderId}")
  IceCreamOrder findOrder(@Param("orderId") int orderId);

  @RequestLine("POST /icecream/bills/pay")
  @Headers("Content-Type: application/json")
  void payBill(Bill bill);
}
