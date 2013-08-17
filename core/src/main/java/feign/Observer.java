package feign;

/**
 * An {@code Observer} is asynchronous equivalent to an {@code Iterator}.
 * <p/>
 * Observers receive results as they are
 * {@link feign.codec.IncrementalDecoder decoded} from an
 * {@link Response.Body http response body}. {@link #onNext(Object) onNext}
 * will be called for each incremental value of type {@code T} until
 * {@link feign.Subscription#unsubscribe()} is called or the response is finished.
 * <br>
 * {@link #onSuccess() onSuccess} or {@link #onFailure(Throwable)} onFailure}
 * will be called when the response is finished, but not both.
 * <br>
 * {@code Observer} can be used as an asynchronous alternative to a
 * {@code Collection}, or any other use where iterative response parsing is
 * worth the additional effort to implement this interface.
 * <br>
 * <br>
 * Here's an example of implementing {@code Observer}:
 * <br>
 * <pre>
 * Observer<Contributor> counter = new Observer<Contributor>() {
 *
 *   public int count;
 *
 *   &#064;Override public void onNext(Contributor element) {
 *     count++;
 *   }
 *
 *   &#064;Override public void onSuccess() {
 *     System.out.println("found " + count + " contributors");
 *   }
 *
 *   &#064;Override public void onFailure(Throwable cause) {
 *     System.err.println("sad face after contributor " + count);
 *   }
 * };
 * subscription = github.contributors("netflix", "feign", counter);
 * </pre>
 *
 * @param <T> expected value to decode incrementally from the http response.
 */
public interface Observer<T> {
  /**
   * Invoked as soon as new data is available. Could be invoked many times or
   * not at all.
   *
   * @param element next decoded element.
   */
  void onNext(T element);

  /**
   * Called when response processing completed successfully.
   */
  void onSuccess();

  /**
   * Called when response processing failed for any reason.
   * <br>
   * Common failure cases include {@link FeignException},
   * {@link java.io.IOException}, and {@link feign.codec.DecodeException}.
   * However, the cause could be a {@code Throwable} of any kind.
   *
   * @param cause the reason for the failure
   */
  void onFailure(Throwable cause);
}
