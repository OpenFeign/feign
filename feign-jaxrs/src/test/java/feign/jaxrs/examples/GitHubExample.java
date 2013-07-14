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

import static dagger.Provides.Type.SET;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import dagger.Module;
import dagger.Provides;
import feign.Feign;
import feign.codec.Decoder;
import feign.jaxrs.JAXRSModule;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/** adapted from {@code com.example.retrofit.GitHubClient} */
public class GitHubExample {

  interface GitHub {
    @GET
    @Path("/repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(
        @PathParam("owner") String owner, @PathParam("repo") String repo);
  }

  static class Contributor {
    String login;
    int contributions;
  }

  public static void main(String... args) {
    GitHub github = Feign.create(GitHub.class, "https://api.github.com", new GitHubModule());

    // Fetch and print a list of the contributors to this library.
    List<Contributor> contributors = github.contributors("netflix", "feign");
    for (Contributor contributor : contributors) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }
  }

  /** JAXRSModule tells us to process @GET etc annotations */
  @Module(overrides = true, library = true, includes = JAXRSModule.class)
  static class GitHubModule {
    @Provides(type = SET)
    Decoder decoder() {
      return new Decoder.TextStream<Object>() {
        Gson gson = new Gson();

        @Override
        public Object decode(Reader reader, Type type) throws IOException {
          try {
            return gson.fromJson(reader, type);
          } catch (JsonIOException e) {
            if (e.getCause() != null && e.getCause() instanceof IOException) {
              throw IOException.class.cast(e.getCause());
            }
            throw e;
          }
        }
      };
    }
  }
}
