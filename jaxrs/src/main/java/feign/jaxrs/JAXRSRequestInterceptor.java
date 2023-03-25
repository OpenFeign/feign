package feign.jaxrs;

import feign.FeignException.BadRequest;
import feign.FeignException.MethodNotAllowed;
import feign.Request.HttpMethod;
import feign.RequestInterceptor;
import feign.RequestTemplate;

public final class JAXRSRequestInterceptor implements RequestInterceptor {

  @Override
  public void apply(RequestTemplate template) {
    boolean queryParamsNotEmpty = !template.queries().isEmpty(),
        formParamsNotEmpty = !template.methodMetadata().formParams().isEmpty();
    if (formParamsNotEmpty && queryParamsNotEmpty) {
      throw new BadRequest("Bad request having queryParams and formParams",
          template.request(), template.body(), template.headers());
    }
    HttpMethod method = HttpMethod.valueOf(template.method());
    switch (method) {
      case POST:
        if (queryParamsNotEmpty) {
          throw new MethodNotAllowed("The method specified in the request is not allowed!",
              template.request(), template.body(), template.headers());
        }
        break;
      default:
        if (formParamsNotEmpty) {
          throw new MethodNotAllowed("The method specified in the request is not allowed!",
              template.request(), template.body(), template.headers());
        }
        break;
    }

  }

}
