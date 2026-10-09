package com.zhiguang.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class OssStorageServiceTest {

    @Autowired
    private OssStorageService ossStorageService;

    @Test
    void testUploadAvatar() throws Exception {

        // 1. 读取 src/test/resources/test-avatar.png
        ClassPathResource resource = new ClassPathResource("test.png");
        // 2. 获取图片输入流
        try (InputStream inputStream = resource.getInputStream()) {
            // 3. 模拟前端上传的 MultipartFile
            MultipartFile file = new MockMultipartFile(
                            "file",
                            "test-avatar.png",
                            "image/png",
                            inputStream
                    );

            // 4. 调用真实 OSS 上传
            String url = ossStorageService.uploadAvatar(10001L, file);
            // 5. 查看返回结果
            System.out.println("上传成功！");
            System.out.println("文件URL：" + url);
            assertNotNull(url);
        }
    }

    @Test
    void testGeneratePresignedPutUrl() {

        // 1. 指定未来上传到 OSS 的文件路径
        String objectKey = "test/presign-test.png";
        // 2. 文件类型
        String contentType = "image/png";
        // 3. 预签名 URL 有效期：10分钟
        int expiresInSeconds = 600;

        // 4. 生成预签名 URL
        String putUrl =
                ossStorageService.generatePresignedPutUrl(
                        objectKey,
                        contentType,
                        expiresInSeconds
                );
        // 5. 打印
        System.out.println("预签名 PUT URL：");
        System.out.println(putUrl);
        // 6. 基本验证
        assertNotNull(putUrl);
        assertFalse(putUrl.isBlank());
        assertTrue(putUrl.startsWith("http"));
    }
}