package moe.smartrte.arcb50.render;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import moe.smartrte.arcb50.model.B50Summary;
import moe.smartrte.arcb50.model.UserSettings;

/**
 * 兼容备选方案：无头不可见 WebView 挂载原版 Web Canvas 绘制引擎
 */
public class HeadlessWebViewDrawer {
    private static final String TAG = "HeadlessWebViewDrawer";

    public interface WebRenderCallback {
        void onSuccess(Bitmap bitmap);
        void onError(String message);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void renderViaWebView(final Context context, final B50Summary summary, final UserSettings settings, final WebRenderCallback callback) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    final WebView webView = new WebView(context);
                    WebSettings ws = webView.getSettings();
                    ws.setJavaScriptEnabled(true);
                    ws.setAllowFileAccess(true);
                    ws.setDomStorageEnabled(true);
                    ws.setLoadWithOverviewMode(true);
                    ws.setUseWideViewPort(true);

                    // 1700px 宽度
                    webView.layout(0, 0, 1700, 3200);

                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);
                            Log.i(TAG, "WebView page loaded, ready for capture");
                            // 延时等待绘制完成
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        int w = Math.max(1700, webView.getWidth());
                                        int h = Math.max(2000, webView.getContentHeight());
                                        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                                        Canvas canvas = new Canvas(bitmap);
                                        webView.draw(canvas);
                                        if (callback != null) callback.onSuccess(bitmap);
                                    } catch (Exception e) {
                                        if (callback != null) callback.onError(e.getMessage());
                                    }
                                }
                            }, 800);
                        }

                        @Override
                        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                            return false;
                        }
                    });

                    webView.loadUrl("file:///android_asset/web/b50gen.html");
                } catch (Exception e) {
                    if (callback != null) callback.onError(e.getMessage());
                }
            }
        });
    }
}
