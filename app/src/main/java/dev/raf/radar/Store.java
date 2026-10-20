package dev.raf.radar;

import android.content.*;
import java.util.*;
import org.json.*;

public final class Store {
  public final android.content.SharedPreferences prefs;

  public Store(Context c) {
    prefs = c.getSharedPreferences("radar", Context.MODE_PRIVATE);
    migrateEconomy();
    if (!prefs.getBoolean("gpsWidgetV1",false)) prefs.edit().putString("overlayPoint","me").putBoolean("gpsWidgetV1",true).apply();
  }

  private void migrateEconomy() {
    if (prefs.getBoolean("economyOnlyV1",false)) return;
    // Preserve point identity, coordinates, thresholds, switches and overlay position.
    save(points());
    prefs.edit().putStringSet("widgetTariffs",Collections.singleton("econom"))
        .putBoolean("sounds",prefs.getBoolean("sounds",prefs.getBoolean("startupSound",true)))
        .putBoolean("dark",true).putBoolean("economyOnlyV1",true).apply();
  }

  public Point widgetPoint() {
    for (Point point : points()) if (point.dynamic) return point;
    throw new IllegalStateException("Dynamic point missing");
  }

  public Reading widgetReading() {
    if (!LocationState.fresh(this)) return null;
    for (Reading r : readings("me")) if ("econom".equals(r.tariff) && r.rub != null && r.fresh()) return r;
    return null;
  }

  public List<String> widgetTariffs() { return Collections.singletonList("econom"); }

  public void widgetTariffs(Collection<String> values) {
    prefs.edit().putStringSet("widgetTariffs",Collections.singleton("econom")).apply();
  }

  public synchronized List<Point> points() {
    List<Point> a = new ArrayList<>();
    try {
      JSONArray j = new JSONArray(prefs.getString("points", "[]"));
      for (int i = 0; i < j.length(); i++) a.add(Point.from(j.getJSONObject(i)));
    } catch (Exception ignored) {
    }
    if (a.stream().noneMatch(p -> p.dynamic)) {
      a.add(0, new Point("me", "Моя геопозиция", 0, 0, true));
      save(a);
    }
    return a;
  }

  public synchronized void save(List<Point> a) {
    JSONArray j = new JSONArray();
    try {
      for (Point p : a) j.put(p.json());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    prefs.edit().putString("points", j.toString()).apply();
  }

  public void update(Point p) {
    List<Point> a = points();
    for (int i = 0; i < a.size(); i++)
      if (a.get(i).id.equals(p.id)) {
        a.set(i, p);
        save(a);
        return;
      }
    a.add(p);
    save(a);
  }

  public void remove(String id) {
    List<Point> a = points();
    a.removeIf(p -> p.id.equals(id) && !p.dynamic);
    save(a);
  }

  public static final class Reading {
    public String tariff, status;
    public Integer rub;
    public Double ratio;
    public long time;

    public Reading(String t, Integer r, Double x, long ts, String s) {
      tariff = t;
      rub = r;
      ratio = x;
      time = ts;
      status = s;
    }

    public boolean fresh() {
      long age = System.currentTimeMillis() - time;
      return time > 0 && age >= 0 && age <= 180000;
    }

    public String value() {
      return rub != null
          ? (rub > 0 ? "+" : "") + rub + " ₽"
          : "—";
    }
  }

  public void readings(String id, List<Reading> a) {
    JSONArray j = new JSONArray();
    try {
      for (Reading r : a)
        if ("econom".equals(r.tariff)) j.put(
            new JSONObject()
                .put("tariff", r.tariff)
                .put("rub", r.rub)
                .put("ratio", r.ratio)
                .put("time", r.time)
                .put("status", r.status));
    } catch (Exception ignored) {
    }
    prefs.edit().putString("read:" + id, j.toString()).apply();
  }

  public List<Reading> readings(String id) {
    List<Reading> a = new ArrayList<>();
    try {
      JSONArray j = new JSONArray(prefs.getString("read:" + id, "[]"));
      for (int i = 0; i < j.length(); i++) {
        JSONObject r = j.getJSONObject(i);
        if (!"econom".equals(r.optString("tariff"))) continue;
        a.add(
            new Reading(
                r.getString("tariff"),
                r.has("rub") ? r.getInt("rub") : null,
                r.has("ratio") ? r.getDouble("ratio") : null,
                r.getLong("time"),
                r.getString("status")));
      }
    } catch (Exception ignored) {
    }
    return a;
  }
}
