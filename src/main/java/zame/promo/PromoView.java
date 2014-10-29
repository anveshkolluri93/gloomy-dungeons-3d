package zame.promo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.InputStream;
import zame.game.BuildConfig;

public class PromoView extends FrameLayout {
    protected static final String PROMO_URL = "http://mobile.zame-dev.org/promo/index.php?package=";

    protected static final long RELOAD_INTERVAL = 10L * 1000L;
    protected static final long ROTATE_INTERVAL = 15L * 1000L;

    protected static final int STATE_INITIALIZED = 0;
    protected static final int STATE_LOADING = 1;
    protected static final int STATE_LOADED = 2;
    protected static final int STATE_DISMISSED = 3;

    protected Context context;
    protected WebView prevWebView;
    protected WebView currentWebView;
    protected int state;

    protected Runnable loadPromoRunnable = new Runnable() {
        @Override
        public void run() {
            loadPromo();
        }
    };

    protected Runnable reloadPromoRunnable = new Runnable() {
        @Override
        public void run() {
            reloadPromo();
        }
    };

    protected Runnable rotatePromoRunnable = new Runnable() {
        @Override
        public void run() {
            rotatePromo();
        }
    };

    protected Runnable promoLoadedRunnable = new Runnable() {
        @Override
        public void run() {
            promoLoaded();
        }
    };

    protected Runnable promoDismissedRunnable = new Runnable() {
        @Override
        public void run() {
            promoDismissed();
        }
    };

    public PromoView(Context context) {
        super(context);
        initialize(context);
    }

    public PromoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public PromoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize(context);
    }

    protected void initialize(Context context) {
        this.context = context;

        prevWebView = createWebView();
        currentWebView = createWebView();

        loadPromo();
    }

    protected WebView createWebView() {
        WebView webView = new WebView(context);
        webView.addJavascriptInterface(new JsApi(), "promoApi");
        webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        webView.setScrollbarFadingEnabled(true);
        webView.setWebViewClient(new PromoWebViewClient());
        webView.setWebChromeClient(new PromoWebChromeClient());
        webView.setVisibility(View.INVISIBLE);

        webView.setBackgroundColor(0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
        }

        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });

        WebSettings webSettings = webView.getSettings();
        webSettings.setBuiltInZoomControls(false);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webSettings.setDisplayZoomControls(false);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        webSettings.setSupportZoom(false);
        webSettings.setUseWideViewPort(true);
        webSettings.setSupportMultipleWindows(true);

        webView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(webView);

        return webView;
    }

    protected void loadPromo() {
        removeCallbacks(loadPromoRunnable);
        removeCallbacks(reloadPromoRunnable);
        removeCallbacks(rotatePromoRunnable);

        if (state != STATE_INITIALIZED) {
            return;
        }

        if (isNetworkConnected()) {
            state = STATE_LOADING;

            if (BuildConfig.DEBUG) {
                currentWebView.loadUrl(PROMO_URL + context.getPackageName() + "&mode=debug");
            } else {
                currentWebView.loadUrl(PROMO_URL + context.getPackageName());
            }
        } else {
            postDelayed(loadPromoRunnable, RELOAD_INTERVAL);
        }
    }

    protected void reloadPromo() {
        currentWebView.setVisibility(View.INVISIBLE);
        currentWebView.stopLoading();
        currentWebView.loadData("", "text/html", null);

        state = STATE_INITIALIZED;
        loadPromo();
    }

    protected void rotatePromo() {
        WebView tmpWebView = prevWebView;
        prevWebView = currentWebView;
        currentWebView = tmpWebView;

        reloadPromo();
    }

    protected void promoLoaded() {
        if (state == STATE_LOADING) {
            currentWebView.setVisibility(View.VISIBLE);

            prevWebView.setVisibility(View.INVISIBLE);
            prevWebView.stopLoading();
            prevWebView.loadData("", "text/html", null);

            state = STATE_LOADED;
            postDelayed(rotatePromoRunnable, ROTATE_INTERVAL);
        }
    }

    protected void promoDismissed() {
        if (state == STATE_LOADING || state == STATE_LOADED) {
            prevWebView.setVisibility(View.INVISIBLE);
            prevWebView.stopLoading();
            prevWebView.loadData("", "text/html", null);

            currentWebView.setVisibility(View.INVISIBLE);
            currentWebView.stopLoading();
            currentWebView.loadData("", "text/html", null);

            state = STATE_DISMISSED;
            postDelayed(rotatePromoRunnable, ROTATE_INTERVAL);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        if (state != STATE_INITIALIZED) {
            return;
        }

        if (hasWindowFocus) {
            loadPromo();
        } else {
            removeCallbacks(loadPromoRunnable);
        }
    }

    protected boolean isNetworkConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return false;
        }

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    protected void openExternalBrowser(final String uri) {
        post(new Runnable() {
            @Override
            public void run() {
                try {
                    context.startActivity((
                        new Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET));
                } catch (Exception ex) {
                    try {
                        Toast.makeText(context, "Could not launch the browser application.", Toast.LENGTH_LONG).show();
                    } catch (Exception inner) {
                    }
                }
            }
        });
    }

    protected void openExternalIntent(final Intent intent) {
        post(new Runnable() {
            @Override
            public void run() {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET));
                } catch (Exception ex) {
                    try {
                        Toast.makeText(context, "Could not start external intent.", Toast.LENGTH_LONG).show();
                    } catch (Exception inner) {
                    }
                }
            }
        });
    }

    protected class JsApi {
        @JavascriptInterface
        public void loaded() {
            PromoView.this.postDelayed(promoLoadedRunnable, 100L);
        }

        @JavascriptInterface
        public void dismiss() {
            PromoView.this.post(promoDismissedRunnable);
        }
    }

    protected class PromoWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            view.setBackgroundColor(0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                view.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            final String MAILTO_PREFIX = "mailto:";

            if (url.startsWith(MAILTO_PREFIX)) {
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", url.replaceFirst(MAILTO_PREFIX, ""), null));
                openExternalIntent(intent);
                return true;
            }

            return false;
        }

        @SuppressLint("NewApi")
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            final String ANDROID_ASSET = "file:///android_asset/";

            if (url.startsWith(ANDROID_ASSET)) {
                try {
                    Uri uri = Uri.parse(url.replaceFirst(ANDROID_ASSET, ""));
                    InputStream stream = view.getContext().getAssets().open(uri.getPath());
                    return new WebResourceResponse("text/html", "UTF-8", stream);
                } catch (Exception ex) {
                }
            }

            return null;
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            view.stopLoading();
            view.loadData("", "text/html", null);

            PromoView.this.post(reloadPromoRunnable);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed();
        }

        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
            view.stopLoading();
            view.loadData("", "text/html", null);

            PromoView.this.post(reloadPromoRunnable);
        }
    }

    protected class PromoWebChromeClient extends WebChromeClient {
        protected WebView childWebView;

        @Override
        public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message resultMsg) {
            try {
                if (childWebView != null) {
                    childWebView.stopLoading();
                    childWebView.destroy();
                }

                childWebView = new WebView(view.getContext());

                childWebView.setWebViewClient(new WebViewClient() {
                    @SuppressLint("NewApi")
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                        url = url.trim();

                        if (url.length() != 0) {
                            openExternalBrowser(url);
                        }

                        childWebView.stopLoading();
                        childWebView.destroy();
                        childWebView = null;

                        return null;
                    }
                });

                ((WebView.WebViewTransport)resultMsg.obj).setWebView(childWebView);
                resultMsg.sendToTarget();
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}