/**
 * 
 */
package feign;

import feign.codec.ErrorDecoder;

/**
 * Interface responsible for interpreting HTTP status codes from Responses.
 * 
 * @author Nicholas Blair
 */
public interface StatusInterpreter {

  /**
   * Implementations should return true if the status code suggests a Response is interpretable.
   * When false is returned, it means the statusCode should result in a Exception being thrown (that
   * the {@link ErrorDecoder} will handle).
   * 
   * @see ErrorDecoder
   * @param statusCode
   * @return true if the response is interpretable.
   */
  public boolean isInterpretableResponse(int statusCode);
  
  /**
   * Default {@link StatusInterpreter}.
   */
  public static class Default implements StatusInterpreter {
    /**
     * {@inheritDoc}
     * Returns true for all 200 classs= statusCodes.
     */
    @Override
    public boolean isInterpretableResponse(int statusCode) {
      return statusCode >= 200 && statusCode < 300;
    }
  }
  /**
   * Optional {@link StatusInterpreter} that behaves like {@link Default} 
   * but additionally handles 404.
   */
  public static class Include404 extends Default implements StatusInterpreter {
    /**
     * {@inheritDoc}
     * Returns true for {@link Default#isInterpretableResponse(int)} OR 404.
     */
    @Override
    public boolean isInterpretableResponse(int statusCode) {
      return super.isInterpretableResponse(statusCode) || statusCode == 404;
    }
  }
}
