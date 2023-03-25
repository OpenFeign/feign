package feign.jaxrs;

import feign.Param.Expander;
import jakarta.ws.rs.core.Cookie;

final class CookieParamExpander extends AbstractParameterValidator<Cookie> implements Expander {
  private String name;

  public CookieParamExpander(String name) {
    this.name = name;
  }

  @Override
  public String expand(Object value) throws IllegalArgumentException {

    if (!super.test(value)) {
      throw new IllegalArgumentException();
    }
    if (value instanceof Cookie) {
      String name = ((Cookie) value).getName();
      if (!this.name.equals(name)) {
        throw new IllegalArgumentException(String
            .format("The Cookie's name '%s' do not match with CookieParam's value '%s'!", name,
                this.name));
      }
      return ((Cookie) value).getValue();
    } else {
      return (String) value;
    }
  }
}
