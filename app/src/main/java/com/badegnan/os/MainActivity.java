package com.badegnan.os;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER = 1001;
    private WebView webView;
    private View loadingOverlay;
    private ValueCallback<Uri[]> uploadCallback;
    private android.widget.TextView loadingStatus;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle state) {
        installCrashHandler();
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(16, 35, 28));
        getWindow().setNavigationBarColor(Color.rgb(16, 35, 28));
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingStatus = findViewById(R.id.loadingStatus);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadsImagesAutomatically(true);
        s.setGeolocationEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        webView.setHapticFeedbackEnabled(false);
        WebView.setWebContentsDebuggingEnabled(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme();
                if (scheme == null || "file".equalsIgnoreCase(scheme) || "about".equalsIgnoreCase(scheme)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) {}
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                injectPremiumBridge(view);
                super.onPageFinished(view, url);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    view.evaluateJavascript("window.dispatchEvent(new CustomEvent('badegnan:native-error',{detail:{code:" + error.getErrorCode() + "}}));", null);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                if (loadingStatus != null) loadingStatus.setText(newProgress < 45 ? "Chargement de l’espace de travail…" : newProgress < 90 ? "Préparation de votre centre de commande…" : "Presque prêt…");
                if (newProgress >= 90 && loadingOverlay != null) {
                    loadingOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> loadingOverlay.setVisibility(View.GONE)).start();
                }
            }

            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = cb;
                try {
                    Intent chooser = p.createIntent();
                    chooser.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(chooser, FILE_CHOOSER);
                    return true;
                } catch (ActivityNotFoundException e) {
                    uploadCallback = null;
                    cb.onReceiveValue(null);
                    return false;
                }
            }
        });

        if (state == null) webView.loadUrl("file:///android_asset/index.html");
        else {
            webView.restoreState(state);
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
        }
        registerConnectivity();
    }

    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                ex.printStackTrace(new java.io.PrintWriter(sw));
                final String trace = sw.toString();
                try {
                    java.io.File f = new java.io.File(getExternalFilesDir(null), "crash_log.txt");
                    java.io.FileWriter fw = new java.io.FileWriter(f, false);
                    fw.write(trace);
                    fw.close();
                } catch (Throwable ignored) {}
                runOnUiThread(() -> {
                    try {
                        android.widget.TextView tv = new android.widget.TextView(MainActivity.this);
                        tv.setText(trace);
                        tv.setTextIsSelectable(true);
                        tv.setPadding(32, 32, 32, 32);
                        android.widget.ScrollView scroll = new android.widget.ScrollView(MainActivity.this);
                        scroll.addView(tv);
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Erreur - fais une capture d'écran")
                                .setView(scroll)
                                .setCancelable(false)
                                .setPositiveButton("Fermer", (d, w) -> {
                                    android.os.Process.killProcess(android.os.Process.myPid());
                                    System.exit(1);
                                })
                                .show();
                    } catch (Throwable t) {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                });
                android.os.Looper.loop();
            } catch (Throwable fatal) {
                if (previous != null) previous.uncaughtException(thread, ex);
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
    }


    private void injectPremiumBridge(WebView view) {
        String js = "(function(){try{"
                + "document.documentElement.classList.add('badegnan-native-v3');"
                + "window.BADEGNAN_NATIVE={version:'3.7.0',platform:'android',offlineReady:true};"
                + "window.dispatchEvent(new CustomEvent('badegnan:native-ready',{detail:window.BADEGNAN_NATIVE}));"
                + "}catch(e){}})();";
        view.evaluateJavascript(js, null);
    }

    private boolean isOnline() {
        if (connectivityManager == null) connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network n = connectivityManager.getActiveNetwork();
        NetworkCapabilities c = n == null ? null : connectivityManager.getNetworkCapabilities(n);
        return c != null && (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void notifyConnectivity(boolean online) {
        if (webView == null) return;
        String js = "window.dispatchEvent(new CustomEvent('badegnan:native-network',{detail:{online:" + online + "}}));";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void registerConnectivity() {
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) { notifyConnectivity(true); }
                @Override public void onLost(Network network) { notifyConnectivity(isOnline()); }
            };
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        }
        webView.postDelayed(() -> notifyConnectivity(isOnline()), 500);
    }

    @Override protected void onSaveInstanceState(Bundle out) { if (webView != null) webView.saveState(out); super.onSaveInstanceState(out); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER && uploadCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            uploadCallback.onReceiveValue(result);
            uploadCallback = null;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (connectivityManager != null && networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        }
        if (uploadCallback != null) { uploadCallback.onReceiveValue(null); uploadCallback = null; }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
                                                                 }
