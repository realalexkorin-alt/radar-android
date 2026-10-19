package dev.raf.radar;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import org.json.*;

final class Updates {
  static final String DEFAULT_REPO = "realalexkorin-alt/radar-android";
  static void check(Activity a, Store s) {
    String repo = s.prefs.getString("repo", DEFAULT_REPO).trim();
    if (repo.isEmpty()) repo = DEFAULT_REPO;
    final String repository = repo;
    if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
      show(a, "Укажи репозиторий GitHub в формате владелец/название.");
      return;
    }
    show(a, "Проверяем GitHub Releases…");
    new Thread(
            () -> {
              try {
                JSONObject j =
                    new JSONObject(
                        read("https://api.github.com/repos/" + repository + "/releases/latest"));
                JSONArray assets = j.getJSONArray("assets");
                JSONObject apk = null;
                for (int i = 0; i < assets.length(); i++) {
                  JSONObject x = assets.getJSONObject(i);
                  if (x.getString("name").equals("radar.apk")) apk = x;
                }
                if (apk == null) throw new IOException("В релизе нет radar.apk");
                String version = j.optString("tag_name");
                final JSONObject chosen = apk;
                a.runOnUiThread(
                    () ->
                        new AlertDialog.Builder(a)
                            .setTitle("Релиз " + version)
                            .setMessage(
                                j.optString("body", "")
                                    + "\n\nПосле загрузки проверим подпись и версию APK.")
                            .setPositiveButton("Загрузить", (d, w) -> download(a, chosen))
                            .setNegativeButton("Отмена", null)
                            .show());
              } catch (Exception e) {
                show(a, "Обновление недоступно: " + e.getMessage());
              }
            },
            "radar-updates")
        .start();
  }

  static String read(String url) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(20000);
    c.setRequestProperty("User-Agent", "Radar-Android");
    try {
      if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
      try (InputStream in = c.getInputStream()) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
          if (out.size() + count > 2_000_000) throw new IOException("Ответ слишком большой");
          out.write(buffer, 0, count);
        }
        return out.toString("UTF-8");
      }
    } finally {
      c.disconnect();
    }
  }

  static void download(Activity a, JSONObject asset) {
    new Thread(
            () -> {
              File f = new File(a.getCacheDir(), "updates/radar.apk");
              try {
                String url = asset.getString("browser_download_url"),
                    digest = asset.optString("digest");
                if (!url.startsWith("https://github.com/")
                    || !digest.matches("sha256:[0-9a-fA-F]{64}"))
                  throw new IOException("Нет контрольной суммы GitHub SHA-256");
                f.getParentFile().mkdirs();
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try {
                  if (c.getResponseCode() != 200)
                    throw new IOException("HTTP " + c.getResponseCode());
                  try (InputStream in = c.getInputStream();
                      OutputStream out = new FileOutputStream(f)) {
                    byte[] b = new byte[8192];
                    int n;
                    long total = 0;
                    while ((n = in.read(b)) != -1) {
                      total += n;
                      if (total > 150_000_000) throw new IOException("APK слишком большой");
                      out.write(b, 0, n);
                      md.update(b, 0, n);
                    }
                  }
                } finally {
                  c.disconnect();
                }
                StringBuilder hex = new StringBuilder();
                for (byte b : md.digest()) hex.append(String.format("%02x", b));
                if (!digest.substring(7).equalsIgnoreCase(hex.toString()))
                  throw new IOException("Контрольная сумма не совпала");
                PackageManager pm = a.getPackageManager();
                PackageInfo candidate =
                    pm.getPackageArchiveInfo(
                        f.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
                PackageInfo current =
                    pm.getPackageInfo(a.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (candidate == null || !a.getPackageName().equals(candidate.packageName))
                  throw new IOException("Неверный пакет APK");
                if (candidate.getLongVersionCode() <= current.getLongVersionCode())
                  throw new IOException("Установлена эта или более новая версия");
                if (candidate.signingInfo == null
                    || !Arrays.equals(
                        candidate.signingInfo.getApkContentsSigners(),
                        current.signingInfo.getApkContentsSigners()))
                  throw new IOException("Подпись APK отличается");
                a.runOnUiThread(
                    () -> {
                      if (!pm.canRequestPackageInstalls()) {
                        new AlertDialog.Builder(a)
                            .setMessage(
                                "Разреши установку обновлений для Радара, затем повтори проверку.")
                            .setPositiveButton(
                                "Настройки",
                                (d, w) ->
                                    a.startActivity(
                                        new Intent(
                                            android.provider.Settings
                                                .ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            android.net.Uri.parse(
                                                "package:" + a.getPackageName()))))
                            .setNegativeButton("Позже", null)
                            .show();
                        return;
                      }
                      Intent install =
                          new Intent(Intent.ACTION_VIEW)
                              .setDataAndType(
                                  FileProvider.getUriForFile(a, a.getPackageName() + ".files", f),
                                  "application/vnd.android.package-archive")
                              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                      a.startActivity(install);
                    });
              } catch (Exception e) {
                f.delete();
                show(a, "Обновление: " + e.getMessage());
              }
            },
            "radar-download")
        .start();
  }

  static void show(Activity a, String message) {
    a.runOnUiThread(
        () -> android.widget.Toast.makeText(a, message, android.widget.Toast.LENGTH_LONG).show());
  }
}
