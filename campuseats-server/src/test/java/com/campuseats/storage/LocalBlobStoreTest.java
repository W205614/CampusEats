package com.campuseats.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.campuseats.common.BusinessException;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class LocalBlobStoreTest {
  @TempDir Path root;

  @Test
  void immutableCopiesKeepVisibilitySeparateAndDoNotDependOnTheStagingFile() throws Exception {
    var store = new LocalBlobStore(root.resolve("objects"));
    var source = root.resolve("staged.png");
    Files.write(source, new byte[] {1, 2, 3});
    String id = UUID.randomUUID().toString();
    var published = store.put(id, source, true);
    Files.write(source, new byte[] {9});
    try (var content = store.open("", published.key(), null, true)) {
      var output = new ByteArrayOutputStream();
      content.transferTo(output);
      assertArrayEquals(new byte[] {1, 2, 3}, output.toByteArray());
    }
    assertThrows(FileAlreadyExistsException.class, () -> store.put(id, source, true));
    assertEquals(
        "NOT_FOUND",
        assertThrows(BusinessException.class, () -> store.open("", published.key(), null, false))
            .code());
    assertEquals(
        "NOT_FOUND",
        assertThrows(BusinessException.class, () -> store.open("", "../staged.png", null, false))
            .code());
  }
}
