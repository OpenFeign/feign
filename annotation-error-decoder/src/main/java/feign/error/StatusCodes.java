package feign.error;

public @interface StatusCodes {
    int[] codes();
    Class<? extends Exception> generate();
}
