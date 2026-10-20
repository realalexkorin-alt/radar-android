package dev.raf.radar;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.util.DataSources;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.cert.X509Certificate;
import java.util.*;

/** Read signed APK metadata without the device vendor's PackageManager archive parser. */
final class ApkInspection {
  final String packageName;
  final long versionCode;
  final Set<String> certificates;
  private ApkInspection(String name, long version, Set<String> signers) {
    packageName = name; versionCode = version; certificates = Collections.unmodifiableSet(signers);
  }
  static ApkInspection read(File file, int deviceApi) throws IOException {
    try {
      ApkVerifier.Result result = new ApkVerifier.Builder(file)
          .setMinCheckedPlatformVersion(deviceApi).setMaxCheckedPlatformVersion(deviceApi)
          .build().verify();
      if (!result.isVerified() || result.getSignerCertificates().isEmpty())
        throw new IOException("Криптографическая подпись APK недействительна");
      Set<String> certificates = new HashSet<>();
      for (X509Certificate signer : result.getSignerCertificates())
        certificates.add(Base64.getEncoder().encodeToString(signer.getEncoded()));
      try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
        ByteBuffer manifest = ApkUtils.getAndroidManifest(DataSources.asDataSource(in));
        String name = ApkUtils.getPackageNameFromBinaryAndroidManifest(manifest.duplicate());
        long version = ApkUtils.getLongVersionCodeFromBinaryAndroidManifest(manifest.duplicate());
        int minimumApi = ApkUtils.getMinSdkVersionFromBinaryAndroidManifest(manifest.duplicate());
        if (minimumApi > deviceApi) throw new IOException("APK требует Android API " + minimumApi);
        if (name == null || name.isEmpty() || version <= 0) throw new IOException("В APK нет имени пакета или версии");
        return new ApkInspection(name, version, certificates);
      }
    } catch (IOException e) { throw e; }
    catch (Exception e) { throw new IOException("Не удалось проверить подписанный APK: " + e.getClass().getSimpleName(), e); }
  }
}
