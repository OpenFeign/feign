/*
 * Copyright 2018 Artem Labazin
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

package feign.form;

import java.io.File;
import java.util.List;
import java.util.Map;

import feign.Headers;
import feign.Param;
import feign.QueryMap;
import feign.RequestLine;
import feign.Response;

/**
 *
 * @author Artem Labazin
 */
public interface TestClient {

  @RequestLine("POST /form")
  @Headers("Content-Type: application/x-www-form-urlencoded")
  Response form (@Param("key1") String key1, @Param("key2") String key2);

  @RequestLine("POST /upload/{id}")
  @Headers("Content-Type: multipart/form-data")
  String upload (@Param("id") Integer id, @Param("public") Boolean isPublic, @Param("file") File file);

  @RequestLine("POST /upload")
  @Headers("Content-Type: multipart/form-data")
  String upload (@Param("file") File file);

  @RequestLine("POST /json")
  @Headers("Content-Type: application/json")
  String json (Dto dto);

  @RequestLine("POST /query_map")
  String queryMap (@QueryMap Map<String, Object> value);

  @RequestLine("POST /upload/files")
  @Headers("Content-Type: multipart/form-data")
  String uploadWithArray (@Param("files") File[] files);

  @RequestLine("POST /upload/files")
  @Headers("Content-Type: multipart/form-data")
  String uploadWithList (@Param("files") List<File> files);

  @RequestLine("POST /upload/files")
  @Headers("Content-Type: multipart/form-data")
  String uploadWithManyFiles (@Param("files") File file1, @Param("files") File file2);

  @RequestLine("POST /upload/with_json")
  @Headers("Content-Type: multipart/form-data")
  Response uploadWithJson (@Param("dto") Dto dto, @Param("file") File file);

  @RequestLine("POST /upload/unknown_type")
  @Headers("Content-Type: multipart/form-data")
  String uploadUnknownType (@Param("file") File file);

  @RequestLine("POST /upload/form_data")
  @Headers("Content-Type: multipart/form-data")
  String uploadFormData (@Param("file") FormData formData);
}
