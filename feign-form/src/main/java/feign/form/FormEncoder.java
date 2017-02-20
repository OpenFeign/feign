/*
 * Copyright 2017 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.form;

import feign.RequestTemplate;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.val;

/**
 * Properly encodes requests with <b>application/x-www-form-urlencoded</b> and <b>multipart/form-data</b> Content-Type.
 * <p>
 * Also, the encoder has a <b>delegate</b> field for encoding non-form requests (like JSON or other).
 * <p>
 * Default <b>delegate</b> object is {@link feign.codec.Encoder.Default} instance.
 * <p>
 * Usage example:
 * <p>
 * <b>Declaring API interface:</b>
 * <pre>
 * interface SomeApi {
 *
 *     &#064;RequestLine("POST /json")
 *     &#064;Headers("Content-Type: application/json")
 *     void json (Dto dto);
 *
 *     &#064;RequestLine("POST /form")
 *     &#064;Headers("Content-Type: application/x-www-form-urlencoded")
 *     void from (@Param("field1") String field1, @Param("field2") String field2);
 *
 * }
 * </pre>
 * <p>
 * <b>Creating Feign client instance:</b>
 * <pre>
 * SomeApi api = Feign.builder()
 *       .encoder(new FormEncoder(new JacksonEncoder()))
 *       .target(SomeApi.class, "http://localhost:8080");
 * </pre>
 * <p>
 * Now it can handle JSON Content-Type by {@code feign.jackson.JacksonEncoder} and
 * form request by {@link feign.form.FormEncoder}.
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since 30.04.2016
 */
public class FormEncoder implements Encoder {

    private final Encoder deligate;

    private final Map<String, FormDataProcessor> processors;

    /**
     * Default {@code FormEncoder} constructor.
     * <p>
     * Sets {@link feign.codec.Encoder.Default} instance as delegate encoder.
     */
    public FormEncoder () {
        this(new Encoder.Default());
    }

    /**
     * {@code FormEncoder} constructor with delegate encoder argument.
     * <p>
     * @param delegate delegate encoder for processing non-form requests.
     */
    public FormEncoder (Encoder delegate) {
        this.deligate = delegate;
        processors = new HashMap<String, FormDataProcessor>(2, 1.F);

        val formEncodedDataProcessor = new FormEncodedDataProcessor();
        processors.put(formEncodedDataProcessor.getSupportetContentType().toLowerCase(),
                       formEncodedDataProcessor);

        val multipartEncodedDataProcessor = new MultipartEncodedDataProcessor();
        processors.put(multipartEncodedDataProcessor.getSupportetContentType().toLowerCase(),
                       multipartEncodedDataProcessor);
    }

    @Override
    public void encode (Object object, Type bodyType, RequestTemplate template) {
        if (!MAP_STRING_WILDCARD.equals(bodyType)) {
            deligate.encode(object, bodyType, template);
            return;
        }

        String formType = "";
        for (Map.Entry<String, Collection<String>> entry : template.headers().entrySet()) {
            if (!entry.getKey().equalsIgnoreCase("Content-Type")) {
                continue;
            }
            for (String contentType : entry.getValue()) {
                if (contentType != null && processors.containsKey(contentType.toLowerCase())) {
                    formType = contentType;
                    break;
                }
            }
            if (!formType.isEmpty()) {
                break;
            }
        }

        if (formType.isEmpty()) {
            deligate.encode(object, bodyType, template);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) object;
        processors.get(formType).process(data, template);
    }
}
