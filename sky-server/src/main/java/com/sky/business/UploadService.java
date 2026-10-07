package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.common.BusinessException;
import com.sky.security.Actor;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {
  private final Path root;

  public UploadService(@Value("${campus.uploads}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  public synchronized String upload(MultipartFile file) {
    Actor.admin();
    require(!file.isEmpty() && file.getSize() <= 5 * 1024 * 1024, 400, "INVALID_IMAGE", "图片最大5MB");
    try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
      var readers = ImageIO.getImageReaders(input);
      require(readers.hasNext(), 400, "INVALID_IMAGE", "图片无法解码");
      var reader = readers.next();
      try {
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        require(
            format.equals("jpeg") || format.equals("jpg") || format.equals("png"),
            400,
            "INVALID_IMAGE",
            "仅支持JPEG与PNG");
        reader.setInput(input);
        int w = reader.getWidth(0), h = reader.getHeight(0);
        require(w > 0 && h > 0 && w <= 4096 && h <= 4096, 400, "INVALID_IMAGE", "图片边长最大4096像素");
        BufferedImage image = reader.read(0);
        Files.createDirectories(root);
        try (var stream = Files.list(root)) {
          require(
              stream.filter(Files::isRegularFile).count() < 10000, 400, "UPLOAD_QUOTA", "图片数量达到上限");
        }
        String name = UUID.randomUUID() + ".png";
        Path path = root.resolve(name).normalize();
        require(path.startsWith(root), 400, "INVALID_IMAGE", "图片路径不正确");
        require(ImageIO.write(image, "png", path.toFile()), 400, "INVALID_IMAGE", "图片编码失败");
        return "/uploads/" + name;
      } finally {
        reader.dispose();
      }
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BusinessException(400, "INVALID_IMAGE", "图片读取失败");
    }
  }
}
