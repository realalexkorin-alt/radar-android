package dev.raf.radar;
/** Bridges native WebView HTTP errors when CORS hides the status from fetch(). */
final class FareHttpState {
 private int status;private long observed=-1;
 synchronized void record(int http,long at){status=http;observed=at;}
 synchronized int resolve(int browserStatus,long attemptAt){
  return browserStatus==0&&observed>=attemptAt&&(status==401||status==403||status==429||status>=500)?status:browserStatus;
 }
}
