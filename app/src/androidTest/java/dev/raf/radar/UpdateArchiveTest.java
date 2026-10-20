package dev.raf.radar;

import android.content.Context;
import android.content.pm.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.net.*;
import java.security.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Exercises the real signed release, not a synthetic corrupt three-byte file. */
public class UpdateArchiveTest {
  @Test public void actualPublishedReleaseParsesInsidePrivateUpdateCache() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File f = new File(c.getCacheDir(), "updates/radar.apk");
    f.getParentFile().mkdirs();
    HttpURLConnection connection = (HttpURLConnection) new URL(
        "https://github.com/realalexkorin-alt/radar-android/releases/download/v0.1.5-alpha/radar.apk").openConnection();
    connection.setConnectTimeout(30000); connection.setReadTimeout(60000);
    try {
      assertEquals(200, connection.getResponseCode());
      try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(f)) {
        byte[] b = new byte[65536]; int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
      }
    } finally { connection.disconnect(); }
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new FileInputStream(f)) {
      byte[] b = new byte[65536]; int n; while ((n = in.read(b)) != -1) md.update(b, 0, n);
    }
    StringBuilder hex = new StringBuilder();
    for (byte b : md.digest()) hex.append(String.format(java.util.Locale.US, "%02x", b));
    assertEquals("832c9e1caeecdb97fd62d76afd5ecba3a86ab53b4d199facf110c62b6704fafa", hex.toString());
    PackageInfo candidate = c.getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
    // Distinguish an incomplete download from a PackageManager/archive parser failure.
    assertEquals("Downloaded bytes differ from the published asset", 50023734L, f.length());
    try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(f)) {
      assertNotNull("Missing AndroidManifest.xml in published APK", zip.getEntry("AndroidManifest.xml"));
      assertNotNull("Missing classes.dex in published APK", zip.getEntry("classes.dex"));
    }
    assertNotNull("Android API " + android.os.Build.VERSION.SDK_INT
        + " cannot parse the real published APK from " + f.getAbsolutePath()
        + " (SHA-256 verified). Check artifact signature and Android package parser logs.", candidate);
    assertEquals("dev.raf.radar", candidate.packageName);
    assertEquals(6, candidate.getLongVersionCode());
    assertNotNull(candidate.signingInfo);
    f.delete();
  }
}
