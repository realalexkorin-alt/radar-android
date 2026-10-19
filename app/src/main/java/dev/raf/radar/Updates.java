package dev.raf.radar;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import org.json.*;

final class Updates {
  static final String DEFAULT_REPO = UpdateCatalog.REPOSITORY;
  private static volatile boolean busy;
  private static final Map<Activity, Dialog> progressDialogs = new WeakHashMap<>();
  private static boolean alive(Activity a) { return !a.isFinishing() && !a.isDestroyed(); }
  static void check(Activity a, Store store) {
    if (resume(a, true)) return;
    if (busy) { show(a, "Проверка или загрузка уже выполняется"); return; }
    busy = true;
    show(a, "Проверяем обновления Радара…");
    new Thread(() -> {
      try {
        JSONObject release = new JSONObject(read(UpdateCatalog.LATEST));
        if (!UpdateCatalog.newer(release.getString("tag_name"), BuildConfig.VERSION_NAME)) {
          show(a, "Установлена последняя версия Радара"); return;
        }
        JSONObject asset = UpdateCatalog.apk(release);
        a.runOnUiThread(() -> {
          if (!alive(a)) return;
          new AlertDialog.Builder(a).setTitle("Новая версия " + release.optString("tag_name"))
              .setMessage(release.optString("body", "") + "\n\nЗагрузить обновление?")
              .setPositiveButton("Загрузить", (d, w) -> download(a, asset))
              .setNegativeButton("Позже", null).show();
        });
      } catch (Exception e) { show(a, "Обновление недоступно: " + e.getMessage()); }
      finally { busy = false; }
    }, "radar-updates").start();
  }

