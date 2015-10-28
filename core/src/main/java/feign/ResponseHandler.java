package feign;

public interface ResponseHandler {

  /**
   * If this handler can handle the given {@link Response}, returns the decoded response into {@code result} and
   * returns true; else returns false. Throws in case of internal errors or in case the given response should be
   * surfaced as an error, e.g., when the response has error code 404.
   */
  boolean handle(Response response, MethodMetadata metadata, ResultHolder result) throws Throwable;

  /**
   * Mutable Object wrapper for holding/returning the result of a handler; null is a valid result.
   */
  class ResultHolder {
    private Object result = null;

    public Object getResult() {
      return result;
    }

    public void setResult(Object result) {
      this.result = result;
    }
  }
}
