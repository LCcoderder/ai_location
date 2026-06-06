package com.example.ai_springboot.controller;

import com.example.ai_springboot.common.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UploadController {

    // 图片保存在项目根目录下的 uploads 文件夹
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/";

    @PostMapping("/upload")
    public Result<String> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return (Result<String>) Result.error("文件不能为空");
        try {
            File dir = new File(UPLOAD_DIR);
            if (!dir.exists()) dir.mkdirs();

            // 生成唯一文件名，防止重名
            String originalName = file.getOriginalFilename();
            String ext = originalName != null ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
            String newFileName = UUID.randomUUID().toString() + ext;

            File dest = new File(UPLOAD_DIR + newFileName);
            file.transferTo(dest);

            // 返回可以通过浏览器访问的网络路径
            return Result.success("/uploads/" + newFileName);
        } catch (IOException e) {
            e.printStackTrace();
            return (Result<String>) Result.error("图片上传失败");
        }
    }
}