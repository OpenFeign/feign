package feign;

/**
 * Zero or more {@code ResponseInterceptor} may be configured for purposes
 * such as verify or modify headers of response, verify the business status of decoded object.
 * No guarantees are given with regards to the order that interceptors are applied.
 * Once interceptors are applied, {@link ResponseInterceptor#beforeDecode(Response)} is called
 * before decode method called, {@link ResponseInterceptor#afterDecode(Object)} is called
 * after decode method called.
 */
public interface ResponseInterceptor {

    /**
     * Called for response before decode, add data on the supplied {@link Response} or doing
     * customized logic
     *
     * @param response
     * @return
     */
    void beforeDecode(Response response);

    /**
     * Called for response after decode, add data to decoded object or doing customized logic
     *
     * @param response
     * @return
     */
    void afterDecode(Object response);
}
