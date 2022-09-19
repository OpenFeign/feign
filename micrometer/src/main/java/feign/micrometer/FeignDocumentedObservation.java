/*
 * Copyright 2012-2022 The Feign Authors
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
package feign.micrometer;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.DocumentedObservation;

/**
 * {@link DocumentedObservation} for Feign.
 * 
 * @since 1.10.0
 */
public enum FeignDocumentedObservation implements DocumentedObservation {

  DEFAULT {
    @Override
    public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
      return DefaultFeignObservationConvention.class;
    }

    @Override
    public KeyName[] getLowCardinalityKeyNames() {
      return HttpClientTags.values();
    }
  };

  enum HttpClientTags implements KeyName {

    STATUS {
      @Override
      public String asString() {
        return "status";
      }
    },
    METHOD {
      @Override
      public String asString() {
        return "method";
      }
    },
    URI {
      @Override
      public String asString() {
        return "uri";
      }
    },
    TARGET_SCHEME {
      @Override
      public String asString() {
        return "target.scheme";
      }
    },
    TARGET_HOST {
      @Override
      public String asString() {
        return "target.host";
      }
    },
    TARGET_PORT {
      @Override
      public String asString() {
        return "target.port";
      }
    }

  }

}
