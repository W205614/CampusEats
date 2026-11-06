package com.sky.storage;

import java.io.*;
import java.nio.file.Path;

/**
 * Immutable object access. Visibility is selected by the server, never by a caller supplied URL.
 */
public interface BlobStore {
  record Published(String bucket, String key, String version) {}

  record Content(InputStream input, long size, String version) implements AutoCloseable {
    public Content(InputStream input, long size) {
      this(input, size, null);
    }

    public void transferTo(OutputStream output) throws IOException {
      byte[] buffer = new byte[16 * 1024];
      long remaining = size;
      while (remaining > 0) {
        int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
        if (count < 0) throw new EOFException("Stored image changed during transfer");
        output.write(buffer, 0, count);
        remaining -= count;
      }
    }

    @Override
    public void close() throws IOException {
      input.close();
    }
  }

  String backend();

  Published destination(String id, boolean privateFile);

  Published put(String id, Path source, boolean privateFile) throws IOException;

  Content open(String bucket, String key, String version, boolean privateFile) throws IOException;
}
