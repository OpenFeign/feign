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
package feign.vertx.testcase.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Bill for consumed ice cream.
 *
 * @author Alexei KLENIN
 */
public class Bill {
  private static final Map<Integer, Float> PRICES =
      Map.of(
          1, (float) 2.00, // two euros for one ball (expensive!)
          3, (float) 2.85, // 2.85€ for 3 balls
          5, (float) 4.30, // 4.30€ for 5 balls
          7, (float) 5); // only five euros for seven balls! Wow

  private static final float MIXIN_PRICE = (float) 0.6; // price per mixin

  private Float price;

  public Bill() {}

  public Bill(final Float price) {
    this.price = price;
  }

  public Float getPrice() {
    return price;
  }

  public void setPrice(final Float price) {
    this.price = price;
  }

  /**
   * Makes a bill from an order.
   *
   * @param order ice cream order
   * @return bill
   */
  public static Bill makeBill(final IceCreamOrder order) {
    int nbBalls = order.getBalls().values().stream().mapToInt(Integer::intValue).sum();
    Float price = PRICES.get(nbBalls) + order.getMixins().size() * MIXIN_PRICE;
    return new Bill(price);
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof Bill)) {
      return false;
    }

    final Bill another = (Bill) other;
    return Objects.equals(price, another.price);
  }

  @Override
  public int hashCode() {
    return Objects.hash(price);
  }
}
