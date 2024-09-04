package feign.vertx.testcase.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Give me some ice-cream! :p
 *
 * @author Alexei KLENIN
 */
public class IceCreamOrder {
  private final int id;                       // order id
  private final Map<Flavor, Integer> balls;   // how much balls of flavor
  private final Set<Mixin> mixins;            // and some mixins ...
  private Instant orderTimestamp;             // and give it to me right now !

  IceCreamOrder() {
    this(Instant.now());
  }

  IceCreamOrder(final Instant orderTimestamp) {
    this.id = ThreadLocalRandom.current().nextInt();
    this.balls = new HashMap<>();
    this.mixins = new LinkedHashSet<>();
    this.orderTimestamp = orderTimestamp;
  }

  IceCreamOrder addBall(final Flavor ballFlavor) {
    final Integer ballCount = balls.containsKey(ballFlavor)
        ? balls.get(ballFlavor) + 1
        : 1;
    balls.put(ballFlavor, ballCount);
    return this;
  }

  IceCreamOrder addMixin(final Mixin mixin) {
    mixins.add(mixin);
    return this;
  }

  IceCreamOrder withOrderTimestamp(final Instant orderTimestamp) {
    this.orderTimestamp = orderTimestamp;
    return this;
  }

  public int getId() {
    return id;
  }

  public Map<Flavor, Integer> getBalls() {
    return balls;
  }

  public Set<Mixin> getMixins() {
    return mixins;
  }

  public Instant getOrderTimestamp() {
    return orderTimestamp;
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof IceCreamOrder)) {
      return false;
    }

    final IceCreamOrder another = (IceCreamOrder) other;
    return id == another.id
        && Objects.equals(balls, another.balls)
        && Objects.equals(mixins, another.mixins)
        && Objects.equals(orderTimestamp, another.orderTimestamp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, balls, mixins, orderTimestamp);
  }

  @Override
  public String toString() {
    return "IceCreamOrder{"
        + " id=" + id
        + ", balls=" + balls
        + ", mixins=" + mixins
        + ", orderTimestamp=" + orderTimestamp
        + '}';
  }
}
