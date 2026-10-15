package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.infra.Db;
import com.sky.security.Actor;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EvidenceService {
  private final Path root;
  private final Db db;
  private final PlatformScope scope;

  public EvidenceService(@Value("${campus.uploads}") String root, Db db, PlatformScope scope) {
    this.root = Path.of(root).toAbsolutePath().normalize().resolve(".private");
    this.db = db;
    this.scope = scope;
  }

  @Transactional
  public String upload(long order, MultipartFile file) throws java.io.IOException {
    long user = Actor.user().id();
    scope.order(order, false);
    require(!file.isEmpty() && file.getSize() <= 5L * 1024 * 1024, 400, "INVALID_IMAGE", "图片最大5MB");
    require(
        db.count(
                "SELECT COUNT(*) FROM evidence WHERE order_id=? AND user_id=? AND request_id IS"
                    + " NULL",
                order,
                user)
            < 20,
        400,
        "EVIDENCE_LIMIT",
        "待绑定图片数量已达上限");
    try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
      var readers = ImageIO.getImageReaders(input);
      require(readers.hasNext(), 400, "INVALID_IMAGE", "图片无法解码");
      var reader = readers.next();
      try {
        reader.setInput(input);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        require(
            Set.of("png", "jpeg", "jpg").contains(format)
                && reader.getWidth(0) <= 4096
                && reader.getHeight(0) <= 4096,
            400,
            "INVALID_IMAGE",
            "仅支持边长不超过4096的PNG或JPEG");
        String id = UUID.randomUUID().toString();
        Files.createDirectories(root);
        Path target = root.resolve(id + ".png");
        require(
            ImageIO.write(reader.read(0), "png", target.toFile()), 400, "INVALID_IMAGE", "图片转换失败");
        db.update(
            "INSERT INTO evidence(id,user_id,order_id,path,created_at)"
                + " VALUES(?,?,?,?,CURRENT_TIMESTAMP)",
            id,
            user,
            order,
            id + ".png");
        return id;
      } finally {
        reader.dispose();
      }
    }
  }

  public byte[] read(String id) throws java.io.IOException {
    require(id.matches("[a-f0-9-]{36}"), 404, "NOT_FOUND", "图片不存在");
    var e = db.one("SELECT * FROM evidence WHERE id=?", id);
    require(e != null, 404, "NOT_FOUND", "图片不存在");
    Actor a = Actor.current();
    require(!a.role().equals("DELIVERER"), 403, "FORBIDDEN", "配送员无权查看售后举证");
    scope.order(Db.id(e, "order_id"), false);
    return Files.readAllBytes(root.resolve(e.get("path").toString()));
  }
}
