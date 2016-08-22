package ru.xxlabaza.feign.form;

import java.io.IOException;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MultipartSupportService implements IMultipartSupportService {

    @Override
    public String upload1(String folder, MultipartFile file, String message) {
        try {
            return new String(file.getBytes()) + ":" + message;
        } catch (IOException e) {
            throw new RuntimeException("Can't get file content", e);
        }
    }
    
    @Override
    public String upload2(MultipartFile file, String folder, String message) {
        try {
            return new String(file.getBytes()) + ":" + message;
        } catch (IOException e) {
            throw new RuntimeException("Can't get file content", e);
        }
    }
}
