package com.campuseats.integration;

import static com.campuseats.common.BusinessException.require;

import com.campuseats.common.BusinessException;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.PullSmsSendStatusByPhoneNumberRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** One SDK request per send: a transport timeout never automatically sends another SMS. */
@Component
public class TencentSmsGateway {
  private final Environment environment;
  private final HttpProfile transport;
  private SmsClient sdkClient;

  @org.springframework.beans.factory.annotation.Autowired
  public TencentSmsGateway(Environment environment) {
    this(environment, null);
  }

  TencentSmsGateway(Environment environment, HttpProfile transport) {
    this.environment = environment;
    this.transport = transport;
  }

  public boolean configured() {
    return !value("secret-id").isBlank()
        && !value("secret-key").isBlank()
        && !value("app-id").isBlank()
        && !value("sign-name").isBlank();
  }

  public String send(String phone, String template, List<String> parameters) {
    require(configured() && !template.isBlank(), 503, "SMS_UNCONFIGURED", "短信服务尚未配置");
    var request = new SendSmsRequest();
    request.setPhoneNumberSet(new String[] {"+86" + phone});
    request.setSmsSdkAppId(value("app-id"));
    request.setSignName(value("sign-name"));
    request.setTemplateId(template);
    request.setTemplateParamSet(parameters.toArray(String[]::new));
    try {
      var response = client().SendSms(request);
      var statuses = response.getSendStatusSet();
      require(
          statuses != null && statuses.length == 1 && statuses[0] != null,
          503,
          "SMS_RESULT_UNKNOWN",
          "短信发送结果尚未确认，请稍后重试");
      require("Ok".equals(statuses[0].getCode()), 503, "SMS_REJECTED", "短信服务未受理本次发送，请检查服务配置和额度");
      require(
          ("+86" + phone).equals(statuses[0].getPhoneNumber())
              && statuses[0].getSerialNo() != null
              && !statuses[0].getSerialNo().isBlank(),
          503,
          "SMS_RESULT_UNKNOWN",
          "短信受理凭据不完整，请稍后核实");
      return statuses[0].getSerialNo();
    } catch (TencentCloudSDKException ex) {
      throw new BusinessException(503, "SMS_RESULT_UNKNOWN", "短信发送结果尚未确认，请稍后重试");
    }
  }

  public String receipt(String phone, String reference, Instant sentAt) {
    var request = new PullSmsSendStatusByPhoneNumberRequest();
    request.setSmsSdkAppId(value("app-id"));
    request.setPhoneNumber("+86" + phone);
    request.setBeginTime(sentAt.minusSeconds(60).getEpochSecond());
    request.setEndTime(Instant.now().getEpochSecond());
    request.setOffset(0L);
    request.setLimit(100L);
    try {
      var response = client().PullSmsSendStatusByPhoneNumber(request);
      for (var result : response.getPullSmsSendStatusSet())
        if (reference.equals(result.getSerialNo())) return result.getReportStatus();
      return "PENDING";
    } catch (TencentCloudSDKException ex) {
      throw new BusinessException(503, "SMS_RECEIPT_UNAVAILABLE", "短信回执查询暂不可用");
    }
  }

  private synchronized SmsClient client() {
    if (sdkClient != null) return sdkClient;
    var profile = transport == null ? new HttpProfile() : transport;
    if (transport == null) profile.setEndpoint("sms.tencentcloudapi.com");
    profile.setConnTimeout(2);
    profile.setReadTimeout(5);
    profile.setWriteTimeout(5);
    // Sending SMS is chargeable and has no request-id deduplication: disable transport replay too.
    profile.setHttpClient(
        new okhttp3.OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.SECONDS)
            .build());
    var clientProfile = new ClientProfile();
    clientProfile.setHttpProfile(profile);
    clientProfile.setDebug(false);
    sdkClient =
        new SmsClient(
            new Credential(value("secret-id"), value("secret-key")),
            environment.getProperty("campus.notifications.sms.region", "ap-guangzhou"),
            clientProfile);
    return sdkClient;
  }

  private String value(String key) {
    return environment.getProperty("campus.notifications.sms." + key, "");
  }
}
