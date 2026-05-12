package com.mohan.netsaver;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

    // ─── Views ────────────────────────────────────────────────
    private WebView                  webView;
    private EditText                 urlInput;
    private ImageButton              btnBack, btnForward, btnRefresh, btnMenu;
    private ImageButton              btnHome, btnSavedFiles, btnShare, btnNewTab;
    private ImageButton              btnClearUrl;
    private ImageView                iconSecurity;
    private LinearProgressIndicator  pageLoadProgress;
    private LinearLayout             saveIndicator;
    private TextView                 saveIndicatorText;
    private View                     statusBarSpacer, navBarSpacer;

    // ─── Networking ───────────────────────────────────────────
    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String HOME_URL = "https://www.google.com";

    // Track URLs already being saved (avoid duplicate saves of same segment)
    private final java.util.Set<String> savingUrls =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        applyWindowInsets();
        setupWebView();
        setupListeners();

        webView.loadUrl(HOME_URL);
    }

    // ─── Init ─────────────────────────────────────────────────

    private void initViews() {
        webView           = findViewById(R.id.webView);
        urlInput          = findViewById(R.id.urlInput);
        btnBack           = findViewById(R.id.btnBack);
        btnForward        = findViewById(R.id.btnForward);
        btnRefresh        = findViewById(R.id.btnRefresh);
        btnMenu           = findViewById(R.id.btnMenu);
        btnHome           = findViewById(R.id.btnHome);
        btnSavedFiles     = findViewById(R.id.btnSavedFiles);
        btnShare          = findViewById(R.id.btnShare);
        btnNewTab         = findViewById(R.id.btnNewTab);
        btnClearUrl       = findViewById(R.id.btnClearUrl);
        iconSecurity      = findViewById(R.id.iconSecurity);
        pageLoadProgress  = findViewById(R.id.pageLoadProgress);
        saveIndicator     = findViewById(R.id.saveIndicator);
        saveIndicatorText = findViewById(R.id.saveIndicatorText);
        statusBarSpacer   = findViewById(R.id.statusBarSpacer);
        navBarSpacer      = findViewById(R.id.navBarSpacer);
    }

    /** Push toolbar below status bar and bottom bar above nav bar */
    private void applyWindowInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            findViewById(R.id.rootLayout).setOnApplyWindowInsetsListener((v, insets) -> {
                int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
                int bot = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                statusBarSpacer.getLayoutParams().height = top;
                statusBarSpacer.requestLayout();
                navBarSpacer.getLayoutParams().height = bot;
                navBarSpacer.requestLayout();
                return insets;
            });
        }
    }

    // ─── WebView setup ────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        // Modern mobile UA
        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"
        );

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                if (p < 100) {
                    pageLoadProgress.setVisibility(View.VISIBLE);
                    pageLoadProgress.setProgressCompat(p, true);
                } else {
                    pageLoadProgress.setVisibility(View.GONE);
                }
                // Update refresh/stop icon
                btnRefresh.setImageResource(p < 100
                        ? R.drawable.ic_stop : R.drawable.ic_refresh);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                // keep URL bar showing URL, not title
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap fav) {
                mainHandler.post(() -> {
                    urlInput.setText(url);
                    updateUrlBarSecurity(url);
                    updateNavButtons();
                });
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                mainHandler.post(() -> {
                    urlInput.setText(url);
                    updateUrlBarSecurity(url);
                    updateNavButtons();
                    pageLoadProgress.setVisibility(View.GONE);
                    btnRefresh.setImageResource(R.drawable.ic_refresh);
                });
            }

            /**
             * ═══════════════════════════════════════════════════════════════
             *  INTERCEPT + TEE STREAM — called on a background (IO) thread
             * ═══════════════════════════════════════════════════════════════
             *
             * Detection strategy (fixes why v2 never saved anything):
             *
             *  1. URL pattern  — covers well-known extensions/paths
             *  2. Content-Type — catches CDN URLs with no file extension
             *     We make a HEAD request first; if it's video/audio we tee it.
             *
             * What we do NOT save:
             *  • .m3u8 playlists  — text manifests, useless as standalone files
             *  • Very small files (<4 KB) — likely init segments / key files
             */
            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {

                String url   = request.getUrl().toString();
                String lower = url.toLowerCase(Locale.ROOT);

                // Quick URL-pattern check (fast path, no network)
                if (looksLikeMediaByUrl(lower)) {
                    return teeStream(url, request);
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.startsWith("tel:") || url.startsWith("mailto:") ||
                        url.startsWith("sms:") || url.startsWith("intent:")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                    catch (ActivityNotFoundException ignored) {}
                    return true;
                }
                return false;
            }
        });
    }

    // ─── Media detection ─────────────────────────────────────

    /**
     * Returns true if the URL strongly suggests a binary media segment.
     *
     * Deliberately excludes .m3u8 (playlist text, not playable standalone)
     * and generic "video" words without a concrete extension (too noisy).
     */
    private boolean looksLikeMediaByUrl(String lower) {
        // Concrete video extensions
        if (lower.contains(".mp4")  || lower.contains(".webm") ||
            lower.contains(".mkv")  || lower.contains(".avi")  ||
            lower.contains(".mov"))  return true;

        // Audio
        if (lower.contains(".mp3")  || lower.contains(".aac")  ||
            lower.contains(".ogg")  || lower.contains(".opus") ||
            lower.contains(".flac") || lower.contains(".m4a"))  return true;

        // HLS transport stream segments (NOT the .m3u8 playlist itself)
        // Matches both ".ts" and ".ts?token=xxx"
        if (segmentPattern(lower, ".ts")) return true;

        // DASH segments – common patterns from major CDNs
        if (lower.contains("dash") && (lower.contains("video") || lower.contains("audio"))) return true;
        if (lower.contains("/seg-") && (lower.contains("-v") || lower.contains("-a"))) return true;
        if (lower.contains("segment") && (lower.contains("video") || lower.contains("audio"))) return true;
        if (lower.contains("/chunk_") || lower.contains("/chunk-")) return true;

        // YouTube-style itag URLs (they never have an extension)
        if (lower.contains("googlevideo.com") && lower.contains("itag=")) return true;

        // Common CDN segment paths
        if ((lower.contains("/hls/") || lower.contains("/hlsvod/")) &&
            !lower.endsWith(".m3u8") && !lower.endsWith(".m3u8?") &&
            !lower.contains("playlist")) return true;

        return false;
    }

    /** True if url contains ext immediately followed by end-of-string, '?', or '#' */
    private static boolean segmentPattern(String lower, String ext) {
        int idx = lower.indexOf(ext);
        if (idx < 0) return false;
        int after = idx + ext.length();
        return after == lower.length() || lower.charAt(after) == '?' || lower.charAt(after) == '#';
    }

    /**
     * Check the Content-Type of a response to confirm it's media.
     * Used as a secondary check when the URL pattern isn't conclusive.
     */
    private static boolean isMediaContentType(String ct) {
        if (ct == null || ct.isEmpty()) return false;
        String lower = ct.toLowerCase(Locale.ROOT);
        return lower.startsWith("video/")
            || lower.startsWith("audio/")
            || lower.contains("mp4")
            || lower.contains("webm")
            || lower.contains("mp2t")
            || lower.contains("mpeg");
    }

    // ─── Tee streaming ────────────────────────────────────────

    /**
     * Makes ONE OkHttp request for the URL.
     * Returns a WebResourceResponse whose InputStream is a TeeInputStream:
     *   network bytes → WebView  (playback)
     *               └→ file     (saved to disk)
     *
     * Zero extra bandwidth. No second request.
     */
    private WebResourceResponse teeStream(String url, WebResourceRequest original) {

        // Deduplicate: don't save the same URL twice in parallel
        if (!savingUrls.add(url)) {
            // Already in progress — let WebView fetch it normally
            return null;
        }

        try {
            Request.Builder rb = new Request.Builder().url(url);

            // Forward original request headers so CDNs don't reject us
            for (Map.Entry<String, String> e : original.getRequestHeaders().entrySet()) {
                String key = e.getKey();
                if (!key.equalsIgnoreCase("host") &&
                    !key.equalsIgnoreCase("content-length") &&
                    !key.equalsIgnoreCase("connection")) {
                    try { rb.header(key, e.getValue()); }
                    catch (IllegalArgumentException ignored) {} // skip malformed headers
                }
            }

            Response response = client.newCall(rb.build()).execute();

            // If response is not 2xx, pass through without saving
            if (!response.isSuccessful()) {
                response.close();
                savingUrls.remove(url);
                return null;
            }

            ResponseBody body = response.body();
            if (body == null) {
                response.close();
                savingUrls.remove(url);
                return null;
            }

            // Determine MIME (Content-Type from server is authoritative)
            String rawCt   = response.header("content-type", "");
            String mimeType = (rawCt != null && !rawCt.isEmpty())
                    ? rawCt.split(";")[0].trim()
                    : guessMime(url);

            // Secondary filter: confirm it's actually media by Content-Type
            // (catches CDN URLs with no extension that we let through by URL pattern)
            if (!isMediaContentType(mimeType)) {
                // Not media — return null so WebView fetches it normally
                response.close();
                savingUrls.remove(url);
                return null;
            }

            // Build save path
            String fileName  = buildFileName(url, mimeType);
            File   saveFile  = getSaveFile(fileName);
            FileOutputStream fileOut = new FileOutputStream(saveFile);

            // The tee: one stream, two sinks
            TeeInputStream tee = new TeeInputStream(body.byteStream(), fileOut) {
                @Override
                public void close() throws java.io.IOException {
                    try { super.close(); }
                    finally {
                        savingUrls.remove(url);
                        mainHandler.post(() -> hideSaveIndicator());
                    }
                }
            };

            // Notify UI
            mainHandler.post(() -> showSaveIndicator(fileName));

            // Build response headers (strip encoding/length that would confuse WebView)
            Map<String, String> headers = new LinkedHashMap<>();
            for (String name : response.headers().names()) {
                String lname = name.toLowerCase(Locale.ROOT);
                if (!lname.equals("content-encoding") &&
                    !lname.equals("transfer-encoding") &&
                    !lname.equals("content-length")) {
                    headers.put(name, response.header(name, ""));
                }
            }
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");

            return new WebResourceResponse(
                    mimeType,
                    "binary",          // never re-decode; OkHttp already decoded gzip
                    response.code(),
                    "OK",
                    headers,
                    tee
            );

        } catch (Exception e) {
            e.printStackTrace();
            savingUrls.remove(url);
            return null;
        }
    }

    // ─── File helpers ─────────────────────────────────────────

    private String guessMime(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains(".mp4"))  return "video/mp4";
        if (lower.contains(".webm")) return "video/webm";
        if (lower.contains(".ts"))   return "video/mp2t";
        if (lower.contains(".mp3"))  return "audio/mpeg";
        if (lower.contains(".aac"))  return "audio/aac";
        if (lower.contains(".ogg"))  return "audio/ogg";
        if (lower.contains(".m4a"))  return "audio/mp4";
        return "application/octet-stream";
    }

    private String buildFileName(String url, String mime) {
        // Strip query/fragment
        String path = url;
        int q = path.indexOf('?'); if (q > 0) path = path.substring(0, q);
        int h = path.indexOf('#'); if (h > 0) path = path.substring(0, h);

        String name = path.substring(path.lastIndexOf('/') + 1);

        // If name is empty or looks like a random token (no dot, >40 chars), generate one
        if (name.isEmpty() || (!name.contains(".") && name.length() > 40) || name.length() > 120) {
            String ext = mime.contains("mp4")   ? ".mp4"
                       : mime.contains("webm")  ? ".webm"
                       : mime.contains("mp2t")  ? ".ts"
                       : mime.contains("mpeg")  ? ".mp3"
                       : mime.contains("aac")   ? ".aac"
                       : mime.contains("ogg")   ? ".ogg"
                       : ".media";
            name = "netsaver_" + System.currentTimeMillis() + ext;
        }
        return name;
    }

    private File getSaveFile(String fileName) {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "NetSaver");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, fileName);
        if (f.exists()) {
            String base = fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            String ext  = fileName.contains(".")
                    ? fileName.substring(fileName.lastIndexOf('.'))    : "";
            f = new File(dir, base + "_" + System.currentTimeMillis() + ext);
        }
        return f;
    }

    // ─── UI helpers ───────────────────────────────────────────

    private void showSaveIndicator(String fileName) {
        saveIndicatorText.setText("Saving: " + fileName);
        saveIndicator.setVisibility(View.VISIBLE);
    }

    private void hideSaveIndicator() {
        saveIndicator.setVisibility(View.GONE);
    }

    private void updateNavButtons() {
        btnBack.setAlpha(webView.canGoBack()    ? 1.0f : 0.38f);
        btnForward.setAlpha(webView.canGoForward() ? 1.0f : 0.38f);
    }

    private void updateUrlBarSecurity(String url) {
        if (url != null && url.startsWith("https://")) {
            iconSecurity.setImageResource(R.drawable.ic_lock);
        } else {
            iconSecurity.setImageResource(R.drawable.ic_lock_open);
        }
    }

    // ─── Listeners ────────────────────────────────────────────

    private void setupListeners() {

        btnBack.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        btnForward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });

        btnRefresh.setOnClickListener(v -> {
            if (pageLoadProgress.getVisibility() == View.VISIBLE) webView.stopLoading();
            else webView.reload();
        });

        // URL bar keyboard action
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(urlInput.getText().toString().trim());
                hideKeyboard();
                return true;
            }
            return false;
        });

        urlInput.setOnFocusChangeListener((v, focused) -> {
            btnClearUrl.setVisibility(focused ? View.VISIBLE : View.GONE);
            if (focused) urlInput.selectAll();
        });

        btnClearUrl.setOnClickListener(v -> {
            urlInput.setText("");
            urlInput.requestFocus();
        });

        // Bottom bar
        btnHome.setOnClickListener(v -> webView.loadUrl(HOME_URL));

        btnSavedFiles.setOnClickListener(v -> showSavedFilesDialog());

        btnShare.setOnClickListener(v -> {
            String url = webView.getUrl();
            if (url != null) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, url);
                startActivity(Intent.createChooser(share, "Share"));
            }
        });

        btnNewTab.setOnClickListener(v -> {
            String url = webView.getUrl();
            if (url != null) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (ActivityNotFoundException ignored) {}
            }
        });

        // Overflow menu
        btnMenu.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, v);
            pm.getMenu().add(0, 1, 0, "Reload page");
            pm.getMenu().add(0, 2, 1, "Toggle desktop site");
            pm.getMenu().add(0, 3, 2, "Saved files");
            pm.getMenu().add(0, 4, 3, "Share page");
            pm.getMenu().add(0, 5, 4, "Open in browser");
            pm.getMenu().add(0, 6, 5, "Clear cache");
            pm.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: webView.reload(); return true;
                    case 2: toggleDesktopSite(); return true;
                    case 3: showSavedFilesDialog(); return true;
                    case 4: btnShare.performClick(); return true;
                    case 5: btnNewTab.performClick(); return true;
                    case 6: clearCache(); return true;
                }
                return false;
            });
            pm.show();
        });
    }

    private void navigate(String input) {
        if (TextUtils.isEmpty(input)) return;
        String url;
        if (input.startsWith("http://") || input.startsWith("https://")) {
            url = input;
        } else if (input.contains(".") && !input.contains(" ") && !input.startsWith(".")) {
            url = "https://" + input;
        } else {
            url = "https://www.google.com/search?q=" + Uri.encode(input);
        }
        webView.loadUrl(url);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void toggleDesktopSite() {
        WebSettings s = webView.getSettings();
        boolean isDesktop = s.getUserAgentString().contains("Windows");
        if (isDesktop) {
            s.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36");
        } else {
            s.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36");
        }
        webView.reload();
    }

    private void clearCache() {
        webView.clearCache(true);
        webView.clearHistory();
        android.widget.Toast.makeText(this, "Cache cleared", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void showSavedFilesDialog() {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "NetSaver");
        List<SavedFile> files = new ArrayList<>();
        if (dir.exists() && dir.listFiles() != null) {
            for (File f : dir.listFiles()) {
                if (f.isFile()) files.add(new SavedFile(f));
            }
        }
        if (files.isEmpty()) {
            android.widget.Toast.makeText(this, "No saved media yet", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        files.sort((a, b) -> Long.compare(b.file.lastModified(), a.file.lastModified()));

        String[] items = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            items[i] = files.get(i).displayName + "  ·  " + files.get(i).getFormattedSize();
        }

        List<SavedFile> finalFiles = files;
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Saved Media")
                .setItems(items, (d, w) -> openFile(finalFiles.get(w).file))
                .setNeutralButton("Delete All", (d, w) -> {
                    for (SavedFile sf : finalFiles) sf.file.delete();
                    android.widget.Toast.makeText(this, "Deleted all saved files", android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void openFile(File file) {
        Uri  uri  = Uri.fromFile(file);
        String mt = guessMime(file.getName());
        Intent i  = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, mt);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(i); }
        catch (ActivityNotFoundException e) {
            android.widget.Toast.makeText(this, "No app to open this file", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        urlInput.clearFocus();
    }

    // ─── Lifecycle ────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onPause()   { super.onPause();   webView.onPause();   }
    @Override protected void onResume()  { super.onResume();  webView.onResume();  }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        webView.destroy();
    }
}
