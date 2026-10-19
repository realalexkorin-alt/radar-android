package dev.raf.radar;
import static org.junit.Assert.*;
import org.junit.Test;
import org.json.*;
import java.io.IOException;

public class UpdateCatalogTest {
  @Test public void onlyOurRepository() {
    assertEquals("https://api.github.com/repos/realalexkorin-alt/radar-android/releases/latest", UpdateCatalog.LATEST);
  }
  @Test public void comparesVersionsNumerically() throws Exception {
    assertTrue(UpdateCatalog.newer("v0.1.10-alpha", "0.1.9-alpha"));
    assertFalse(UpdateCatalog.newer("v0.1.3-alpha", "0.1.4-alpha"));
    assertFalse(UpdateCatalog.newer("v0.1.4-alpha", "0.1.4-alpha"));
    assertTrue(UpdateCatalog.newer("v0.1.4", "0.1.4-alpha"));
  }
  @Test(expected=IOException.class) public void malformedVersionRejected() throws Exception {
    UpdateCatalog.newer("latest", "0.1.4-alpha");
  }
  private JSONObject release(String repository, String digest) throws Exception {
    return new JSONObject().put("assets", new JSONArray().put(new JSONObject()
        .put("name", "radar.apk").put("browser_download_url", "https://github.com/" + repository + "/releases/download/v0.1.5/radar.apk")
        .put("digest", digest)));
  }
  private String digest() { return "sha256:" + String.join("", java.util.Collections.nCopies(64, "a")); }
  @Test public void selectsVerifiedApk() throws Exception {
    assertEquals("radar.apk", UpdateCatalog.apk(release(UpdateCatalog.REPOSITORY, digest())).getString("name"));
  }
  @Test(expected=IOException.class) public void foreignRepositoryRejected() throws Exception {
    UpdateCatalog.apk(release("someone/other", digest()));
  }
  @Test(expected=IOException.class) public void missingChecksumRejected() throws Exception {
    UpdateCatalog.apk(release(UpdateCatalog.REPOSITORY, ""));
  }
  @Test(expected=IOException.class) public void prereleaseRejected() throws Exception {
    UpdateCatalog.apk(release(UpdateCatalog.REPOSITORY, digest()).put("prerelease", true));
  }
}
