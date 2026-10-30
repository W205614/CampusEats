package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.common.BusinessException;
import com.sky.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {
  private final ImageStorage storage;

  public UploadService(ImageStorage storage) {
    this.storage = storage;
  }

  public String upload(MultipartFile file) {
    Actor.admin();
    return uploadProduct(file);
  }

  public String uploadProduct(MultipartFile file) {
    var actor = Actor.employee();
    require(
        java.util.Set.of("ADMIN", "PLATFORM_ADMIN", "SHOP_MANAGER").contains(actor.role()),
        403,
        "FORBIDDEN",
        "需要店长或平台权限");
    try (var image = storage.stage(file)) {
      String id = image.publish(false);
      image.accept();
      return "/uploads/" + id + ".png";
    } catch (java.io.IOException ex) {
      throw new BusinessException(503, "UPLOAD_UNAVAILABLE", "图片存储暂不可用");
    }
  }
}
