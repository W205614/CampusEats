package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.common.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Shared admission and byte budget for product pictures and private evidence. */
@Service
public class ImageStorage {
  static final long MAX_FILE = 5L * 1024 * 1024;
  private final Path root;
  private final Semaphore slots;
  private final long quota, minFree;
  private final MeterRegistry metrics;

  public ImageStorage(
      @Value("${campus.uploads}") String root,
      @Value("${campus.upload-concurrency:1}") int concurrency,
      @Value("${campus.upload-quota-bytes:2147483648}") long quota,
      @Value("${campus.upload-min-free-bytes:268435456}") long minFree,
      MeterRegistry metrics) {
    require(
        concurrency > 0 && concurrency <= 4 && quota > 0 && minFree >= 0,
        503,
        "CONFIGURATION",
        "上传资源限制配置不正确");
    this.root = Path.of(root).toAbsolutePath().normalize();
    this.slots = new Semaphore(concurrency);
    this.quota = quota;
    this.minFree = minFree;
    this.metrics = metrics;
  }

  public Stage stage(MultipartFile file) throws IOException {
    if (!slots.tryAcquire()) {
      rejected("busy");
      throw new BusinessException(503, "UPLOAD_BUSY", "图片处理繁忙，请稍后重试");
    }
    Path temp = null;
    try {
      require(!file.isEmpty() && file.getSize() <= MAX_FILE, 400, "INVALID_IMAGE", "图片最大5MB");
      Files.createDirectories(root.resolve(".tmp"));
      checkSpace();
      temp = Files.createTempFile(root.resolve(".tmp"), "image-", ".part");
      try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
        var readers = ImageIO.getImageReaders(input);
        require(readers.hasNext(), 400, "INVALID_IMAGE", "图片无法解码");
        var reader = readers.next();
        try {
          reader.setInput(input);
          String format = reader.getFormatName().toLowerCase(Locale.ROOT);
          int w = reader.getWidth(0), h = reader.getHeight(0);
          require(
              Set.of("png", "jpeg", "jpg").contains(format)
                  && w > 0
                  && h > 0
                  && w <= 4096
                  && h <= 4096,
              400,
              "INVALID_IMAGE",
              "仅支持边长不超过4096的PNG或JPEG");
          var image = reader.read(0);
          try (var out = new BoundedOutput(Files.newOutputStream(temp))) {
            require(ImageIO.write(image, "png", out), 400, "INVALID_IMAGE", "图片编码失败");
          } finally {
            image.flush();
          }
        } finally {
          reader.dispose();
        }
      }
      return new Stage(temp);
    } catch (Exception ex) {
      try {
        if (temp != null) Files.deleteIfExists(temp);
      } finally {
        slots.release();
      }
      if (ex instanceof BusinessException b) {
        if (!b.code().equals("UPLOAD_STORAGE_FULL")) rejected(b.code());
        throw b;
      }
      rejected("invalid_image");
      throw new BusinessException(400, "INVALID_IMAGE", "图片读取或编码失败，编码后最大5MB");
    }
  }

  private void rejected(String reason) {
    metrics.counter("campus.upload.resource.rejected", "reason", reason).increment();
  }

  private void checkSpace() throws IOException {
    long used;
    try (var files = Files.walk(root)) {
      used =
          files
              .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
              .mapToLong(
                  p -> {
                    try {
                      return Files.size(p);
                    } catch (IOException e) {
                      throw new UncheckedIOException(e);
                    }
                  })
              .sum();
    }
    if (used > quota || Files.getFileStore(root).getUsableSpace() < minFree) {
      rejected("storage_full");
      throw new BusinessException(507, "UPLOAD_STORAGE_FULL", "图片存储空间不足，请联系维护人员");
    }
  }

  public final class Stage implements AutoCloseable {
    private Path path;
    private boolean accepted, closed;

    private Stage(Path path) {
      this.path = path;
    }

    public String publish(boolean privateFile) throws IOException {
      synchronized (ImageStorage.this) {
        // Staged files are included in the budget, including concurrent writers.
        checkSpace();
        if (!privateFile) {
          try (var files = Files.list(root)) {
            require(
                files.filter(Files::isRegularFile).count() < 10000,
                400,
                "UPLOAD_QUOTA",
                "图片数量达到上限");
          }
        }
        String id = UUID.randomUUID().toString();
        Path directory = privateFile ? root.resolve(".private") : root;
        Files.createDirectories(directory);
        Path target = directory.resolve(id + ".png");
        Files.move(path, target, StandardCopyOption.ATOMIC_MOVE);
        path = target;
        return id;
      }
    }

    public void accept() {
      accepted = true;
    }

    @Override
    public void close() throws IOException {
      if (closed) return;
      closed = true;
      try {
        if (!accepted) Files.deleteIfExists(path);
      } finally {
        slots.release();
      }
    }
  }

  private static final class BoundedOutput extends FilterOutputStream {
    private long written;

    BoundedOutput(OutputStream out) {
      super(out);
    }

    private void reserve(int bytes) throws IOException {
      if (written + bytes > MAX_FILE) throw new IOException("Encoded image exceeds limit");
      written += bytes;
    }

    @Override
    public void write(int b) throws IOException {
      reserve(1);
      out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      reserve(len);
      out.write(b, off, len);
    }
  }
}
