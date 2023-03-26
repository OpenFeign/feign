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

import static feign.Util.checkState;
import static feign.Util.emptyToNull;
import static feign.Util.removeValues;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import feign.AlwaysEncodeBodyContract;
import feign.MethodMetadata;
import feign.Param.Expander;
import feign.Request;
import feign.jaxrs.AbstractParameterValidator.DefaultParameterExpander;

/**
 * Please refer to the
 * <a href="https://github.com/Netflix/feign/tree/master/feign-jaxrs">Feign
 * JAX-RS README</a>.
 */
public class JAXRSContract extends AlwaysEncodeBodyContract {

	static final String ACCEPT = "Accept";
	static final String CONTENT_TYPE = "Content-Type";
	// private static final Class<?>[] ALL_CONTEXT_TYPE = { Application.class,
	// UriInfo.class, Request.class,
	// HttpHeaders.class, SecurityContext.class, Providers.class };

	// Protected so unittest can call us
	// XXX: Should parseAndValidateMetadata(Class, Method) be public instead? The
	// old deprecated
	// parseAndValidateMetadata(Method) was public..
	@Override
	public MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {

		return super.parseAndValidateMetadata(targetType, method);
	}

	public JAXRSContract() {
		super.registerClassAnnotation(Path.class, (path, data) -> {
			if (path != null && !path.value().isEmpty()) {
				String pathValue = path.value();
				if (!pathValue.startsWith("/")) {
					pathValue = "/" + pathValue;
				}
				if (pathValue.endsWith("/")) {
					// Strip off any trailing slashes, since the template has already had slashes
					// appropriately
					// added
					pathValue = pathValue.substring(0, pathValue.length() - 1);
				}
				// jax-rs allows whitespace around the param name, as well as an optional regex.
				// The
				// contract
				// should
				// strip these out appropriately.
				pathValue = pathValue.replaceAll("\\{\\s*(.+?)\\s*(:.+?)?\\}", "\\{$1\\}");
				data.template().uri(pathValue);
			}
		});
		super.registerClassAnnotation(Consumes.class, this::handleConsumesAnnotation);
		super.registerClassAnnotation(Produces.class, this::handleProducesAnnotation);
		super.registerClassAnnotation(Encoded.class, this::handleEncodedAnnotation);

		registerMethodAnnotation(methodAnnotation -> {
			final Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
			final HttpMethod http = annotationType.getAnnotation(HttpMethod.class);
			return http != null;
		}, (methodAnnotation, data) -> {
			final Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
			final HttpMethod http = annotationType.getAnnotation(HttpMethod.class);
			checkState(data.template().method() == null, "Method %s contains multiple HTTP methods. Found: %s and %s",
					data.configKey(), data.template().method(), http.value());
			data.template().method(Request.HttpMethod.valueOf(http.value()));
		});

		super.registerMethodAnnotation(Path.class, (path, data) -> {
			final String pathValue = emptyToNull(path.value());
			if (pathValue == null) {
				return;
			}
			String methodAnnotationValue = path.value();
			if (!methodAnnotationValue.startsWith("/") && !data.template().url().endsWith("/")) {
				methodAnnotationValue = "/" + methodAnnotationValue;
			}
			// jax-rs allows whitespace around the param name, as well as an optional regex.
			// The contract
			// should
			// strip these out appropriately.
			methodAnnotationValue = methodAnnotationValue.replaceAll("\\{\\s*(.+?)\\s*(:.+?)?\\}", "\\{$1\\}");
			data.template().uri(methodAnnotationValue, true);
		});
		super.registerMethodAnnotation(Consumes.class, this::handleConsumesAnnotation);
		super.registerMethodAnnotation(Produces.class, this::handleProducesAnnotation);
		super.registerMethodAnnotation(Encoded.class, this::handleEncodedAnnotation);
		// trying to minimize the diff
		registerParamAnnotations();
	}

	private void handleProducesAnnotation(Produces produces, MethodMetadata data) {
		final String[] serverProduces = removeValues(produces.value(), mediaType -> emptyToNull(mediaType) == null,
				String.class);
		checkState(serverProduces.length > 0, "Produces.value() was empty on %s", data.configKey());
		data.template().header(ACCEPT, Collections.emptyList()); // remove any previous produces
		data.template().header(ACCEPT, serverProduces);
	}

