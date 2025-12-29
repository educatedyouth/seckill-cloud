package com.example.seckill.thirdparty.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.thirdparty.config.MinioConfig;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@RestController
@RequestMapping("/thirdparty/oss")
public class OssController {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    /**
     * 文件上传接口
     * @param file 前端传来的文件
     * @return 文件的完整访问 URL
     */
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        try {
            // 1. 生成唯一文件名：2025/12/28/uuid-filename.jpg
            String originalFilename = file.getOriginalFilename();
            String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            String fileName = new SimpleDateFormat("yyyy/MM/dd").format(new Date()) + "/"
                    + UUID.randomUUID().toString() + suffix;

            // 2. 上传流
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(fileName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 3. 拼接返回路径
            // 格式：http://127.0.0.1:9000/seckill/2025/12/28/xxx.jpg
            String url = minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + "/" + fileName;

            return Result.success(url);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }
}