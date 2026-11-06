package com.sky.storage;

import static com.sky.common.BusinessException.require;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.file.*;

public final class LocalBlobStore implements BlobStore {
  private final Path root;

  public LocalBlobStore(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  @Override
  public String backend() {
    return "LOCAL";
  }

  @Override
  public Published destination(String id, boolean privateFile) {
    return new Published("", id + ".png", null);
  }

  @Override
  public Published put(String id, Path source, boolean privateFile) throws IOException {
    var directory = privateFile ? root.resolve(".private") : root;
    Files.createDirectories(directory);
    String key = id + ".png";
    Files.copy(source, directory.resolve(key));
    return new Published("", key, null);
  }

  @Override
  public Content open(String bucket, String key, String version, boolean privateFile)
      throws IOException {
    require(key.matches("[a-f0-9-]{36}\\.png"), 404, "NOT_FOUND", "图片不存在");
    Path path = (privateFile ? root.resolve(".private") : root).resolve(key);
    require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), 404, "NOT_FOUND", "图片不存在");
    var channel = Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    return new Content(Channels.newInputStream(channel), channel.size());
  }
}