	private void handleConsumesAnnotation(Consumes consumes, MethodMetadata data) {
		final String[] serverConsumes = removeValues(consumes.value(), mediaType -> emptyToNull(mediaType) == null,
				String.class);
		checkState(serverConsumes.length > 0, "Consumes.value() was empty on %s", data.configKey());
		data.template().header(CONTENT_TYPE, serverConsumes);
	}

	private void handleEncodedAnnotation(Encoded encoded, MethodMetadata data) {
		data.template().setDisableDecodingForAll(true);
	}

	protected void indexToExpander(MethodMetadata data, int paramIndex, Expander expander) {
		Map<Integer, Expander> indexToExpander = new HashMap<>();
		indexToExpander.put(paramIndex, expander);
		if (data.indexToExpander() != null) {
			data.indexToExpander().putAll(indexToExpander);
		} else {
			data.indexToExpander(indexToExpander);
		}
	}

	// @DefaultValue annotation must be in end of other parameter annotation
	protected void registerParamAnnotations() {
		{

			registerParameterAnnotation(PathParam.class, (param, data, paramIndex) -> {
				final String name = param.value();
				checkState(emptyToNull(name) != null, "PathParam.value() was empty on parameter %s", paramIndex);
				nameParam(data, name, paramIndex);
				indexToExpander(data, paramIndex, new DefaultParameterExpander());
			});
			registerParameterAnnotation(QueryParam.class, (param, data, paramIndex) -> {

				final String name = param.value();
				checkState(emptyToNull(name) != null, "QueryParam.value() was empty on parameter %s", paramIndex);
				final String query = addTemplatedParam(name);
				data.template().query(name, query);
				nameParam(data, name, paramIndex);
				indexToExpander(data, paramIndex, new DefaultParameterExpander());
			});
			registerParameterAnnotation(HeaderParam.class, (param, data, paramIndex) -> {
				final String name = param.value();
				checkState(emptyToNull(name) != null, "HeaderParam.value() was empty on parameter %s", paramIndex);
				final String header = addTemplatedParam(name);
				data.template().header(name, header);
				nameParam(data, name, paramIndex);
				indexToExpander(data, paramIndex, new DefaultParameterExpander());
			});
			registerParameterAnnotation(FormParam.class, (param, data, paramIndex) -> {
				final String name = param.value();
				checkState(emptyToNull(name) != null, "FormParam.value() was empty on parameter %s", paramIndex);
				data.formParams().add(name);
				nameParam(data, name, paramIndex);
				indexToExpander(data, paramIndex, new DefaultParameterExpander());
			});

			registerParameterAnnotation(DefaultValue.class, (param, data, paramIndex) -> {
				final String defaultValue = param.value();
				indexToExpander(data, paramIndex, new DefaultValueExpander(defaultValue,
						Optional.ofNullable(data.indexToExpander()).map(v -> v.get(paramIndex))));

			});
			registerParameterAnnotation(MatrixParam.class, (param, data, paramIndex) -> {
				final String name = param.value();
				checkState(emptyToNull(name) != null, "MatrixParam.value() was empty on parameter %s", paramIndex);
				nameParam(data, name, paramIndex);
				indexToExpander(data, paramIndex, new DefaultParameterExpander());
			});

			registerParameterAnnotation(CookieParam.class, (param, data, paramIndex) -> {
				final String name = param.value();
				checkState(emptyToNull(name) != null, "CookieParam.value() was empty on parameter %s", paramIndex);
				final String cookie = addTemplatedParam(name);
				data.template().header("Cookie", name + '=' + cookie);
				nameParam(data, name, paramIndex);
				indexToExpander(data, paramIndex, new CookieParamExpander(name));
			});
			registerParameterAnnotation(Encoded.class, (param, data, paramIndex) -> {
				data.indexToEncoded().put(paramIndex, true);
			});

		}
	}

	// Not using override as the super-type's method is deprecated and will be
	// removed.
	// Protected so JAXRS2Contract can make use of this
	protected String addTemplatedParam(String name) {
		return String.format("{%s}", name);
	}

}
