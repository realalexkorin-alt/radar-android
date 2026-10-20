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
  static File f;
  @BeforeClass public static void downloadPublishedRelease() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    f = new File(c.getCacheDir(), "archive-tests/published.apk");
    f.getParentFile().mkdirs();
    HttpURLConnection connection = (HttpURLConnection) new URL(
        "https://github.com/realalexkorin-alt/radar-android/releases/download/v0.1.10-alpha/radar.apk").openConnection();
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
    assertEquals("681234a62889aff935fa31a39624c0283711849dde8be765b8887453c5fab479", hex.toString());
  }
  @AfterClass public static void cleanup() { if (f != null) f.delete(); }
  @Test public void actualPublishedReleaseParsesInsidePrivateUpdateCache() throws Exception {
    ApkInspection candidate = ApkInspection.read(f, android.os.Build.VERSION.SDK_INT);
    assertEquals("dev.raf.radar", candidate.packageName);
    assertEquals(11, candidate.versionCode);
    assertEquals(1, candidate.certificates.size());
    byte[] certificate = java.util.Base64.getDecoder().decode(candidate.certificates.iterator().next());
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate);
    StringBuilder hex = new StringBuilder();
    for (byte b : digest) hex.append(String.format(java.util.Locale.US, "%02x", b));
    assertEquals("7baf77afc2f4b5c570c9b08d6092f55ad3ec10a2d798649c846bf2a1429bbd89", hex.toString());
  }
  @Test public void unsignedRepackIsRejectedEvenWithCorrectPackageMetadata() throws Exception {
    File unsigned = new File(f.getParentFile(), "unsigned.apk");
    try {
      try (java.util.zip.ZipFile source = new java.util.zip.ZipFile(f);
           java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new FileOutputStream(unsigned))) {
        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = source.entries();
        byte[] b = new byte[65536];
        while (entries.hasMoreElements()) {
          java.util.zip.ZipEntry e = entries.nextElement();
          if (e.getName().startsWith("META-INF/")) continue;
          out.putNextEntry(new java.util.zip.ZipEntry(e.getName()));
          try (InputStream in = source.getInputStream(e)) { int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); }
          out.closeEntry();
        }
      }
      try { ApkInspection.read(unsigned, android.os.Build.VERSION.SDK_INT); fail("Unsigned APK accepted"); }
      catch (IOException expected) { assertTrue(expected.getMessage().contains("подпис")); }
    } finally { unsigned.delete(); }
  }
}
