package com.sky.storage;

import static com.sky.common.BusinessException.require;

import com.qcloud.cos.*;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class StorageConfiguration {
  @Bean("localBlobStore")
  LocalBlobStore local(@Value("${campus.uploads}") String root) {
    return new LocalBlobStore(Path.of(root));
  }

  @Bean(destroyMethod = "shutdown")
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      name = "campus.storage.backend",
      havingValue = "cos")
  COSClient cosClient(
      @Value("${campus.storage.region:}") String region,
      @Value("${campus.storage.secret-id:}") String secretId,
      @Value("${campus.storage.secret-key:}") String secretKey) {
    require(
        !region.isBlank() && !secretId.isBlank() && !secretKey.isBlank(),
        503,
        "STORAGE_CONFIGURATION",
        "COS地域和访问凭据未配置");
    var config = new ClientConfig(new Region(region));
    config.setHttpProtocol(HttpProtocol.https);
    config.setConnectionTimeout(2000);
    config.setSocketTimeout(5000);
    config.setMaxErrorRetry(0);
    return new COSClient(new BasicCOSCredentials(secretId, secretKey), config);
  }

  @Bean("cosBlobStore")
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      name = "campus.storage.backend",
      havingValue = "cos")
  CosBlobStore cos(
      COSClient client,
      @Value("${campus.storage.product-bucket:}") String products,
      @Value("${campus.storage.evidence-bucket:}") String evidence) {
    return new CosBlobStore(client, products, evidence);
  }
}
