package com.clive.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Settings, Wallpaper, Apps on Home and the Wi-Fi panel from the design, rendered from
 * assets/index.html over the native Home. Opened on demand and closed when you leave, so
 * the web engine isn't kept in memory while you're on Home.
 */
public class SettingsActivity extends Activity {

    static final int REQ_HOME_ROLE = 42;

    private WebView web;
    private Bridge bridge;
    private boolean pageReady;

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            js("window.TV && TV.netChanged()");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        web = new WebView(this);
        web.setBackgroundColor(0x00000000);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
            }
        });

        bridge = new Bridge(this, web);
        web.addJavascriptInterface(bridge, "Native");
        setContentView(web);
        web.requestFocus();

        Intent in = getIntent();
        String mode = "wifi".equals(in.getStringExtra("mode")) ? "wifi" : "settings";
        StringBuilder hash = new StringBuilder("mode=").append(mode);
        if (in.getStringExtra("screen") != null) hash.append("&screen=").append(in.getStringExtra("screen"));
        hash.append("&f=").append(in.getIntExtra("f", 0));
        web.loadUrl("file:///android_asset/index.html#" + hash);

        IntentFilter nf = new IntentFilter();
        nf.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        nf.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        nf.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(networkReceiver, nf);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (pageReady) js("window.TV && TV.resumed()");
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        js("window.TV && TV.back()");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int k = event.getKeyCode();
        boolean ours = k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_SETTINGS;
        if (!ours) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            js(k == KeyEvent.KEYCODE_BACK ? "window.TV && TV.back()" : "window.TV && TV.menu()");
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_HOME_ROLE) {
            if (!bridge.isDefaultHome()) bridge.openButtonMapper();
            js("window.TV && TV.netChanged()");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        js("window.TV && TV.netChanged()");
    }

    void js(final String code) {
        if (web == null) return;
        web.post(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript(code, null);
            }
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(networkReceiver); } catch (Exception ignored) { }
        if (web != null) {
            web.removeJavascriptInterface("Native");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
