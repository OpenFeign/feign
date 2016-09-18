/*
 * Copyright 2016 Artem Labazin <xxlabaza@gmail.com>.
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
import java.util.Map;

/**
 * Interface for form data processing.
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since 30.04.2016
 */
public interface FormDataProcessor {

    /**
     * Processing form data to request body.
     *
     * @param data form data, where key is a parameter name and value is...a value.
     * @param template current request object.
     */
    void process (Map<String, Object> data, RequestTemplate template);

    /**
     * Returns {@code FormDataProcessor} implementation supporting Content-Type.
     *
     * @return supported MIME Content-Type
     */
    String getSupportetContentType ();
}
