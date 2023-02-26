package feign.jaxrs;

import feign.Param.Expander;

final class DefaultValueExpander implements Expander {
	private String defaultValue;

	public DefaultValueExpander(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	@Override
	public String expand(Object value) {
		return value == null ? defaultValue : value.toString();

	}

}
