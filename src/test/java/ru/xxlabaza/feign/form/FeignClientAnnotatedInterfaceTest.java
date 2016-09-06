package ru.xxlabaza.feign.form;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.multipart.MultipartFile;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Server.class)
@WebIntegrationTest(value = {"server.port=8080", "feign.hystrix.enabled=false"})
public class FeignClientAnnotatedInterfaceTest {

    @Autowired
    private MultipartSupportServiceClient client;

    @Test
    public void upload1Test() throws Exception {

        MultipartFile file = new MockMultipartFile("file", "test".getBytes(StandardCharsets.UTF_8));
        String response = client.upload1("test folder", file, "message text");
        Assert.assertEquals("test:message text", response);
    }

    @Test
    public void upload2Test() throws Exception {

        MultipartFile file = new MockMultipartFile("file", "test".getBytes(StandardCharsets.UTF_8));
        String response = client.upload2(file, "test folder", "message text");
        Assert.assertEquals("test:message text", response);
    }
}
