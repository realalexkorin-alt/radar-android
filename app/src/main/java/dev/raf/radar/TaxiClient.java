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
    public final String reason;

    ApiException(int s) {this(s,"response_unknown");}
    ApiException(int s,String cause) {
      super(
          s == 401 || s == 403
              ? "Доступ ограничен. Мониторинг приостановлен"
              : s == 429 ? "Лимит запросов. Пауза 15 минут" : "Ошибка сервиса: " + s);
      status = s;reason=cause;
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

  private final android.content.Context context;
  public TaxiClient(android.content.Context c) { context = c.getApplicationContext(); }

  public List<Store.Reading> fetch(Point p) throws Exception {
    Map<String,String> session=YandexSession.headers(context);
    JSONObject body=request(p);if(session.containsKey("X-YaTaxi-UserId"))body.put("id",session.get("X-YaTaxi-UserId"));
    JSONObject response = BrowserFare.fetch(context, body, session);
    String zone = "";
    try {
      String key = "zone:" + p.id;
      android.content.SharedPreferences prefs = context.getSharedPreferences("radar", 0);
      long now = System.currentTimeMillis();
      double oldLat = Double.longBitsToDouble(prefs.getLong(key + ":lat", 0));
      double oldLon = Double.longBitsToDouble(prefs.getLong(key + ":lon", 0));
      if (now - prefs.getLong(key + ":time", 0) < 3600000
          && Math.abs(oldLat - p.lat) < 0.002 && Math.abs(oldLon - p.lon) < 0.002) {
        zone = prefs.getString(key, "");
      } else {
        zone = post("https://tc.mobile.yandex.net/3.0/nearestzone",
            new JSONObject().put("id", "").put("point", new JSONArray().put(p.lon).put(p.lat)), null)
            .optString("nearest_zone", "");
        prefs.edit().putString(key, zone).putLong(key + ":time", now)
            .putLong(key + ":lat", Double.doubleToLongBits(p.lat))
            .putLong(key + ":lon", Double.doubleToLongBits(p.lon)).apply();
      }
    } catch (Exception e) {Diagnostics.error("taxi","zone_lookup_failed",e,0,-1,-1,null);}
    JSONObject table;
    try (InputStream in = context.getAssets().open("tariffs.json")) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192]; int n;
      while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
      table = new JSONObject(out.toString("UTF-8"));
    }
    return parse(response, p.tariffs, System.currentTimeMillis(), table.optJSONObject(zone), zone);
  }

  private JSONObject post(String endpoint, JSONObject body, String cookie) throws Exception {return post(endpoint,body,cookie,Collections.emptyMap());}
  private JSONObject post(String endpoint, JSONObject body, String cookie,Map<String,String> session) throws Exception {
    long started=android.os.SystemClock.elapsedRealtime();String stage=endpoint.equals(ENDPOINT)?"routestats":"nearestzone";
    HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(15000);
    c.setInstanceFollowRedirects(false);
    c.setRequestMethod("POST");
    c.setDoOutput(true);
    c.setRequestProperty("Content-Type", "application/json");
    c.setRequestProperty("User-Agent", "Radar/0.1 Android");
    c.setRequestProperty("Accept-Language", "ru");
    if (cookie != null) c.setRequestProperty("Cookie", cookie);
    if(ENDPOINT.equals(endpoint))for(Map.Entry<String,String> h:session.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
    try {
      try (OutputStream o = c.getOutputStream()) {
        o.write(body.toString().getBytes(StandardCharsets.UTF_8));
      }
      int status = c.getResponseCode();
      Diagnostics.record("taxi",stage,status==200?"info":"error",null,status,android.os.SystemClock.elapsedRealtime()-started,-1,null);
      if(status==401&&ENDPOINT.equals(endpoint))YandexSession.invalidate(context,session);
      if (status != 200) {
        String detail;
        try (InputStream error = c.getErrorStream()) {
          detail = ServiceError.classify(error, c.getContentType());
        } catch (Exception ignored) { detail = "response_unreadable"; }
        Diagnostics.error("taxi", detail, null, status, -1, -1, null);
        throw new ApiException(status,detail);
      }
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
    } catch(Exception e){if(!(e instanceof ApiException))Diagnostics.error("taxi",stage+"_failed",e,0,android.os.SystemClock.elapsedRealtime()-started,-1,null);throw e;} finally {
      c.disconnect();
    }
  }

  public static List<Store.Reading> parse(JSONObject j, List<String> selected, long now)
      throws JSONException {
    return parse(j, selected, now, null, "");
  }

  public static List<Store.Reading> parse(JSONObject j, List<String> selected, long now,
      JSONObject base, String zone) throws JSONException {
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
      Integer rub = null;
      if (match != null && "RUB".equals(j.optJSONObject("currency_rules") == null ? "" : j.optJSONObject("currency_rules").optString("code"))) {
        rub = RubCalculator.calculate(match, base, tariff);
        if (rub != null) status = "Расчётная надбавка · " + zone + " · база 19.10.2026";
        else status = "Нет базовой цены · " + (zone.isEmpty() ? "зона неизвестна" : zone);
      }
      out.add(new Store.Reading(tariff, rub, ratio, now, status));
    }
    return out;
  }
}
