package dev.raf.radar;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public final class TaxiClient {
  public static final String ENDPOINT = "https://ya-authproxy.taxi.yandex.ru/3.0/routestats";

  public static final class ApiException extends IOException {
    public final int status;

    ApiException(int s) {
      super(
          s == 401 || s == 403
              ? "Доступ ограничен. Мониторинг приостановлен"
              : s == 429 ? "Лимит запросов. Пауза 15 минут" : "Ошибка сервиса: " + s);
      status = s;
    }
  }

  public static JSONObject request(Point p) throws JSONException {
    JSONArray ts = new JSONArray();
    for (String t : p.tariffs) ts.put(new JSONObject().put("class", t));
    return new JSONObject()
        .put("route", new JSONArray().put(new JSONArray().put(p.lon).put(p.lat)))
        .put("selected_class", p.tariffs.isEmpty() ? "econom" : p.tariffs.get(0))
        .put("payment", new JSONObject().put("type", "cash"))
        .put("tariff_requirements", ts)
        .put("supports_explicit_antisurge", true)
        .put("supports_paid_options", true)
        .put("extended_description", true)
        .put("format_currency", true)
        .put("with_title", true)
        .put("summary_version", 2)
        .put("is_lightweight", false)
        .put("skip_estimated_waiting", true);
  }

  public List<Store.Reading> fetch(Point p) throws Exception {
    String cookie = android.webkit.CookieManager.getInstance().getCookie("https://taxi.yandex.ru");
    return parse(post(request(p), cookie), p.tariffs, System.currentTimeMillis());
  }

  private JSONObject post(JSONObject body, String cookie) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(15000);
    c.setInstanceFollowRedirects(false);
    c.setRequestMethod("POST");
    c.setDoOutput(true);
    c.setRequestProperty("Content-Type", "application/json");
    c.setRequestProperty("User-Agent", "Radar/0.1 Android");
    c.setRequestProperty("Accept-Language", "ru");
    if (cookie != null) c.setRequestProperty("Cookie", cookie);
    try {
      try (OutputStream o = c.getOutputStream()) {
        o.write(body.toString().getBytes(StandardCharsets.UTF_8));
      }
      int status = c.getResponseCode();
      if (status != 200) throw new ApiException(status);
      try (InputStream in = c.getInputStream()) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n, total = 0;
        while ((n = in.read(b)) != -1) {
          total += n;
          if (total > 2_000_000) throw new IOException("Ответ слишком большой");
          out.write(b, 0, n);
        }
        return new JSONObject(out.toString("UTF-8"));
      }
    } finally {
      c.disconnect();
    }
  }

  public static List<Store.Reading> parse(JSONObject j, List<String> selected, long now)
      throws JSONException {
    JSONArray levels = j.optJSONArray("service_levels");
    if (levels == null) throw new JSONException("Сервис изменил формат ответа");
    List<Store.Reading> out = new ArrayList<>();
    for (String tariff : selected) {
      JSONObject match = null;
      for (int i = 0; i < levels.length(); i++) {
        JSONObject x = levels.optJSONObject(i);
        if (x != null && tariff.equals(x.optString("class"))) {
          match = x;
          break;
        }
      }
      Double ratio = null;
      String status = "Тариф недоступен";
      if (match != null) {
        JSONObject paid = match.optJSONObject("paid_options");
        if (paid != null && paid.has("value")) {
          double v = paid.optDouble("value", Double.NaN);
          if (Double.isFinite(v) && v > 0) ratio = v;
        }
        status = ratio == null ? "Нет данных о надбавке" : "Множитель спроса · не ₽";
      }
      // Passenger fare and paid_options.value are NOT a verified driver RUB surcharge.
      out.add(new Store.Reading(tariff, null, ratio, now, status));
    }
    return out;
  }
}
