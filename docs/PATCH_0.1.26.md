# 0.1.26-alpha — GPS quality and background peer sync

GPS requests every 3 seconds, other providers every 5. Reject overlapping low-quality jitter while a precise fix is recent; reject impossible short-term jumps beyond accuracy circles; reacquire after long gaps. Original measurement age and finite/range validation retained. Synchronized foreground/service GPS state writes.

Enabled sharing works on an open app screen with PRO off; foreground monitoring keeps sharing and directory sync in background. Successful peer data cached every 30s and refreshed on foreground UI. Leaving without monitoring marks offline; sharing switch remains authoritative. Device/environment determine real accuracy.

Focused workflow 39935934219 passed release assembly, lint, formatter and GpsQualityTest on Android API 35 (jitter, impossible jump, stale/poor fix rejection, reacquisition). Source b797148fc34a031ce05f2d351cb64de5bb2af321.
