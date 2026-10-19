package com.sky.business;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.common.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ImageStorageTest {
  @TempDir Path root;

  ImageStorage storage(long quota, long free) {
    return new ImageStorage(root.toString(), 1, quota, free, new SimpleMeterRegistry());
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
    assertEquals(
        "INVALID_IMAGE",
        assertThrows(BusinessException.class, () -> storage(20000000, 0).stage(file)).code());
    try (var files = Files.walk(root)) {
      assertEquals(0, files.filter(Files::isRegularFile).count());
    }
  }
}
