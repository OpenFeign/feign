package ru.xxlabaza.feign.form;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

public interface IMultipartSupportService {

    @RequestMapping(value = "/multipart/upload1/{folder}" , method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody String upload1(
            @PathVariable("folder") String folder,
            @RequestPart MultipartFile file,
            @RequestParam(value = "message", required = false) String message);
    
    @RequestMapping(value = "/multipart/upload2/{folder}" , method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody String upload2(
            @RequestBody MultipartFile file,
            @PathVariable("folder") String folder,
            @RequestParam(value = "message", required = false) String message);
}
