package dev.raf.radar;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class TaxiClientTest {
  @Test
  public void passengerFareNeverBecomesRubSurcharge() throws Exception {
    JSONObject j =
        new JSONObject(
            "{\"service_levels\":[{\"class\":\"econom\",\"price\":\"365"
                + " ₽\",\"paid_options\":{\"value\":2.15}}]}");
    Store.Reading r = TaxiClient.parse(j, Arrays.asList("econom"), 100).get(0);
    assertNull(r.rub);
    assertEquals(2.15, r.ratio, 0.001);
  }

  @Test
  public void absentTariffNotZero() throws Exception {
    Store.Reading r =
        TaxiClient.parse(new JSONObject("{\"service_levels\":[]}"), Arrays.asList("vip"), 1).get(0);
    assertNull(r.rub);
    assertNull(r.ratio);
  }

  @Test(expected = JSONException.class)
  public void changedSchemaRejected() throws Exception {
    TaxiClient.parse(new JSONObject("{}"), Arrays.asList("econom"), 1);
  }

  @Test
  public void requestCoordinateOrder() throws Exception {
    Point p = new Point("p", "p", 56.8, 60.6, false);
    JSONArray a = TaxiClient.request(p).getJSONArray("route").getJSONArray(0);
    assertEquals(60.6, a.getDouble(0), 0.001);
    assertEquals(56.8, a.getDouble(1), 0.001);
  }

  @Test
  public void pointRoundTrip() throws Exception {
    Point p = new Point("me", "Моя геопозиция", 0, 0, true);
    p.notify = true;
    p.enabled = false;
    p.address = "Тестовый адрес";
    p.threshold = 250;
    p.tariffs = Arrays.asList("econom");
    Point q = Point.from(p.json());
    assertTrue(q.dynamic);
    assertTrue(q.notify);
    assertFalse(q.enabled);
    assertEquals(p.address, q.address);
    assertEquals(250, q.threshold);
    assertEquals(p.tariffs, q.tariffs);
  }
}
