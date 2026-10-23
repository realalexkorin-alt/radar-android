package dev.raf.radar;

import android.content.Context;
import android.location.*;
import android.os.Bundle;

/** Location while the map's Activity is visible, including when PRO is off. */
final class MapGps implements LocationListener {
  private final Context context;private final Store store;private final Runnable changed;
  private LocationManager manager;private boolean listening;
  MapGps(Context context,Store store,Runnable changed){this.context=context;this.store=store;this.changed=changed;}
  void start(){
    if(!Access.location(context)){stop();return;}if(listening)return;
    manager=context.getSystemService(LocationManager.class);
    try{listening=true;for(String provider:manager.getProviders(true)){
      Location last=manager.getLastKnownLocation(provider);if(last!=null)onLocationChanged(last);
      manager.requestLocationUpdates(provider,LocationManager.GPS_PROVIDER.equals(provider)?3000:5000,0,this);
    }}catch(SecurityException e){Diagnostics.error("gps","permission_denied",e,0,-1,-1,null);stop();}
  }
  void stop(){if(manager!=null)manager.removeUpdates(this);listening=false;}
  public void onLocationChanged(Location l){if(LocationState.accept(store,l)){Diagnostics.once("gps","fix_accepted",false);changed.run();}}
  public void onProviderEnabled(String p){}public void onProviderDisabled(String p){Diagnostics.once("gps","provider_disabled",true);}
  public void onStatusChanged(String p,int status,Bundle extras){}
}
