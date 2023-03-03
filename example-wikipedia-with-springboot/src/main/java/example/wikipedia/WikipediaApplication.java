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

import example.wikipedia.WikipediaClient.Page;
import example.wikipedia.WikipediaClient.Response;
import example.wikipedia.WikipediaClient.Wikipedia;
import jakarta.annotation.PostConstruct;
import java.util.Iterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class WikipediaApplication {

  public static void main(String[] args) {
    SpringApplication.run(WikipediaApplication.class, args).start();;
  }

  @Autowired
  private Wikipedia wikipedia;

  @PostConstruct
  public void run() {
    System.out.println("Let's search for PTAL!");
    Iterator<Page> pages = lazySearch(wikipedia, "PTAL");
    while (pages.hasNext()) {
      System.out.println(pages.next().title);
    }
  }

  /**
   * this will lazily continue searches, making new http calls as necessary.
   *
   * @param wikipedia used to search
   * @param query see {@link Wikipedia#search(String)}.
   */
  static Iterator<Page> lazySearch(final Wikipedia wikipedia, final String query) {
    final Response<Page> first = wikipedia.search(query);
    if (first.nextOffset == null) {
      return first.iterator();
    }
    return new Iterator<Page>() {
      Iterator<Page> current = first.iterator();
      Long nextOffset = first.nextOffset;

      @Override
      public boolean hasNext() {
        while (!current.hasNext() && nextOffset != null) {
          System.out.println("Wow.. even more results than " + nextOffset);
          Response<Page> nextPage = wikipedia.resumeSearch(query, nextOffset);
          current = nextPage.iterator();
          nextOffset = nextPage.nextOffset;
        }
        return current.hasNext();
      }

      @Override
      public Page next() {
        return current.next();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
