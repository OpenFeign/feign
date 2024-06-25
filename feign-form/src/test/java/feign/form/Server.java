/*
 * Copyright 2024 the original author or authors.
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

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.I_AM_A_TEAPOT;
import static org.springframework.http.HttpStatus.LOCKED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import lombok.val;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@Controller
@SpringBootApplication
@SuppressWarnings("checkstyle:DesignForExtension")
public class Server {

  @PostMapping("/form")
  public ResponseEntity<Void> form (
    @RequestParam("key1") String key1,
    @RequestParam("key2") String key2
  ) {
    val status = !key1.equals(key2)
                 ? BAD_REQUEST
                 : OK;
    return ResponseEntity.status(status).body(null);
  }

  @PostMapping("/upload/{id}")
  @ResponseStatus(OK)
  public ResponseEntity<Long> upload (
    @PathVariable("id") Integer id,
    @RequestParam("public") Boolean isPublic,
    @RequestParam("file") MultipartFile file
  ) {
    HttpStatus status;
    if (id == null || id != 10) {
      status = LOCKED;
    } else if (isPublic == null || !isPublic) {
      status = FORBIDDEN;
    } else if (file.getSize() == 0) {
      status = I_AM_A_TEAPOT;
    } else if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
      status = CONFLICT;
    } else {
      status = OK;
    }
    return ResponseEntity.status(status).body(file.getSize());
  }

  @PostMapping("/upload")
  public ResponseEntity<Long> upload (@RequestParam("file") MultipartFile file) {
    HttpStatus status;
    if (file.getSize() == 0) {
      status = I_AM_A_TEAPOT;
    } else if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
      status = CONFLICT;
    } else {
      status = OK;
    }
    return ResponseEntity.status(status).body(file.getSize());
  }

  @PostMapping("/upload/files")
  public ResponseEntity<Long> upload (@RequestParam("files") MultipartFile[] files) {
    HttpStatus status;
    if (files[0].getSize() == 0 || files[1].getSize() == 0) {
      status = I_AM_A_TEAPOT;
    } else if (files[0].getOriginalFilename() == null || files[0].getOriginalFilename().trim().isEmpty() ||
               files[1].getOriginalFilename() == null || files[1].getOriginalFilename().trim().isEmpty()) {
      status = CONFLICT;
    } else {
      status = OK;
    }
    return ResponseEntity.status(status).body(files[0].getSize() + files[1].getSize());
  }

  @PostMapping(path = "/json", consumes = APPLICATION_JSON_VALUE)
  public ResponseEntity<String> json (@RequestBody Dto dto) {
    HttpStatus status;
    if (!dto.getName().equals("Artem")) {
      status = CONFLICT;
    } else if (!dto.getAge().equals(11)) {
      status = I_AM_A_TEAPOT;
    } else {
      status = OK;
    }
    return ResponseEntity.status(status).body("ok");
  }

  @PostMapping("/query_map")
  public ResponseEntity<Integer> queryMap (
    @RequestParam("filter") List<String> filters
  ) {
    val status = filters != null && !filters.isEmpty()
                 ? OK
                 : I_AM_A_TEAPOT;
    return ResponseEntity.status(status).body(filters.size());
  }

  @PostMapping(path = "/wild-card-map", consumes = APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<Integer> wildCardMap (
    @RequestParam("key1") String key1,
    @RequestParam("key2") String key2
  ) {
    val status = key1.equals(key2)
                 ? OK
                 : I_AM_A_TEAPOT;
    return ResponseEntity.status(status).body(null);
  }

  @PostMapping(path = "/upload/with_dto", consumes = MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Long> uploadWithDto (
    Dto dto,
    @RequestPart("file") MultipartFile file
  ) throws IOException {
    val status = dto != null && dto.getName().equals("Artem")
                 ? OK
                 : I_AM_A_TEAPOT;
    return ResponseEntity.status(status).body(file.getSize());
  }

  @PostMapping(path = "/upload/byte_array", consumes = MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> uploadByteArray (
    @RequestPart("file") MultipartFile file
  ) {
    val status = file != null
                 ? OK
                 : I_AM_A_TEAPOT;
    return ResponseEntity.status(status).body(file.getOriginalFilename());
  }

  @PostMapping(path = "/upload/byte_array_parameter", consumes = MULTIPART_FORM_DATA_VALUE)
  // We just want the request because when there's a filename part of the Content-Disposition header spring
  // will treat it as a file (available through getFile()) and when it doesn't have the filename part it's
  // available in the parameter (getParameter())
  public ResponseEntity<String> uploadByteArrayParameter (
    MultipartHttpServletRequest request
  ) {
    val status = request.getFile("file") == null && request.getParameter("file") != null
                 ? OK
                 : I_AM_A_TEAPOT;
    return ResponseEntity.status(status).build();
  }

  @PostMapping(path = "/upload/unknown_type", consumes = MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> uploadUnknownType (
    @RequestPart("file") MultipartFile file
  ) {
    val status = file != null
                 ? OK
                 : I_AM_A_TEAPOT;
    return ResponseEntity.status(status).body(file.getContentType());
  }

  @PostMapping(path = "/upload/form_data", consumes = MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> uploadFormData (
    @RequestPart("file") MultipartFile file
  ) {
    val status = file != null
                 ? OK
                 : I_AM_A_TEAPOT;
    return ResponseEntity.status(status).body(file.getOriginalFilename() + ':' + file.getContentType());
  }

  @PostMapping(path = "/submit/url", consumes = APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<String> submitRepeatableQueryParam (
    @RequestParam("names") String[] names
  ) {
    val response = new StringBuilder();
    if (names != null && names.length == 2) {
      response
          .append(names[0])
          .append(" and ")
          .append(names[1]);
    }
    val status = response.length() > 0
                 ? OK
                 : I_AM_A_TEAPOT;

    return ResponseEntity.status(status).body(response.toString());
  }

  @PostMapping(path = "/submit/form", consumes = MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> submitRepeatableFormParam (
    @RequestParam("names") Collection<String> names
  ) {
    val response = new StringBuilder();
    if (names != null && names.size() == 2) {
      val iterator = names.iterator();
      response
          .append(iterator.next())
          .append(" and ")
          .append(iterator.next());
    }
    val status = response.length() > 0
                 ? OK
                 : I_AM_A_TEAPOT;

    return ResponseEntity.status(status).body(response.toString());
  }

  @PostMapping(path = "/form-data", consumes = APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<String> submitPostData (
    @RequestParam("f_name") String firstName,
    @RequestParam("age") Integer age
  ) {
    val response = new StringBuilder();
    if (firstName != null && age != null) {
      response
              .append(firstName)
              .append("=")
              .append(age);
    }
    val status = response.length() > 0
            ? OK
            : I_AM_A_TEAPOT;

    return ResponseEntity.status(status).body(response.toString());
  }
}
