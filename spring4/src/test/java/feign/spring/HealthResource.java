/**
 * Copyright (C) 2017 Marvin Herman Froeder (marvin@marvinformatics.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public interface HealthResource extends GenericResource<Data> {

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
