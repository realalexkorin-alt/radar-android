package dev.raf.radar;
import java.io.IOException;
import org.json.*;

public final class UpdateCatalog {
  public static final String REPOSITORY = "realalexkorin-alt/radar-android";
  public static final String LATEST = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
  private UpdateCatalog() {}
  public static boolean newer(String tag, String current) throws IOException {
    String a = tag.startsWith("v") ? tag.substring(1) : tag;
    String b = current.startsWith("v") ? current.substring(1) : current;
    if (!a.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?")
        || !b.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?"))
      throw new IOException("Непонятный номер версии релиза");
    String[] x = a.split("-", 2)[0].split("\\."), y = b.split("-", 2)[0].split("\\.");
    try {
      for (int i = 0; i < 3; i++) {
        int comparison = Long.compare(Long.parseLong(x[i]), Long.parseLong(y[i]));
        if (comparison != 0) return comparison > 0;
      }
      return a.indexOf('-') < 0 && b.indexOf('-') >= 0;
    } catch (NumberFormatException e) { throw new IOException("Непонятный номер версии релиза", e); }
  }
  public static JSONObject apk(JSONObject release) throws Exception {
    if (release.optBoolean("draft") || release.optBoolean("prerelease"))
      throw new IOException("Релиз ещё не опубликован в канале обновлений");
    JSONArray assets = release.getJSONArray("assets");
    for (int i = 0; i < assets.length(); i++) {
      JSONObject asset = assets.getJSONObject(i);
      if (!"radar.apk".equals(asset.optString("name"))) continue;
      if (!asset.optString("browser_download_url").startsWith(
          "https://github.com/" + REPOSITORY + "/releases/download/"))
        throw new IOException("APK находится вне репозитория Радара");
      if (!asset.optString("digest").matches("sha256:[0-9a-fA-F]{64}"))
        throw new IOException("В релизе нет контрольной суммы APK");
      return asset;
    }
    throw new IOException("В релизе нет radar.apk");
  }
}
