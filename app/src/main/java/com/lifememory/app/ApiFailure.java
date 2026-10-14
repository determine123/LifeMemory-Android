package com.lifememory.app;

import android.content.Context;
import java.io.IOException;

/** Never include request headers, credentials or an untrusted response body in diagnostics. */
final class ApiFailure extends IOException {
  final int status;

  boolean retryable() {
    return status == 408 || status == 429 || status >= 500;
  }

  ApiFailure(Context c, int status, String host, String route) {
    super(message(c, status, host, route));
    this.status = status;
  }

  static String message(Context c, int status, String host, String route) {
    boolean en = I18n.english(c);
    String fix;
    switch (status) {
      case 401:
        fix =
            en
                ? "API key rejected. Check that the key belongs to this provider, then replace it"
                    + " and test the saved settings. Repeated retries will not fix credentials."
                : "API密钥鉴权失败。确认密钥属于当前服务商，重新填写并保存，再测试连接；反复重试不能修复错误密钥。";
        break;
      case 402:
        fix =
            en
                ? "Payment or balance problem. Check your provider account balance."
                : "余额或付费状态异常，请检查服务商账户余额。";
        break;
      case 403:
        fix =
            en
                ? "Access denied. Check provider permissions and regional restrictions."
                : "服务商拒绝访问，请检查权限及地区限制。";
        break;
      case 404:
        fix =
            en
                ? "Endpoint or model not found. Check the API base URL, model and provider"
                    + " capabilities."
                : "接口或模型不存在，请检查API地址、模型及服务商支持的能力。";
        break;
      case 429:
        fix =
            en
                ? "Rate or quota limit reached. Check quota and wait before retrying."
                : "请求频率或额度受限，请检查额度并稍后重试。";
        break;
      default:
        fix =
            en
                ? "Provider request failed. Check service availability and configuration."
                : "服务请求失败，请检查服务状态及配置。";
    }
    return "HTTP " + status + " · " + host + route + "\n" + fix;
  }
}
