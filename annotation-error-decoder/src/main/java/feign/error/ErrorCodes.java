package feign.error;

public @interface ErrorCodes {
    int[] codes();
    Class<? extends Exception> generate();
}
