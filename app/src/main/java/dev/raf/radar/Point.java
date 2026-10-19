package dev.raf.radar;

import java.util.*;
import org.json.*;

public final class Point {
  public String id, name;
  public String address = "";
  public boolean enabled = true;
  public double lat, lon;
  public boolean dynamic, notify = false;
  public int threshold = 200;
  public List<String> tariffs = new ArrayList<>(Arrays.asList("econom", "business", "comfortplus"));

  public Point(String id, String name, double lat, double lon, boolean dynamic) {
    this.id = id;
    this.name = name;
    this.lat = lat;
    this.lon = lon;
    this.dynamic = dynamic;
  }

  public JSONObject json() throws JSONException {
    return new JSONObject()
        .put("id", id)
        .put("name", name)
        .put("address", address)
        .put("enabled", enabled)
        .put("lat", lat)
        .put("lon", lon)
        .put("dynamic", dynamic)
        .put("notify", notify)
        .put("threshold", threshold)
        .put("tariffs", new JSONArray(tariffs));
  }

  public static Point from(JSONObject j) throws JSONException {
    Point p =
        new Point(
            j.getString("id"),
            j.getString("name"),
            j.getDouble("lat"),
            j.getDouble("lon"),
            j.optBoolean("dynamic"));
    p.address = j.optString("address", "");
    p.enabled = j.optBoolean("enabled", true);
    p.notify = j.optBoolean("notify");
    p.threshold = j.optInt("threshold", 200);
    p.tariffs.clear();
    JSONArray t = j.getJSONArray("tariffs");
    for (int i = 0; i < t.length(); i++) p.tariffs.add(t.getString(i));
    return p;
  }

  public static String label(String t) {
    switch (t) {
      case "econom":
        return "Эконом";
      case "business":
        return "Комфорт";
      case "comfortplus":
        return "Комфорт+";
      case "vip":
        return "Бизнес";
      case "child_tariff":
        return "Детский";
      case "minivan":
        return "Минивэн";
      default:
        return t;
    }
  }
}
