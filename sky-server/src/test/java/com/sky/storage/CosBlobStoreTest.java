package com.sky.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.qcloud.cos.*;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.endpoint.EndpointBuilder;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import com.sky.common.BusinessException;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CosBlobStoreTest {
  static final String PRODUCT = "products-1234567890", EVIDENCE = "evidence-1234567890";
  @TempDir Path root;
  private CosHttpFixture server;
  private COSClient client;
  private CosBlobStore store;
  private final AtomicInteger writes = new AtomicInteger(), reads = new AtomicInteger();
  private final AtomicBoolean failWrite = new AtomicBoolean();
  private final Map<String, byte[]> objects = new HashMap<>();
  private String authorization, acl, query;

  @BeforeEach
  void setup() throws Exception {
    server =
        new CosHttpFixture(
            request -> {
              authorization = request.headers().get("Authorization");
              query = request.uri().getRawQuery();
              String key = request.uri().getPath();
              if (request.method().equals("PUT")) {
                writes.incrementAndGet();
                acl = request.headers().get("x-cos-acl");
                byte[] bytes = request.body();
                objects.put(key, bytes);
                if (failWrite.get()) {
                  byte[] error =
                      "<Error><Code>InternalError</Code><Message>fixture</Message></Error>"
                          .getBytes();
                  return new CosHttpFixture.Response(500, Map.of(), error);
                } else {
                  return new CosHttpFixture.Response(
                      200,
                      Map.of(
                          "ETag",
                          "\""
                              + HexFormat.of()
                                  .formatHex(MessageDigest.getInstance("MD5").digest(bytes))
                              + "\"",
                          "x-cos-version-id",
                          "fixture-version"),
                      new byte[0]);
                }
              } else if (request.method().equals("GET")) {
                reads.incrementAndGet();
                byte[] bytes = objects.get(key);
                return bytes == null
                    ? new CosHttpFixture.Response(404, Map.of(), new byte[0])
                    : new CosHttpFixture.Response(
                        200,
                        Map.of("Content-Type", "image/png", "x-cos-version-id", "fixture-version"),
                        bytes);
              }
              return new CosHttpFixture.Response(405, Map.of(), new byte[0]);
            });
    client = client(server.port());
    store = new CosBlobStore(client, PRODUCT, EVIDENCE);
  }

  static COSClient client(int port) {
    var config = new ClientConfig(new Region("ap-shanghai"));
    config.setHttpProtocol(HttpProtocol.http);
    config.setMaxErrorRetry(0);
    config.setConnectionTimeout(1000);
    config.setSocketTimeout(2000);
    config.setEndpointBuilder(
        new EndpointBuilder() {
          public String buildGeneralApiEndpoint(String bucket) {
            return "127.0.0.1:" + port;
          }

          public String buildGetServiceApiEndpoint() {
            return "127.0.0.1:" + port;
          }
        });
    return new COSClient(
        new BasicCOSCredentials("fixture-secret-id", "fixture-secret-key"), config);
  }

  @AfterEach
  void close() throws IOException {
    client.shutdown();
    server.close();
  }

  @Test
  void officialSdkSignsUploadsKeepsBothBucketsPrivateAndReadsRecordedVersion() throws Exception {
    String id = UUID.randomUUID().toString();
    Path source = root.resolve("staged.png");
    byte[] bytes = new byte[] {1, 2, 3, 4};
    Files.write(source, bytes);
    var published = store.put(id, source, true);
    assertEquals(EVIDENCE, published.bucket());
    assertEquals("images/" + id + ".png", published.key());
    assertEquals("fixture-version", published.version());
    assertTrue(authorization.contains("q-sign-algorithm=sha1"));
    assertTrue(authorization.contains("fixture-secret-id"));
    assertEquals("private", acl);
    try (var content = store.open(published.bucket(), published.key(), published.version(), true)) {
      assertEquals(bytes.length, content.size());
      var output = new ByteArrayOutputStream();
      content.transferTo(output);
      assertArrayEquals(bytes, output.toByteArray());
    }
    assertEquals("versionId=fixture-version", query);
    assertEquals(
        "STORAGE_REFERENCE",
        assertThrows(
                BusinessException.class,
                () -> store.open(EVIDENCE, published.key(), published.version(), false))
            .code());
    assertEquals(1, reads.get());
    var product = store.put(UUID.randomUUID().toString(), source, false);
    assertEquals(PRODUCT, product.bucket());
    assertEquals("private", acl);
  }

  @Test
  void failedPutIsNotBlindlyRetriedAndDestinationExistsBeforeNetworkCall() throws Exception {
    Path source = root.resolve("staged.png");
    Files.write(source, new byte[] {5});
    String id = UUID.randomUUID().toString();
    assertEquals("images/" + id + ".png", store.destination(id, true).key());
    assertEquals(EVIDENCE, store.destination(id, true).bucket());
    failWrite.set(true);
    assertThrows(
        com.qcloud.cos.exception.CosServiceException.class, () -> store.put(id, source, true));
    assertEquals(1, writes.get());
  }
}
