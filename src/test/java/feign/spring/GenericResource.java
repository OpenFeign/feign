package feign.spring;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

public interface GenericResource<DTO>
{

  @RequestMapping(value = "generic", method = RequestMethod.GET)
  public @ResponseBody DTO getData(@RequestBody DTO input);

}