  static String read(String url) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setConnectTimeout(15000); c.setReadTimeout(20000);
    c.setRequestProperty("User-Agent", "Radar-Android");
    try {
      if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
      try (InputStream in = c.getInputStream()) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) != -1) {
          if (out.size() + n > 2_000_000) throw new IOException("Ответ слишком большой");
          out.write(b, 0, n);
        }
        return out.toString("UTF-8");
      }
    } finally { c.disconnect(); }
  }

  static void download(Activity a, JSONObject asset) {
    if (busy) { show(a, "Загрузка уже выполняется"); return; }
    busy = true;
    android.widget.ProgressBar bar = new android.widget.ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(100); bar.setIndeterminate(asset.optLong("size", 0) <= 0);
    AlertDialog progress = new AlertDialog.Builder(a).setTitle("Загрузка обновления")
        .setView(bar).setNegativeButton("Скрыть", null).create();
    progress.setCanceledOnTouchOutside(false);
    progress.setOnDismissListener(d -> progressDialogs.remove(a));
    progressDialogs.put(a, progress); progress.show();
    new Thread(() -> {
      File file = file(a); Store store = new Store(a);
      try {
        // Revalidate the catalog entry before making the download request.
        JSONObject wrapper = new JSONObject().put("assets", new JSONArray().put(asset));
        UpdateCatalog.apk(wrapper);
        store.prefs.edit().remove("pendingUpdateDigest").apply();
        file.getParentFile().mkdirs();
        HttpURLConnection c = (HttpURLConnection) new URL(asset.getString("browser_download_url")).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        try {
          if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
          try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(file)) {
            byte[] b = new byte[8192]; int n, previous = -1; long total = 0, size = asset.optLong("size", 0);
            while ((n = in.read(b)) != -1) {
              total += n;
              if (total > 150_000_000) throw new IOException("APK слишком большой");
              out.write(b, 0, n);
              int percent = size > 0 ? (int) Math.min(100, total * 100 / size) : 0;
              if (percent != previous) {
                previous = percent; a.runOnUiThread(() -> { if (alive(a)) bar.setProgress(percent); });
              }
            }
          }
        } finally { c.disconnect(); }
        String digest = asset.getString("digest");
        verify(a, file, digest);
        store.prefs.edit().putString("pendingUpdateDigest", digest).apply();
        a.runOnUiThread(() -> { if (alive(a)) offerInstall(a); });
      } catch (Exception e) {
        file.delete(); store.prefs.edit().remove("pendingUpdateDigest").apply();
        show(a, "Обновление: " + e.getMessage());
      } finally {
        busy = false;
        a.runOnUiThread(() -> { if (alive(a) && progress.isShowing()) progress.dismiss(); });
      }
    }, "radar-download").start();
  }

  private static File file(Context c) { return new File(c.getCacheDir(), "updates/radar.apk"); }
  static void verify(Activity a, File file, String digest) throws Exception {
    if (!digest.matches("sha256:[0-9a-fA-F]{64}")) throw new IOException("Нет контрольной суммы APK");
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new FileInputStream(file)) {
      byte[] b = new byte[8192]; int n;
      while ((n = in.read(b)) != -1) md.update(b, 0, n);
    }
    StringBuilder hex = new StringBuilder();
    for (byte b : md.digest()) hex.append(String.format(Locale.US, "%02x", b));
    if (!digest.substring(7).equalsIgnoreCase(hex.toString())) throw new IOException("Контрольная сумма не совпала");
    PackageManager pm = a.getPackageManager();
    PackageInfo candidate = pm.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
    PackageInfo current = pm.getPackageInfo(a.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
    if (candidate == null || !a.getPackageName().equals(candidate.packageName)) throw new IOException("Неверный пакет APK");
    if (candidate.getLongVersionCode() <= current.getLongVersionCode()) throw new IOException("Установлена эта или более новая версия");
    if (candidate.signingInfo == null || current.signingInfo == null || !Arrays.equals(
        candidate.signingInfo.getApkContentsSigners(), current.signingInfo.getApkContentsSigners()))
      throw new IOException("Подпись APK отличается");
  }

  static boolean resume(Activity a) {
    return resume(a, false);
  }
  private static boolean resume(Activity a, boolean requested) {
    Store store = new Store(a);
    String digest = store.prefs.getString("pendingUpdateDigest", "");
    if (busy || digest.isEmpty() || (!requested && !a.getPackageManager().canRequestPackageInstalls())) return false;
    busy = true;
    new Thread(() -> {
      try {
        verify(a, file(a), digest);
        a.runOnUiThread(() -> { if (alive(a)) offerInstall(a); });
      } catch (Exception e) {
        store.prefs.edit().remove("pendingUpdateDigest").apply(); file(a).delete();
        show(a, "Обновление: " + e.getMessage());
        a.runOnUiThread(() -> { if (alive(a) && a instanceof MainActivity) ((MainActivity) a).access.onResume(); });
      } finally { busy = false; }
    }, "radar-install").start();
    return true;
  }

  private static void offerInstall(Activity a) {
    if (!a.getPackageManager().canRequestPackageInstalls()) {
      new AlertDialog.Builder(a).setTitle("Обновление загружено")
          .setMessage("Разрешите Радару установку обновлений. После возврата установка продолжится без повторной загрузки.")
          .setPositiveButton("Разрешить", (d, w) -> a.startActivity(new Intent(
              Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + a.getPackageName()))))
          .setNegativeButton("Позже", null).show();
      return;
    }
    Intent install = new Intent(Intent.ACTION_VIEW).setDataAndType(
        FileProvider.getUriForFile(a, a.getPackageName() + ".files", file(a)),
        "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    new Store(a).prefs.edit().remove("pendingUpdateDigest").apply();
    try { a.startActivity(install); }
    catch (ActivityNotFoundException e) { show(a, "Системный установщик не найден"); }
  }
  static void show(Activity a, String message) {
    a.runOnUiThread(() -> { if (alive(a)) android.widget.Toast.makeText(a, message, android.widget.Toast.LENGTH_LONG).show(); });
  }
  static void detach(Activity a) {
    Dialog dialog = progressDialogs.remove(a);
    if (dialog != null) try { dialog.dismiss(); } catch (IllegalArgumentException ignored) {}
  }
}
