package dev.raf.radar;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Classifies a bounded response without storing server text, credentials or location. */
final class ServiceError {
  static String classify(InputStream input, String contentType) {
    if (input == null) return "response_empty";
    try {
      ByteArrayOutputStream data = new ByteArrayOutputStream();
      byte[] buffer = new byte[512];
      while (data.size() < 8192) {
        int n = input.read(buffer, 0, Math.min(buffer.length, 8192 - data.size()));
        if (n < 0) break;
        if (n == 0) break;
        data.write(buffer, 0, n);
      }
      String text = new String(data.toByteArray(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
      return classifyText(text,contentType);
    } catch (Exception ignored) {
      return "response_unreadable";
    }
  }
  static String classifyText(String body,String contentType){
    String text=body==null?"":body.toLowerCase(Locale.ROOT);
    if(text.contains("smartcaptcha")||text.contains("/showcaptcha")||text.contains("verify you are human")||text.contains("вы не робот"))return "response_verification";
    if(text.contains("invalid_csrf_token")||text.contains("csrf token"))return "response_csrf";
    if(text.contains("access denied")||text.contains("доступ запрещ"))return "response_access_denied";
      if (text.trim().isEmpty()) return "response_empty";
      if (text.contains("timeout") || text.contains("timed out")) return "response_timeout";
      if (text.contains("rate limit") || text.contains("rate_limit") || text.contains("too many requests")) return "response_rate_limit";
      if (text.contains("unavailable")) return "response_unavailable";
      if (text.contains("bad gateway")) return "response_bad_gateway";
      if (text.contains("unauthorized") || text.contains("forbidden")) return "response_access_denied";
      if (text.contains("internal server error") || text.contains("internal_error")) return "response_internal_error";
      if (text.contains("upstream")) return "response_upstream_error";
      String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
      if (type.contains("json") || text.trim().startsWith("{")) return "response_json_other";
      if (type.contains("html") || text.trim().startsWith("<")) return "response_html_other";
      return "response_text_other";
  }
}
