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

import feign.auth.oauth2.mock.domain.*;
import java.util.Collection;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/icecream")
public class IcecreamController {
  private final OrderGenerator orderGenerator = new OrderGenerator();

  @GetMapping("/flavors")
  public Collection<Flavor> getAvailableFlavors() {
    return List.of(Flavor.values());
  }

  @GetMapping("/mixins")
  public Collection<Mixin> getAvailableMixins() {
    return List.of(Mixin.values());
  }

  @PostMapping("/orders")
  public Bill makeOrder(@RequestBody IceCreamOrder order) {
    return Bill.makeBill(order);
  }

  @GetMapping("/orders/{orderId}")
  public IceCreamOrder findOrder(@PathVariable int orderId) {
    return orderGenerator.generate();
  }

  @PostMapping("/bills/pay")
  public void payBill(@RequestBody Bill bill) {}
}
