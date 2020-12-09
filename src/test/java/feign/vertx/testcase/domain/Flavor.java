package feign.vertx.testcase.domain;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ice cream flavors.
 *
 * @author Alexei KLENIN
 */
public enum Flavor {
  STRAWBERRY, CHOCOLATE, BANANA, PISTACHIO, MELON, VANILLA;

  static public final String FLAVORS_JSON = Stream
        .of(Flavor.values())
        .map(flavor -> "\"" + flavor + "\"")
        .collect(Collectors.joining(", ", "[ ", " ]"));
}
