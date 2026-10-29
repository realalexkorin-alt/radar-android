package dev.raf.radar;
/** One ordinary page reload after 401; never retry CAPTCHA, 403 or rate limits. */
final class AuthRecovery {
 private long last=-1;
 synchronized boolean begin(int http,String kind,boolean retried,long now){
  if(retried)return false;
  boolean network=http==0&&(kind.equals("network")||kind.equals("timeout"));
  if(!(http==401&&"refused".equals(kind))&&!network)return false;
  if(last>=0&&now-last<(network?60000:300000))return false;
  last=now;return true;
 }
}
