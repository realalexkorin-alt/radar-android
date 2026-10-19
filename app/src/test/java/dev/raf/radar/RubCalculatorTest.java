package dev.raf.radar;
import static org.junit.Assert.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
public class RubCalculatorTest {
  private JSONObject base() throws Exception { return new JSONObject("{\"priceEconom\":95,\"priceComfort\":109.25,\"priceComfortPlus\":199}"); }
  private JSONObject level(String price) throws Exception { return new JSONObject().put("price", price); }
  @Test public void economMatchesOriginal() throws Exception { assertEquals(Integer.valueOf(270), RubCalculator.calculate(level("365 ₽"),base(),"econom")); }
  @Test public void decimalBaselineAndRounding() throws Exception { assertEquals(Integer.valueOf(200), RubCalculator.calculate(level("от 309,25 ₽"),base(),"business")); }
  @Test public void noSurgeClampedToZero() throws Exception { assertEquals(Integer.valueOf(0), RubCalculator.calculate(level("90 ₽"),base(),"econom")); }
  @Test public void missingOrZeroBaseNotFullFare() throws Exception { assertNull(RubCalculator.calculate(level("365 ₽"),null,"econom")); assertNull(RubCalculator.calculate(level("365 ₽"),new JSONObject("{\"priceEconom\":0}"),"econom")); }
  @Test public void malformedPriceUnavailable() throws Exception { assertNull(RubCalculator.calculate(level("нет машин"),base(),"econom")); assertNull(RubCalculator.calculate(level("100–200 ₽"),base(),"econom")); }
  @Test public void oldApiPriceList() throws Exception { assertEquals(Integer.valueOf(270), RubCalculator.calculate(new JSONObject("{\"price\":[{\"value\":\"365 ₽\"}]}"),base(),"econom")); }
  @Test public void currencyGuardAndMultiplierPreserved() throws Exception {
    JSONObject j = new JSONObject().put("currency_rules",new JSONObject().put("code","RUB")).put("service_levels",new JSONArray().put(level("365 ₽").put("class","econom").put("paid_options",new JSONObject().put("value",2.15))));
    Store.Reading r=TaxiClient.parse(j,Arrays.asList("econom"),100,base(),"ekb").get(0);
    assertEquals(Integer.valueOf(270),r.rub); assertEquals(2.15,r.ratio,0.001);
    j.getJSONObject("currency_rules").put("code","BYN"); assertNull(TaxiClient.parse(j,Arrays.asList("econom"),100,base(),"ekb").get(0).rub);
  }
}
