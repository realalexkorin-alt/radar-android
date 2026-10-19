package dev.raf.radar;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import java.util.*;

/** Entry checks plus feature-specific guards; Android always grants the access itself. */
final class Access {
  static final int REQUEST = 42;
  private final MainActivity activity;
  private final Store store;
  private AlertDialog dialog;
  private boolean checked, requesting, returning;

  Access(MainActivity a, Store s) { activity = a; store = s; }
  static boolean location(Context c) {
    return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }
  static boolean notifications(Context c) {
    NotificationManager manager = c.getSystemService(NotificationManager.class);
    if (!manager.areNotificationsEnabled()) return false;
    NotificationChannel channel = manager.getNotificationChannel("alerts");
    return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
  }
  static boolean monitorReady(Context c) { return location(c) && notifications(c); }
  static String missing(Context c) {
    if (!location(c)) return "Точная геопозиция";
    if (!notifications(c)) return "Уведомления";
    if (!Settings.canDrawOverlays(c)) return "Поверх других приложений";
    if (!c.getPackageManager().canRequestPackageInstalls()) return "Установка обновлений";
    return null;
  }
  void onResume() {
    if (!monitorReady(activity) && MonitorService.running)
      activity.stopService(new Intent(activity, MonitorService.class));
    if (requesting || dialog != null || activity.isFinishing()) return;
    if (returning) { returning = false; advance(false); }
    else if (!checked) { checked = true; advance(true); }
  }
  void onStop() { if (!requesting && !returning && dialog == null) checked = false; }
  void request() { if (!requesting && dialog == null) advance(true); }
  private boolean mayRequest(String permission) {
    return !store.prefs.getBoolean("asked:" + permission, false)
        || activity.shouldShowRequestPermissionRationale(permission);
  }
  private void advance(boolean runtime) {
    activity.onAccessChanged();
    List<String> permissions = new ArrayList<>();
    if (runtime && !location(activity) && mayRequest(Manifest.permission.ACCESS_FINE_LOCATION)) {
      permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
      permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    }
    if (runtime && Build.VERSION.SDK_INT >= 33
        && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        && mayRequest(Manifest.permission.POST_NOTIFICATIONS)) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
    if (!permissions.isEmpty()) {
      for (String p : permissions) store.prefs.edit().putBoolean("asked:" + p, true).apply();
      requesting = true;
      activity.requestPermissions(permissions.toArray(new String[0]), REQUEST);
      return;
    }
    String missing = missing(activity);
    if (missing == null) return;
    String explanation = missing.equals("Точная геопозиция")
        ? "Нужна для точного положения вашей точки и мониторинга. Разрешите геопозицию при использовании приложения и выберите точное местоположение."
        : missing.equals("Уведомления") ? "Нужны для уведомлений о надбавке и состоянии мониторинга."
        : missing.equals("Поверх других приложений") ? "Нужно для виджета поверх карты и других приложений."
        : "Нужно для установки скачанного обновления Радара. Доступ к файлам телефона не требуется.";
    dialog = new AlertDialog.Builder(activity).setTitle("Разрешения для Радара")
        .setMessage(missing + "\n\n" + explanation)
        .setPositiveButton("Разрешить", (d, w) -> openNext())
        .setNegativeButton("Позже", null).create();
    dialog.setOnDismissListener(d -> dialog = null);
    dialog.show();
  }
  private void openNext() {
    String missing = missing(activity);
    if (missing == null) { activity.onAccessChanged(); return; }
    Intent intent;
    android.net.Uri uri = android.net.Uri.parse("package:" + activity.getPackageName());
    if (missing.equals("Поверх других приложений"))
      intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri);
    else if (missing.equals("Установка обновлений"))
      intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri);
    else if (missing.equals("Уведомления")) {
      NotificationChannel channel = activity.getSystemService(NotificationManager.class).getNotificationChannel("alerts");
      intent = new Intent(channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE
          ? Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS : Settings.ACTION_APP_NOTIFICATION_SETTINGS)
          .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID, "alerts");
    } else intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
    returning = true;
    try { activity.startActivity(intent); }
    catch (ActivityNotFoundException e) { returning = false; Updates.show(activity, "Откройте разрешения Радара в системных настройках."); }
  }
  void result(int request) {
    if (request != REQUEST) return;
    requesting = false;
    activity.getWindow().getDecorView().post(() -> { if (!activity.isFinishing() && !activity.isDestroyed()) advance(false); });
  }
  void close() { if (dialog != null) dialog.dismiss(); }
}
