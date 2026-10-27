package dev.raf.radar;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
public class ServiceErrorTest {
  private String classify(String s, String type) { return ServiceError.classify(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)), type); }
  @Test public void recognizesCommonResponsesWithoutLoggingContents() {
    assertEquals("response_timeout", classify("{\"error\":\"upstream timeout\",\"token\":\"secret\"}", "application/json"));
    assertEquals("response_bad_gateway", classify("<h1>Bad Gateway</h1>", "text/html"));
    assertEquals("response_json_other", classify("{\"error\":\"private details\"}", "application/json"));
    assertEquals("response_empty", ServiceError.classify(null, null));
  }
}
