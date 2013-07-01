/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.jaxrs.examples;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import dagger.Module;
import dagger.Provides;
import feign.Feign;
import feign.Request;
import feign.RequestTemplate;
import feign.Target;
import feign.codec.Decoder;
import feign.codec.Decoders;
import feign.examples.AWSSignatureVersion4;
import feign.jaxrs.JAXRSModule;

public class IAMExample {

  interface IAM {
    @GET @Path("/?Action=GetUser&Version=2010-05-08") String arn();
  }

  public static void main(String... args) {

    IAM iam = Feign.create(new IAMTarget(args[0], args[1]), new IAMModule());
    System.out.println(iam.arn());
  }

  static class IAMTarget extends AWSSignatureVersion4 implements Target<IAM> {

    @Override public Class<IAM> type() {
      return IAM.class;
    }

    @Override public String name() {
      return "iam";
    }

    @Override public String url() {
      return "https://iam.amazonaws.com";
    }

    private IAMTarget(String accessKey, String secretKey) {
      super(accessKey, secretKey);
    }

    @Override public Request apply(RequestTemplate in) {
      in.insert(0, url());
      return super.apply(in);
    }
  }

  @Module(overrides = true, library = true, includes = JAXRSModule.class)
  static class IAMModule {
    @Provides @Singleton Map<String, Decoder> decoders() {
      return ImmutableMap.of("IAM#arn()", Decoders.firstGroup("<Arn>([\\S&&[^<]]+)</Arn>"));
    }
  }
}
