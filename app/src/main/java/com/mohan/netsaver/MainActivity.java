package com.mohan.netsaver;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.DynamicColors;
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
        DynamicColors.applyToActivityIfAvailable(this);
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
             *  2. Content-Type — confirms matched URLs are media/images before teeing.
             *
             * What we do NOT save:
             *  • .m3u8 playlists  — text manifests, useless as standalone files
             */
            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {

                String url   = request.getUrl().toString();
                String lower = url.toLowerCase(Locale.ROOT);

                // Quick URL-pattern check (fast path, no network)
                if (looksLikeSavableFileByUrl(lower)) {
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

    // ─── Saveable file detection ─────────────────────────────

    /**
     * Returns true if the URL strongly suggests a saveable media or image file.
     *
     * Deliberately excludes .m3u8 (playlist text, not playable standalone)
     * and generic "video" words without a concrete extension (too noisy).
     */
    private boolean looksLikeSavableFileByUrl(String lower) {
        // Concrete image extensions
        if (segmentPattern(lower, ".jpg")  || segmentPattern(lower, ".jpeg") ||
            segmentPattern(lower, ".png")  || segmentPattern(lower, ".webp") ||
            segmentPattern(lower, ".gif")  || segmentPattern(lower, ".bmp")  ||
            segmentPattern(lower, ".heic") || segmentPattern(lower, ".heif") ||
            segmentPattern(lower, ".avif") || segmentPattern(lower, ".svg")) return true;

        // Concrete video extensions
        if (segmentPattern(lower, ".mp4")  || segmentPattern(lower, ".webm") ||
            segmentPattern(lower, ".mkv")  || segmentPattern(lower, ".avi")  ||
            segmentPattern(lower, ".mov"))  return true;

        // Audio
        if (segmentPattern(lower, ".mp3")  || segmentPattern(lower, ".aac")  ||
            segmentPattern(lower, ".ogg")  || segmentPattern(lower, ".opus") ||
            segmentPattern(lower, ".flac") || segmentPattern(lower, ".m4a"))  return true;

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
        while (idx >= 0) {
            int after = idx + ext.length();
            if (after == lower.length() || lower.charAt(after) == '?' || lower.charAt(after) == '#') {
                return true;
            }
            idx = lower.indexOf(ext, after);
        }
        return false;
    }

    /**
     * Returns a savable MIME type when either the server Content-Type or the URL
     * itself identifies media. A URL extension is intentionally allowed to win
     * over generic or wrong server types because many cache-busted media links are
     * served as application/octet-stream or text/plain even though they are files.
     */
    private String getSavableMimeType(String url, String rawContentType) {
        String serverMime = rawContentType == null ? "" : rawContentType.split(";")[0].trim();
        if (isSavableContentType(serverMime)) return serverMime;

        String guessedMime = guessMime(url);
        if (isSavableContentType(guessedMime) &&
                looksLikeSavableFileByUrl(url.toLowerCase(Locale.ROOT))) {
            return guessedMime;
        }

        return null;
    }

    /**
     * Check the Content-Type of a response to confirm it's media or an image.
     * Used as a secondary check when the URL pattern isn't conclusive.
     */
    private static boolean isSavableContentType(String ct) {
        if (ct == null || ct.isEmpty()) return false;
        String lower = ct.toLowerCase(Locale.ROOT);
        return lower.startsWith("video/")
            || lower.startsWith("audio/")
            || lower.startsWith("image/")
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

            // Determine MIME. Some CDNs serve image/video files with generic or
            // incorrect Content-Type values (for example application/octet-stream),
            // so a strong URL extension match should still be saved with the MIME
            // guessed from the URL. This fixes image URLs such as .jpg?cacheBust.
            String rawCt = response.header("content-type", "");
            String mimeType = getSavableMimeType(url, rawCt);

            // Secondary filter: confirm it's actually media/image by Content-Type
            // or by a concrete media extension in the URL.
            if (mimeType == null) {
                // Not a saveable file — return null so WebView fetches it normally
                response.close();
                savingUrls.remove(url);
                return null;
            }

            // Build save path
            String fileName  = buildFileName(url, mimeType);
            File   saveFile  = getSaveFile(fileName, mimeType);
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
            headers.put("Content-Type", mimeType);
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
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "image/jpeg";
        if (lower.contains(".png"))  return "image/png";
        if (lower.contains(".webp")) return "image/webp";
        if (lower.contains(".gif"))  return "image/gif";
        if (lower.contains(".bmp"))  return "image/bmp";
        if (lower.contains(".heic")) return "image/heic";
        if (lower.contains(".heif")) return "image/heif";
        if (lower.contains(".avif")) return "image/avif";
        if (lower.contains(".svg"))  return "image/svg+xml";
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
            String ext = mime.contains("jpeg")  ? ".jpg"
                       : mime.contains("png")   ? ".png"
                       : mime.contains("webp")  ? ".webp"
                       : mime.contains("gif")   ? ".gif"
                       : mime.contains("bmp")   ? ".bmp"
                       : mime.contains("heic")  ? ".heic"
                       : mime.contains("heif")  ? ".heif"
                       : mime.contains("avif")  ? ".avif"
                       : mime.contains("svg")   ? ".svg"
                       : mime.contains("mp4")   ? ".mp4"
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

    private File getSaveFile(String fileName, String mimeType) {
        String directoryType = mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/")
                ? Environment.DIRECTORY_PICTURES
                : Environment.DIRECTORY_MOVIES;
        File dir = new File(getExternalFilesDir(directoryType), "NetSaver");
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
        List<SavedFile> files = getSavedFiles();
        if (files.isEmpty()) {
            android.widget.Toast.makeText(this, "No saved files yet", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_saved_gallery, null, false);
        TextView subtitle = content.findViewById(R.id.gallerySubtitle);
        RecyclerView grid = content.findViewById(R.id.savedGalleryGrid);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(content).create();
        Runnable updateSubtitle = () -> subtitle.setText(files.size() + " saved item" + (files.size() == 1 ? "" : "s")
                + " · tap a card to view");

        SavedGalleryAdapter adapter = new SavedGalleryAdapter(files, () -> {
            if (files.isEmpty()) {
                dialog.dismiss();
                android.widget.Toast.makeText(this, "No saved files left", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                updateSubtitle.run();
            }
        });

        grid.setLayoutManager(new GridLayoutManager(this, 2));
        grid.setAdapter(adapter);
        updateSubtitle.run();

        content.findViewById(R.id.btnGalleryClose).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.btnGalleryDone).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.btnGalleryDeleteAll).setOnClickListener(v -> {
            for (SavedFile sf : new ArrayList<>(files)) sf.file.delete();
            files.clear();
            adapter.notifyDataSetChanged();
            dialog.dismiss();
            android.widget.Toast.makeText(this, "Deleted all saved files", android.widget.Toast.LENGTH_SHORT).show();
        });

        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private List<SavedFile> getSavedFiles() {
        List<SavedFile> files = new ArrayList<>();
        collectSavedFiles(files, Environment.DIRECTORY_MOVIES);
        collectSavedFiles(files, Environment.DIRECTORY_PICTURES);
        files.sort((a, b) -> Long.compare(b.file.lastModified(), a.file.lastModified()));
        return files;
    }

    private void collectSavedFiles(List<SavedFile> files, String directoryType) {
        File dir = new File(getExternalFilesDir(directoryType), "NetSaver");
        if (dir.exists() && dir.listFiles() != null) {
            for (File f : dir.listFiles()) {
                if (f.isFile()) files.add(new SavedFile(f));
            }
        }
    }

    private void openFile(File file) {
        Uri uri = getContentUri(file);
        String mt = guessMime(file.getName());
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, mt);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(i); }
        catch (ActivityNotFoundException e) {
            android.widget.Toast.makeText(this, "No app to open this file", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSavedFile(File file) {
        Uri uri = getContentUri(file);
        String mt = guessMime(file.getName());
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mt);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_SUBJECT, file.getName());
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(share, "Share saved content")); }
        catch (ActivityNotFoundException e) {
            android.widget.Toast.makeText(this, "No app to share this file", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private Uri getContentUri(File file) {
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
    }

    private boolean isImageFile(File file) {
        String mt = guessMime(file.getName());
        return mt.startsWith("image/") && !mt.equals("image/svg+xml");
    }

    private String getGalleryKind(File file) {
        String mt = guessMime(file.getName());
        if (mt.startsWith("image/")) return "Image";
        if (mt.startsWith("audio/")) return "Audio";
        if (mt.startsWith("video/")) return "Video";
        return "File";
    }

    private class SavedGalleryAdapter extends RecyclerView.Adapter<SavedGalleryAdapter.ViewHolder> {
        private final List<SavedFile> files;
        private final Runnable onChanged;

        SavedGalleryAdapter(List<SavedFile> files, Runnable onChanged) {
            this.files = files;
            this.onChanged = onChanged;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_saved_gallery, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            SavedFile saved = files.get(position);
            holder.name.setText(saved.displayName);
            holder.meta.setText(saved.getFormattedSize());
            holder.kind.setText(getGalleryKind(saved.file));

            if (isImageFile(saved.file)) {
                holder.preview.setPadding(0, 0, 0, 0);
                holder.preview.setImageURI(Uri.fromFile(saved.file));
            } else {
                int pad = (int) (28 * getResources().getDisplayMetrics().density);
                holder.preview.setPadding(pad, pad, pad, pad);
                holder.preview.setImageResource(R.drawable.ic_video);
            }

            holder.itemView.setOnClickListener(v -> openFile(saved.file));
            holder.share.setOnClickListener(v -> shareSavedFile(saved.file));
            holder.delete.setOnClickListener(v -> {
                int current = holder.getBindingAdapterPosition();
                if (current == RecyclerView.NO_POSITION) return;
                SavedFile removed = files.remove(current);
                removed.file.delete();
                notifyItemRemoved(current);
                onChanged.run();
            });
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView preview;
            final TextView kind, name, meta;
            final ImageButton share, delete;

            ViewHolder(View itemView) {
                super(itemView);
                preview = itemView.findViewById(R.id.galleryPreview);
                kind = itemView.findViewById(R.id.galleryKind);
                name = itemView.findViewById(R.id.galleryFileName);
                meta = itemView.findViewById(R.id.galleryFileMeta);
                share = itemView.findViewById(R.id.btnGalleryShare);
                delete = itemView.findViewById(R.id.btnGalleryDelete);
            }
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
