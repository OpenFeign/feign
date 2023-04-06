/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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
			throw new BadRequest("Bad request having queryParams and formParams", template.request(), template.body(),
					template.headers());
		}
		HttpMethod method = HttpMethod.valueOf(template.method());
		switch (method) {
			case POST :
				if (queryParamsNotEmpty) {
					throw new MethodNotAllowed("The method specified in the request is not allowed!",
							template.request(), template.body(), template.headers());
				}
				break;
			default :
				if (formParamsNotEmpty) {
					throw new MethodNotAllowed("The method specified in the request is not allowed!",
							template.request(), template.body(), template.headers());
				}
				break;
		}

	}

}
