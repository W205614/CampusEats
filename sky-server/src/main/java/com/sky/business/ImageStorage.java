package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.common.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Shared admission and byte budget for product pictures and private evidence. */
@Service
public class ImageStorage {
  static final long MAX_FILE = 5L * 1024 * 1024;
  static final long RECONCILE_NANOS = TimeUnit.SECONDS.toNanos(60);
  private final Path root;
  private final Semaphore slots;
  private final long quota, minFree;
  private final MeterRegistry metrics;
  private final LongSupplier nanoTime;
  // All file mutations and ledger changes share this monitor with reconciliation.
  private boolean usageKnown;
  private long usedBytes, publicFiles, reconciledAt;

  @Autowired
  public ImageStorage(
      @Value("${campus.uploads}") String root,
      @Value("${campus.upload-concurrency:1}") int concurrency,
      @Value("${campus.upload-quota-bytes:2147483648}") long quota,
      @Value("${campus.upload-min-free-bytes:268435456}") long minFree,
      MeterRegistry metrics) {
    this(root, concurrency, quota, minFree, metrics, System::nanoTime);
  }

  ImageStorage(
      String root,
      int concurrency,
      long quota,
      long minFree,
      MeterRegistry metrics,
      LongSupplier nanoTime) {
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
    this.nanoTime = nanoTime;
  }

  public Stage stage(MultipartFile file) throws IOException {
    if (!slots.tryAcquire()) {
      rejected("busy");
      throw new BusinessException(503, "UPLOAD_BUSY", "图片处理繁忙，请稍后重试");
    }
    Path temp = null;
    try {
      require(!file.isEmpty() && file.getSize() <= MAX_FILE, 400, "INVALID_IMAGE", "图片最大5MB");
      synchronized (this) {
        Files.createDirectories(root.resolve(".tmp"));
        checkSpace();
        temp = Files.createTempFile(root.resolve(".tmp"), "image-", ".part");
      }
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
        if (temp != null) removeFile(temp);
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

  private synchronized void checkSpace() throws IOException {
    long now = nanoTime.getAsLong();
    if (!usageKnown
        || now - reconciledAt >= RECONCILE_NANOS
        || usedBytes > quota - MAX_FILE
        || publicFiles >= 9999) reconcile(now);
    if (usedBytes > quota || Files.getFileStore(root).getUsableSpace() < minFree) {
      rejected("storage_full");
      throw new BusinessException(507, "UPLOAD_STORAGE_FULL", "图片存储空间不足，请联系维护人员");
    }
  }

  private void reconcile(long now) throws IOException {
    usageKnown = false;
    long[] totals = new long[2];
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path path, BasicFileAttributes attributes)
              throws IOException {
            if (attributes.isRegularFile()) totals[0] = Math.addExact(totals[0], attributes.size());
            if (root.equals(path.getParent())
                && (attributes.isRegularFile()
                    || attributes.isSymbolicLink() && Files.isRegularFile(path))) totals[1]++;
            return FileVisitResult.CONTINUE;
          }
        });
    usedBytes = totals[0];
    publicFiles = totals[1];
    reconciledAt = now;
    usageKnown = true;
    metrics.counter("campus.upload.usage.reconciliations").increment();
  }

  private synchronized void removeFile(Path path) throws IOException {
    try {
      long bytes = Files.size(path);
      boolean publicFile = root.equals(path.getParent());
      if (!Files.deleteIfExists(path)) usageKnown = false;
      else if (usageKnown) {
        if (usedBytes < bytes || publicFile && publicFiles == 0) usageKnown = false;
        else {
          usedBytes -= bytes;
          if (publicFile) publicFiles--;
        }
      }
    } catch (NoSuchFileException ex) {
      // External mutation is not an application rollback; rebuild the ledger next time.
      usageKnown = false;
    } catch (IOException | RuntimeException ex) {
      usageKnown = false;
      throw ex;
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
          require(publicFiles < 10000, 400, "UPLOAD_QUOTA", "图片数量达到上限");
        }
        String id = UUID.randomUUID().toString();
        Path directory = privateFile ? root.resolve(".private") : root;
        try {
          Files.createDirectories(directory);
          Path target = directory.resolve(id + ".png");
          boolean wasPublic = root.equals(path.getParent());
          Files.move(path, target, StandardCopyOption.ATOMIC_MOVE);
          path = target;
          if (usageKnown) publicFiles += (privateFile ? 0 : 1) - (wasPublic ? 1 : 0);
        } catch (IOException | RuntimeException ex) {
          usageKnown = false;
          throw ex;
        }
        return id;
      }
    }

    public void accept() {
      synchronized (ImageStorage.this) {
        accepted = true;
      }
    }

    @Override
    public void close() throws IOException {
      synchronized (ImageStorage.this) {
        if (closed) return;
        closed = true;
        try {
          if (!accepted) removeFile(path);
        } finally {
          slots.release();
        }
      }
    }
  }

  private final class BoundedOutput extends FilterOutputStream {
    private long written;

    BoundedOutput(OutputStream out) {
      super(out);
    }

    private void reserve(int bytes) throws IOException {
      if (written + bytes > MAX_FILE) throw new IOException("Encoded image exceeds limit");
    }

    @Override
    public void write(int b) throws IOException {
      synchronized (ImageStorage.this) {
        reserve(1);
        try {
          out.write(b);
          written++;
          if (usageKnown) usedBytes = Math.addExact(usedBytes, 1);
        } catch (IOException | RuntimeException ex) {
          usageKnown = false;
          throw ex;
        }
      }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      synchronized (ImageStorage.this) {
        reserve(len);
        try {
          out.write(b, off, len);
          written += len;
          if (usageKnown) usedBytes = Math.addExact(usedBytes, len);
        } catch (IOException | RuntimeException ex) {
          usageKnown = false;
          throw ex;
        }
      }
    }

    @Override
    public void close() throws IOException {
      synchronized (ImageStorage.this) {
        try {
          super.close();
        } catch (IOException | RuntimeException ex) {
          usageKnown = false;
          throw ex;
        }
      }
    }
  }
}
