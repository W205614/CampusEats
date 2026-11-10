package com.campuseats.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;

import com.campuseats.business.ImageStorage;
import com.campuseats.web.ProductImageController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ImageResponseBoundaryTest {
  @TempDir Path root;

  @Test
  void uploadedImageIsReencodedAndReturnedAsNonSniffablePngInsteadOfHtml() throws Exception {
    var upload = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", upload);
    upload.write("<script>alert('untrusted')</script>".getBytes(StandardCharsets.UTF_8));
    var storage = new ImageStorage(root.toString(), 1, 20 * 1024 * 1024, 0,
        new SimpleMeterRegistry());
    String id;
    try (var stage = storage.stage(
        new MockMultipartFile("file", "image.png", "image/png", upload.toByteArray()))) {
      id = stage.publish(false);
      stage.accept();
    }
    Path published = root.resolve(id + ".png");
    var images = mock(StoredImages.class);
    when(images.open(id, false)).thenReturn(
        new BlobStore.Content(Files.newInputStream(published), Files.size(published)));
    var mvc = standaloneSetup(new ProductImageController(images)).build();
    byte[] response = mvc.perform(get("/uploads/" + id + ".png"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andReturn().getResponse().getContentAsByteArray();
    assertArrayEquals(Files.readAllBytes(published), response);
    assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(response)));
    assertFalse(new String(response, StandardCharsets.ISO_8859_1).contains("<script>"));
  }
}
