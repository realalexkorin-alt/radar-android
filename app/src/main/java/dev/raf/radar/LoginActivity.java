package dev.raf.radar;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.webkit.*;
import android.widget.*;

/** Experimental website sign-in. No password extraction, JS bridge, or custom TLS overrides. */
public final class LoginActivity extends Activity {
  private WebView web;

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    Button done = new Button(this);
    done.setText("Готово · вернуться в Радар");
    done.setOnClickListener(
        v -> {
          CookieManager.getInstance().flush();
          finish();
        });
    root.addView(done);
    TextView hint = new TextView(this);
    hint.setText("Вход необязателен. Совместимость сессии с расчётом пока не подтверждена.");
    root.addView(hint);
    web = new WebView(this);
    root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(root);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setAllowFileAccess(false);
    web.getSettings().setAllowContentAccess(false);
    web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    CookieManager.getInstance().setAcceptCookie(true);
    web.setWebViewClient(
        new WebViewClient() {
          @Override
          public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest req) {
            Uri u = req.getUrl();
            String h = u.getHost();
            if ("https".equals(u.getScheme())
                && h != null
                && (h.equals("yandex.ru")
                    || h.endsWith(".yandex.ru")
                    || h.equals("yandex.com")
                    || h.endsWith(".yandex.com"))) return false;
            return true;
          }
        });
    web.loadUrl("https://taxi.yandex.ru/");
  }

  @Override
  public void onDestroy() {
    if (web != null) {
      web.stopLoading();
      web.destroy();
    }
    super.onDestroy();
  }
}
