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
package example.wikipedia;

import java.util.ArrayList;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

public class WikipediaClient {



  @FeignClient(value = "jplaceholder", url = "https://en.wikipedia.org/",
      configuration = WikipediaClientConfiguration.class)
  public static interface Wikipedia {


    @RequestMapping(method = RequestMethod.GET,
        value = "/w/api.php?action=query&continue=&generator=search&prop=info&format=json&gsrsearch={search}")
    Response<Page> search(@PathVariable("search") String search);


    @RequestMapping(method = RequestMethod.GET,
        value = "/w/api.php?action=query&continue=&generator=search&prop=info&format=json&gsrsearch={search}&gsroffset={offset}")
    Response<Page> resumeSearch(@PathVariable("search") String search,
                                @PathVariable("offset") long offset);
  }

  static class Page {

    long id;
    String title;
  }

  public static class Response<X> extends ArrayList<X> {

    /**
     * when present, the position to resume the list.
     */
    Long nextOffset;
  }
}
