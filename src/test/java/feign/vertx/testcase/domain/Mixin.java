package feign.vertx.testcase.domain;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ice cream mix-ins.
 *
 * @author Alexei KLENIN
 */
public enum Mixin {
  COOKIES, MNMS, CHOCOLATE_SIROP, STRAWBERRY_SIROP, NUTS, RAINBOW;

  public static final String MIXINS_JSON = Stream
      .of(Mixin.values())
      .map(flavor -> "\"" + flavor + "\"")
      .collect(Collectors.joining(", ", "[ ", " ]"));
}
