package dev.raf.radar;

import org.json.*;

/** Estimate: current passenger quote minus zone baseline, rounded to ten RUB. */
public final class RubCalculator {
  private RubCalculator() {}
  static String field(String tariff) {
    switch (tariff) {
      case "econom": case "start": return "priceEconom";
      case "business": return "priceComfort";
      case "comfortplus": return "priceComfortPlus";
      case "minivan": return "priceMinivan";
      case "vip": return "priceVip";
      case "ultimate": return "pricePremier";
      case "child_tariff": return "priceChildren";
      case "cargo": return "priceCargo";
      case "express": return "priceExpress";
      case "courier": return "priceCourier";
      default: return "";
    }
  }
  public static Integer calculate(JSONObject level, JSONObject base, String tariff) {
    if (base == null || field(tariff).isEmpty()) return null;
    double baseline = base.optDouble(field(tariff), Double.NaN);
    if (!Double.isFinite(baseline) || baseline <= 0) return null;
    // Current anonymous API uses price as a string; older API uses [{value: ...}].
    Object raw = level.opt("price");
    if (raw instanceof JSONArray) {
      JSONObject first = ((JSONArray) raw).optJSONObject(0);
      raw = first == null ? null : first.opt("value");
    }
    if (raw == null || raw == JSONObject.NULL) return null;
    if (raw.toString().matches(".*[0-9]\\s*[–—-]\\s*[0-9].*")) return null;
    String value = raw.toString().replaceAll("[^0-9.,]", "").replace(',', '.');
    try {
      double price = Double.parseDouble(value);
      if (!Double.isFinite(price) || price <= 0 || price > 1000000) return null;
      return (int) (Math.round(Math.max(0, price - baseline) / 10) * 10);
    } catch (NumberFormatException e) { return null; }
  }
}
