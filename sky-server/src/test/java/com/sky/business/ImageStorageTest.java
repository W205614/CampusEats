package com.sky.business;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.common.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class ImageStorageTest {
  @TempDir Path root;

  ImageStorage storage(long quota, long free) {
    return new ImageStorage(root.toString(), 1, quota, free, new SimpleMeterRegistry());
  }

  void assertLedgerMatchesFiles(ImageStorage storage) throws Exception {
    long bytes;
    try (var files = Files.walk(root)) {
      bytes =
          files
              .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
              .mapToLong(
                  p -> {
                    try {
                      return Files.size(p);
                    } catch (IOException ex) {
                      throw new UncheckedIOException(ex);
                    }
                  })
              .sum();
    }
    long count;
    try (var files = Files.list(root)) {
      count = files.filter(Files::isRegularFile).count();
    }
    assertEquals(true, ReflectionTestUtils.getField(storage, "usageKnown"));
    assertEquals(bytes, ReflectionTestUtils.getField(storage, "usedBytes"));
    assertEquals(count, ReflectionTestUtils.getField(storage, "publicFiles"));
  }

  MockMultipartFile image() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    return new MockMultipartFile("file", "test.png", "image/png", out.toByteArray());
  }

  @Test
  void admissionIsSharedUntilCommitAndRollbackRemovesPublishedFile() throws Exception {
    var storage = storage(100000, 0);
    try (var staged = storage.stage(image())) {
      assertEquals(
          "UPLOAD_BUSY",
          assertThrows(BusinessException.class, () -> storage.stage(image())).code());
      String id = staged.publish(true);
      assertTrue(Files.exists(root.resolve(".private/" + id + ".png")));
    }
    try (var files = Files.walk(root)) {
      assertEquals(0, files.filter(Files::isRegularFile).count());
    }
    try (var staged = storage.stage(image())) {
      String id = staged.publish(false);
      staged.accept();
      assertTrue(Files.exists(root.resolve(id + ".png")));
    }
  }

  @Test
  void byteQuotaAndFreeSpaceRefuseWritesWithoutDeletingExistingFiles() throws Exception {
    try (var staged = storage(1, 0).stage(image())) {
      assertEquals(507, assertThrows(BusinessException.class, () -> staged.publish(true)).status());
    }
    Files.writeString(root.resolve("existing.png"), "keep");
    assertEquals(
        507,
        assertThrows(BusinessException.class, () -> storage(100000, Long.MAX_VALUE).stage(image()))
            .status());
    assertEquals("keep", Files.readString(root.resolve("existing.png")));
  }

  @Test
  void smallJpegCannotExpandIntoAnOversizedStoredPng() throws Exception {
    var pixels = new BufferedImage(1400, 1400, BufferedImage.TYPE_INT_RGB);
    var random = new Random(42);
    for (int y = 0; y < 1400; y++)
      for (int x = 0; x < 1400; x++) pixels.setRGB(x, y, random.nextInt());
    var out = new ByteArrayOutputStream();
    ImageIO.write(pixels, "jpeg", out);
    assertTrue(out.size() < ImageStorage.MAX_FILE);
    var file = new MockMultipartFile("file", "noise.jpg", "image/jpeg", out.toByteArray());
    var storage = storage(20000000, 0);
    assertEquals(
        "INVALID_IMAGE", assertThrows(BusinessException.class, () -> storage.stage(file)).code());
    try (var files = Files.walk(root)) {
      assertEquals(0, files.filter(Files::isRegularFile).count());
    }
    assertLedgerMatchesFiles(storage);
  }

  @Test
  void repeatedUploadsAndRollbacksUseOneScanAndAccountForBothDestinations() throws Exception {
    var metrics = new SimpleMeterRegistry();
    var storage = new ImageStorage(root.toString(), 1, 100000000, 0, metrics);
    for (int i = 0; i < 12; i++) {
      try (var staged = storage.stage(image())) {
        staged.publish(i % 2 == 0);
        staged.accept();
      }
      assertLedgerMatchesFiles(storage);
    }
    var rolledBack = storage.stage(image());
    rolledBack.publish(false);
    rolledBack.close();
    rolledBack.close();
    assertLedgerMatchesFiles(storage);
    assertEquals(1, metrics.get("campus.upload.usage.reconciliations").counter().count());
    // Double close must not release an additional permit.
    try (var staged = storage.stage(image())) {
      assertEquals(
          "UPLOAD_BUSY",
          assertThrows(BusinessException.class, () -> storage.stage(image())).code());
    }
  }

  @Test
  void firstScanAndRestartIncludeExistingPrivateAndTemporaryFiles() throws Exception {
    Files.createDirectories(root.resolve(".private"));
    Files.createDirectories(root.resolve(".tmp"));
    Files.writeString(root.resolve("public.png"), "public");
    Files.writeString(root.resolve(".private/evidence.png"), "private");
    Files.writeString(root.resolve(".tmp/leftover.part"), "temporary");
    for (int restart = 0; restart < 2; restart++) {
      var storage = storage(100000000, 0);
      try (var staged = storage.stage(image())) {
        assertLedgerMatchesFiles(storage);
      }
      assertLedgerMatchesFiles(storage);
    }
    assertEquals(
        "UPLOAD_STORAGE_FULL",
        assertThrows(BusinessException.class, () -> storage(1, 0).stage(image())).code());
  }

  @Test
  void elapsedCalibrationFindsExternalFilesWithoutSchedulingOrRepeatedScans() throws Exception {
    var time = new AtomicLong();
    var metrics = new SimpleMeterRegistry();
    var storage = new ImageStorage(root.toString(), 1, 100000000, 0, metrics, time::get);
    try (var staged = storage.stage(image())) {}
    Files.writeString(root.resolve("restored.png"), "out-of-process restoration");
    time.set(ImageStorage.RECONCILE_NANOS - 1);
    try (var staged = storage.stage(image())) {}
    assertEquals(1, metrics.get("campus.upload.usage.reconciliations").counter().count());
    time.incrementAndGet();
    try (var staged = storage.stage(image())) {
      assertLedgerMatchesFiles(storage);
    }
    assertLedgerMatchesFiles(storage);
    assertEquals(2, metrics.get("campus.upload.usage.reconciliations").counter().count());
  }

  @Test
  void concurrentStagedFilesCountBeforePublishAndRollbackReleasesTheirBudget() throws Exception {
    byte[] encoded = image().getBytes();
    Files.write(root.resolve("existing.png"), new byte[(int) ImageStorage.MAX_FILE]);
    var storage =
        new ImageStorage(
            root.toString(),
            2,
            ImageStorage.MAX_FILE + encoded.length,
            0,
            new SimpleMeterRegistry());
    try (var first = storage.stage(image())) {
      var second = storage.stage(image());
      assertEquals(
          "UPLOAD_STORAGE_FULL",
          assertThrows(BusinessException.class, () -> first.publish(true)).code());
      second.close();
      assertLedgerMatchesFiles(storage);
      first.publish(true);
      first.accept();
    }
    assertLedgerMatchesFiles(storage);
    assertEquals(
        ImageStorage.MAX_FILE + encoded.length, ReflectionTestUtils.getField(storage, "usedBytes"));
  }

  @Test
  void parallelEncodersAndCalibrationDoNotLoseByteUpdates() throws Exception {
    var time = new AtomicLong();
    var storage =
        new ImageStorage(root.toString(), 2, 100000000, 0, new SimpleMeterRegistry(), time::get);
    var ready = new CyclicBarrier(2);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var first =
          workers.submit(
              () -> {
                try (var staged = storage.stage(image())) {
                  ready.await(10, TimeUnit.SECONDS);
                  time.set(ImageStorage.RECONCILE_NANOS);
                  staged.publish(false);
                  staged.accept();
                }
                return null;
              });
      var second =
          workers.submit(
              () -> {
                try (var staged = storage.stage(image())) {
                  ready.await(10, TimeUnit.SECONDS);
                  staged.publish(true);
                  staged.accept();
                }
                return null;
              });
      first.get(15, TimeUnit.SECONDS);
      second.get(15, TimeUnit.SECONDS);
    }
    assertLedgerMatchesFiles(storage);
  }

  @Test
  void publicCountLimitStillAllowsPrivateEvidenceAndRollbackRemovesPublicCount() throws Exception {
    for (int i = 0; i < 9999; i++) Files.createFile(root.resolve("existing-" + i + ".png"));
    var storage = storage(100000000, 0);
    try (var rolledBack = storage.stage(image())) {
      rolledBack.publish(false);
      assertLedgerMatchesFiles(storage);
    }
    try (var accepted = storage.stage(image())) {
      accepted.publish(false);
      accepted.accept();
    }
    try (var refused = storage.stage(image())) {
      assertEquals(
          "UPLOAD_QUOTA",
          assertThrows(BusinessException.class, () -> refused.publish(false)).code());
      refused.publish(true);
      refused.accept();
    }
    assertLedgerMatchesFiles(storage);
  }

  @Test
  void publishFailureInvalidatesUsageAndNextUploadReconcilesWithoutLosingFiles() throws Exception {
    Files.writeString(root.resolve(".private"), "occupied directory name");
    var metrics = new SimpleMeterRegistry();
    var storage = new ImageStorage(root.toString(), 1, 100000000, 0, metrics);
    try (var staged = storage.stage(image())) {
      assertThrows(IOException.class, () -> staged.publish(true));
    }
    assertEquals(false, ReflectionTestUtils.getField(storage, "usageKnown"));
    try (var next = storage.stage(image())) {
      next.publish(false);
      next.accept();
    }
    assertLedgerMatchesFiles(storage);
    assertEquals("occupied directory name", Files.readString(root.resolve(".private")));
    assertEquals(2, metrics.get("campus.upload.usage.reconciliations").counter().count());
  }
}
