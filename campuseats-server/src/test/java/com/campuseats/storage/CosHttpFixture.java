package com.campuseats.storage;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Keeps COS header spelling intact; JDK HttpServer canonicalizes it incompatibly with this SDK. */
final class CosHttpFixture implements AutoCloseable {
  record Request(String method, URI uri, Map<String, String> headers, byte[] body) {}

  record Response(int status, Map<String, String> headers, byte[] body) {}

  interface Handler {
    Response respond(Request request) throws Exception;
  }

  private final ServerSocket listener;
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
  private volatile boolean closed;

  CosHttpFixture(Handler handler) throws IOException {
    listener = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
    workers.submit(
        () -> {
          while (!closed) {
            try {
              Socket socket = listener.accept();
              workers.submit(() -> serve(socket, handler));
            } catch (IOException failure) {
              if (!closed) throw new UncheckedIOException(failure);
            }
          }
        });
  }

  int port() {
    return listener.getLocalPort();
  }

  private static void serve(Socket socket, Handler handler) {
    try (socket) {
      socket.setSoTimeout(5000);
      var input = new BufferedInputStream(socket.getInputStream());
      var output = socket.getOutputStream();
      String requestLine = line(input);
      if (requestLine == null) return;
      String[] parts = requestLine.split(" ", 3);
      Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      for (String header; (header = line(input)) != null && !header.isEmpty(); ) {
        int colon = header.indexOf(':');
        headers.put(header.substring(0, colon), header.substring(colon + 1).trim());
      }
      if ("100-continue".equalsIgnoreCase(headers.get("Expect"))) {
        output.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
      }
      int length = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
      byte[] body = input.readNBytes(length);
      if (body.length != length) throw new EOFException("Incomplete fixture request");
      var response = handler.respond(new Request(parts[0], URI.create(parts[1]), headers, body));
      var head =
          new StringBuilder("HTTP/1.1 ")
              .append(response.status())
              .append(" Result\r\n")
              .append("Connection: close\r\nContent-Length: ")
              .append(response.body().length)
              .append("\r\n");
      response
          .headers()
          .forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
      output.write(head.append("\r\n").toString().getBytes(StandardCharsets.ISO_8859_1));
      output.write(response.body());
      output.flush();
    } catch (Exception failure) {
      throw new RuntimeException(failure);
    }
  }

  private static String line(InputStream input) throws IOException {
    var bytes = new ByteArrayOutputStream();
    for (int value; (value = input.read()) >= 0; ) {
      if (value == '\n') return bytes.toString(StandardCharsets.ISO_8859_1).replace("\r", "");
      if (bytes.size() >= 8192) throw new IOException("Oversized fixture header");
      bytes.write(value);
    }
    return bytes.size() == 0 ? null : bytes.toString(StandardCharsets.ISO_8859_1);
  }

  @Override
  public void close() throws IOException {
    closed = true;
    listener.close();
    workers.close();
  }
}
