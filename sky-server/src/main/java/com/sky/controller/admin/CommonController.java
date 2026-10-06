package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;

/**
 * 通用接口
 */
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
@Slf4j
public class CommonController {
    @Autowired
    private AliOssUtil aliOssUtil;
    @Value("${sky.storage.local-enabled:false}")
    private boolean localStorage;
    @Value("${sky.storage.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 文件上传
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file) {
        log.info("文件上传: {}", file);

        try {
            if (file == null || file.isEmpty()) return Result.error(MessageConstant.UPLOAD_FAILED);
            // 原始文件名
            String originalFilename = file.getOriginalFilename();
            // 截取原始文件名后缀
            if (originalFilename == null || originalFilename.lastIndexOf('.') < 0) return Result.error("图片格式不支持");
            String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
            if (!extension.matches("\\.(png|jpe?g|gif|webp)")) return Result.error("图片格式不支持");
            // 构建新文件名称
            String objectName = UUID.randomUUID().toString() + extension;
            if (localStorage) {
                Path directory = Paths.get(uploadDir).toAbsolutePath().normalize();
                Files.createDirectories(directory);
                file.transferTo(directory.resolve(objectName));
                return Result.success("/uploads/" + objectName);
            }

            // 文件的请求路径
            String filePath = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(filePath);
        } catch (IOException e) {
            log.info("文件上传失败: {}", e);
        }

        return Result.error(MessageConstant.UPLOAD_FAILED);
    }
}
