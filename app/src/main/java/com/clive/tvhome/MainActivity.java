package com.clive.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
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
 * The TV home screen. The interface is the TV Home Launcher design, rendered from
 * assets/index.html; this activity hosts it and feeds it what the TV actually has
 * (installed apps, inputs, network, device details) through {@link Bridge}.
 */
public class MainActivity extends Activity {

    static final int REQ_HOME_ROLE = 42;

    private WebView web;
    private Bridge bridge;
    private boolean pageReady;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            bridge.invalidateApps();
            js("window.TV && TV.appsChanged()");
        }
    };

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
        hideSystemUi();

        web = new WebView(this);
        web.setBackgroundColor(0xFF1D2A30);
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
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true; // the launcher never navigates away from itself
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
        web.loadUrl("file:///android_asset/index.html");

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        registerReceiver(packageReceiver, pf);

        IntentFilter nf = new IntentFilter();
        nf.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        nf.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        nf.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(networkReceiver, nf);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_HOME_ROLE) {
            // TV declined or has no chooser: use the Home-button switch instead.
            if (!bridge.isDefaultHome()) bridge.openHomeSwitch();
            js("window.TV && TV.netChanged()");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back in TV Home: the stock launcher no longer gets a pass.
        getSharedPreferences(HomeButtonService.PREFS, MODE_PRIVATE).edit()
                .remove(HomeButtonService.ALLOW_STOCK_UNTIL).apply();
        hideSystemUi();
        web.onResume();
        if (pageReady) js("window.TV && TV.resumed()");
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    /** Home pressed while the launcher is already in front: return to the main Home view. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        js("window.TV && TV.home()");
    }

    /** A launcher never closes on Back; the page decides what Back means. */
    @Override
    public void onBackPressed() {
        js("window.TV && TV.back()");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    js("window.TV && TV.back()");
                    return true;
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_SETTINGS:
                    js("window.TV && TV.menu()");
                    return true;
                default:
                    break;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP
                && (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                || event.getKeyCode() == KeyEvent.KEYCODE_MENU
                || event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
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
                if (Build.VERSION.SDK_INT >= 19) web.evaluateJavascript(code, null);
                else web.loadUrl("javascript:" + code);
            }
        });
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(packageReceiver); } catch (Exception ignored) { }
        try { unregisterReceiver(networkReceiver); } catch (Exception ignored) { }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
