package dev.raf.radar;
import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.os.Build;import org.json.*;import java.util.*;
/** Local delivery on successful app/service sync. Platform push transport is separate. */
final class FriendNotifications {
 static void sync(Context c,Store s,JSONObject data){JSONArray rows=data.optJSONArray("incoming");if(rows==null)return;Set<String> ids=new HashSet<>();for(int i=0;i<rows.length();i++){JSONObject p=rows.optJSONObject(i);if(p!=null)ids.add(p.optString("id"));}Set<String> previous=s.prefs.getStringSet("notifiedFriendRequests",Collections.emptySet());
  NotificationManager nm=c.getSystemService(NotificationManager.class);if(ids.isEmpty()){nm.cancel(8728);s.prefs.edit().putStringSet("notifiedFriendRequests",ids).apply();return;}if(previous.containsAll(ids))return;
  if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
  nm.createNotificationChannel(new NotificationChannel("friends","Запросы в друзья",NotificationManager.IMPORTANCE_DEFAULT));JSONObject first=rows.optJSONObject(0);String name=first==null?"Пользователь":first.optString("name","Пользователь");Intent intent=new Intent(c,MainActivity.class).putExtra("openFriends",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  nm.notify(8728,new Notification.Builder(c,"friends").setSmallIcon(R.drawable.ic_radar).setContentTitle("Запрос в друзья").setContentText(ids.size()==1?"Вас хочет добавить в друзья "+name:"Новых запросов: "+ids.size()).setNumber(ids.size()).setAutoCancel(true).setContentIntent(PendingIntent.getActivity(c,8728,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build());s.prefs.edit().putStringSet("notifiedFriendRequests",ids).apply();
 }
}
