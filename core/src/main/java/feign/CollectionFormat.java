package feign;

/**
 * Various ways to encode collections in URL parameters.
 *
 * These specific cases are inspired by the
 * <a href="http://swagger.io/specification/">OpenAPI specification</a>.
 */
public enum CollectionFormat {
  /** Comma separated values, eg foo=bar,baz */
  CSV,
  /** Space separated values, eg foo=bar baz */
  SSV,
  /** Tab separated values, eg foo=bar[tab]baz */
  TSV,
  /** Values separated with the pipe (|) character, eg foo=bar|baz */
  PIPES,
  /** Parameter name repeated for each value, eg foo=bar&foo=baz */
  MULTI,
}
