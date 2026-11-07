package com.campuseats.storage;

import static com.campuseats.common.BusinessException.require;

import com.qcloud.cos.COS;
import com.qcloud.cos.exception.*;
import com.qcloud.cos.model.*;
import com.campuseats.common.BusinessException;
import java.io.*;
import java.nio.file.Path;
import java.util.UUID;

/** Both buckets are private; reads are streamed through the existing application URLs. */
public final class CosBlobStore implements BlobStore {
  private final COS cos;
  private final String productBucket, evidenceBucket;

  public CosBlobStore(COS cos, String productBucket, String evidenceBucket) {
    require(
        !productBucket.isBlank()
            && !evidenceBucket.isBlank()
            && !productBucket.equals(evidenceBucket),
        503,
        "STORAGE_CONFIGURATION",
        "COS商品与举证需要配置不同的私有存储桶");
    this.cos = cos;
    this.productBucket = productBucket;
    this.evidenceBucket = evidenceBucket;
  }

  @Override
  public String backend() {
    return "COS";
  }

  @Override
  public Published destination(String id, boolean privateFile) {
    UUID.fromString(id);
    return new Published(
        privateFile ? evidenceBucket : productBucket, "images/" + id + ".png", null);
  }

  @Override
  public Published put(String id, Path source, boolean privateFile) throws IOException {
    var destination = destination(id, privateFile);
    String bucket = destination.bucket();
    String key = destination.key();
    var request = new PutObjectRequest(bucket, key, source.toFile());
    var metadata = new ObjectMetadata();
    metadata.setContentType("image/png");
    request.setMetadata(metadata);
    request.setCannedAcl(CannedAccessControlList.Private);
    var result = cos.putObject(request);
    require(
        result.getVersionId() != null
            && !result.getVersionId().isBlank()
            && !result.getVersionId().equals("null"),
        503,
        "STORAGE_VERSIONING_REQUIRED",
        "COS存储桶必须启用版本控制");
    return new Published(bucket, key, result.getVersionId());
  }

  @Override
  public Content open(String bucket, String key, String version, boolean privateFile)
      throws IOException {
    require(
        bucket.equals(privateFile ? evidenceBucket : productBucket)
            && key.matches("images/[a-f0-9-]{36}\\.png"),
        503,
        "STORAGE_REFERENCE",
        "图片存储引用不正确");
    var request = new GetObjectRequest(bucket, key);
    if (version != null && !version.isBlank()) request.setVersionId(version);
    try {
      var object = cos.getObject(request);
      return new Content(
          object.getObjectContent(),
          object.getObjectMetadata().getContentLength(),
          object.getObjectMetadata().getVersionId());
    } catch (CosServiceException failure) {
      if (failure.getStatusCode() == 404) throw new BusinessException(404, "NOT_FOUND", "图片不存在");
      throw new BusinessException(503, "STORAGE_UNAVAILABLE", "图片存储暂不可用");
    } catch (CosClientException failure) {
      throw new BusinessException(503, "STORAGE_UNAVAILABLE", "图片存储暂不可用");
    }
  }
}
