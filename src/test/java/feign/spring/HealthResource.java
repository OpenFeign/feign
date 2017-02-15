package feign.spring;

import java.util.MissingResourceException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/health", produces = "text/html")
public interface HealthResource extends GenericResource<Data>
{

  @RequestMapping(method = RequestMethod.GET)
  public @ResponseBody String getStatus();

  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  public void check(
      @PathVariable("id") String campaignId,
      @RequestParam(value = "deep", defaultValue = "false") boolean deepCheck);

  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  public void check(
      @PathVariable("id") String campaignId,
      @RequestParam(value = "deep", defaultValue = "false") boolean deepCheck,
      @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun);

  @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "This customer is not found in the system")
  @ExceptionHandler(MissingResourceException.class)
  void missingResourceExceptionHandler();

}
