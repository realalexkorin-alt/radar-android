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
        "https://github.com/realalexkorin-alt/radar-android/releases/download/v0.1.4-alpha/radar.apk").openConnection();
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
    assertEquals("6032ecbf68dbafe5663987a962605ae6006b7d589d383fa90c72353bc65a3a4f", hex.toString());
    PackageInfo candidate = c.getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
    assertNotNull("Android could not parse the genuine signed release at " + f, candidate);
    assertEquals("dev.raf.radar", candidate.packageName);
    assertEquals(5, candidate.getLongVersionCode());
    assertNotNull(candidate.signingInfo);
    f.delete();
  }
}
