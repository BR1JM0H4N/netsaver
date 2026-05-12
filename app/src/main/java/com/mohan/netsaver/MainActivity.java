package com.mohan.netsaver;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
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
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText urlInput;
    private ImageButton btnBack, btnForward, btnRefresh, btnMenu;
    private ImageButton btnHome, btnBookmarks, btnSavedFiles, btnShare, btnNewTab;
    private ImageButton btnClearUrl;
    private ProgressBar pageLoadProgress;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String HOME_URL = "https://www.google.com";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupWebView();
        setupListeners();

        webView.loadUrl(HOME_URL);
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        urlInput = findViewById(R.id.urlInput);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnMenu = findViewById(R.id.btnMenu);
        btnHome = findViewById(R.id.btnHome);
        btnBookmarks = findViewById(R.id.btnBookmarks);
        btnSavedFiles = findViewById(R.id.btnSavedFiles);
        btnShare = findViewById(R.id.btnShare);
        btnNewTab = findViewById(R.id.btnNewTab);
        btnClearUrl = findViewById(R.id.btnClearUrl);
        pageLoadProgress = findViewById(R.id.pageLoadProgress);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 10; Mobile) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"
        );

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    pageLoadProgress.setVisibility(View.VISIBLE);
                    pageLoadProgress.setProgress(newProgress);
                } else {
                    pageLoadProgress.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                // Could show in toolbar if desired
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                urlInput.setText(url);
                updateNavButtons();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                urlInput.setText(url);
                updateNavButtons();
                pageLoadProgress.setVisibility(View.GONE);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request) {

                String url = request.getUrl().toString();
                String lower = url.toLowerCase();

                // Intercept video/media resources for tee-streaming
                if (isMediaUrl(lower)) {
                    return interceptWithTeeStream(url, request);
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Handle special schemes
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (ActivityNotFoundException ignored) {}
                    return true;
                }
                return false;
            }
        });
    }

    private boolean isMediaUrl(String lower) {
        return lower.contains(".m3u8") ||
               lower.contains(".ts?") ||
               lower.endsWith(".ts") ||
               lower.contains(".mp4") ||
               lower.contains(".webm") ||
               lower.contains(".mp3") ||
               lower.contains(".aac") ||
               lower.contains(".ogg") ||
               (lower.contains("video") && (lower.contains(".mp4") || lower.contains(".webm"))) ||
               lower.contains("media/segment") ||
               lower.contains("hls") ||
               lower.contains("chunk") && (lower.contains("video") || lower.contains("audio"));
    }

    /**
     * TRUE ZERO-DOUBLE-BANDWIDTH: Tee Streaming
     *
     * Architecture:
     *   WebView request
     *       ↓
     *   Intercept SAME request (one OkHttp call)
     *       ↓
     *   TeeInputStream splits the single response stream:
     *       ├── → WebView (for real-time playback)
     *       └── → FileOutputStream (for saving to disk)
     *
     * NO duplicate requests. NO double bandwidth.
     */
    private WebResourceResponse interceptWithTeeStream(String url, WebResourceRequest originalRequest) {
        try {
            // Build request mimicking original WebView headers
            Request.Builder reqBuilder = new Request.Builder().url(url);

            // Forward original headers to appear legitimate to the server
            for (java.util.Map.Entry<String, String> entry : originalRequest.getRequestHeaders().entrySet()) {
                String key = entry.getKey();
                // Skip headers OkHttp manages itself
                if (!key.equalsIgnoreCase("host") && !key.equalsIgnoreCase("content-length")) {
                    reqBuilder.header(key, entry.getValue());
                }
            }

            Response response = client.newCall(reqBuilder.build()).execute();
            ResponseBody body = response.body();

            if (body == null || !response.isSuccessful()) {
                if (response.body() != null) response.body().close();
                return null;
            }

            String mimeType = getMimeType(response, url);
            String encoding = response.header("content-encoding", "identity");

            // Decide file name
            String fileName = extractFileName(url, mimeType);
            File outputFile = getSaveFile(fileName);

            FileOutputStream fileOut = new FileOutputStream(outputFile);

            // Tee: single stream → WebView + file simultaneously
            InputStream networkStream = body.byteStream();
            TeeInputStream teeStream = new TeeInputStream(networkStream, fileOut);

            // Notify user on main thread
            mainHandler.post(() -> showSavingToast(fileName));

            return new WebResourceResponse(
                    mimeType,
                    encoding,
                    response.code(),
                    "OK",
                    buildResponseHeaders(response),
                    teeStream
            );

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getMimeType(Response response, String url) {
        String ct = response.header("content-type", "");
        if (ct != null && !ct.isEmpty()) {
            // Strip charset parameter
            return ct.split(";")[0].trim();
        }
        String lower = url.toLowerCase();
        if (lower.contains(".m3u8")) return "application/vnd.apple.mpegurl";
        if (lower.contains(".ts")) return "video/mp2t";
        if (lower.contains(".mp4")) return "video/mp4";
        if (lower.contains(".webm")) return "video/webm";
        if (lower.contains(".mp3")) return "audio/mpeg";
        if (lower.contains(".aac")) return "audio/aac";
        return "application/octet-stream";
    }

    private String extractFileName(String url, String mimeType) {
        String name = url.substring(url.lastIndexOf("/") + 1);
        if (name.contains("?")) name = name.substring(0, name.indexOf("?"));
        if (name.contains("#")) name = name.substring(0, name.indexOf("#"));
        if (name.isEmpty() || name.length() > 100) {
            String ext = mimeType.contains("mp4") ? ".mp4"
                    : mimeType.contains("webm") ? ".webm"
                    : mimeType.contains("mp2t") ? ".ts"
                    : mimeType.contains("mpegurl") ? ".m3u8"
                    : mimeType.contains("mp3") || mimeType.contains("mpeg") ? ".mp3"
                    : ".media";
            name = "netsaver_" + System.currentTimeMillis() + ext;
        }
        return name;
    }

    private File getSaveFile(String fileName) {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "NetSaver");
        if (!dir.exists()) dir.mkdirs();
        // Avoid overwriting: append timestamp if file exists
        File f = new File(dir, fileName);
        if (f.exists()) {
            String base = fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf("."))
                    : fileName;
            String ext = fileName.contains(".")
                    ? fileName.substring(fileName.lastIndexOf("."))
                    : "";
            f = new File(dir, base + "_" + System.currentTimeMillis() + ext);
        }
        return f;
    }

    private java.util.Map<String, String> buildResponseHeaders(Response response) {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        for (String name : response.headers().names()) {
            String lower = name.toLowerCase();
            // Pass through important headers, skip ones that cause issues
            if (!lower.equals("content-encoding") &&
                !lower.equals("transfer-encoding") &&
                !lower.equals("content-length")) {
                headers.put(name, response.header(name, ""));
            }
        }
        headers.put("Access-Control-Allow-Origin", "*");
        return headers;
    }

    private void showSavingToast(String fileName) {
        Toast.makeText(this, "⬇ Saving: " + fileName, Toast.LENGTH_SHORT).show();
    }

    private void setupListeners() {
        // Navigation
        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });

        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });

        btnRefresh.setOnClickListener(v -> {
            if (pageLoadProgress.getVisibility() == View.VISIBLE) {
                webView.stopLoading();
            } else {
                webView.reload();
            }
        });

        // URL bar
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateTo(urlInput.getText().toString().trim());
                hideKeyboard();
                return true;
            }
            return false;
        });

        urlInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                urlInput.selectAll();
                btnClearUrl.setVisibility(View.VISIBLE);
            } else {
                btnClearUrl.setVisibility(View.GONE);
            }
        });

        btnClearUrl.setOnClickListener(v -> {
            urlInput.setText("");
            urlInput.requestFocus();
        });

        // Bottom bar
        btnHome.setOnClickListener(v -> webView.loadUrl(HOME_URL));

        btnBookmarks.setOnClickListener(v ->
            Toast.makeText(this, "Bookmarks coming soon", Toast.LENGTH_SHORT).show()
        );

        btnSavedFiles.setOnClickListener(v -> showSavedFilesDialog());

        btnShare.setOnClickListener(v -> {
            String url = webView.getUrl();
            if (url != null) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, url);
                startActivity(Intent.createChooser(share, "Share URL"));
            }
        });

        btnNewTab.setOnClickListener(v -> {
            // Open in external browser as "new tab" equivalent
            String url = webView.getUrl();
            if (url != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(HOME_URL));
                try { startActivity(intent); } catch (ActivityNotFoundException ignored) {}
            } else {
                webView.loadUrl(HOME_URL);
            }
        });

        // Overflow menu
        btnMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add(0, 1, 0, "Reload");
            popup.getMenu().add(0, 2, 1, "Desktop site");
            popup.getMenu().add(0, 3, 2, "Saved files");
            popup.getMenu().add(0, 4, 3, "Share");
            popup.getMenu().add(0, 5, 4, "Open in browser");
            popup.getMenu().add(0, 6, 5, "Clear cache");

            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: webView.reload(); return true;
                    case 2: toggleDesktopSite(); return true;
                    case 3: showSavedFilesDialog(); return true;
                    case 4: btnShare.performClick(); return true;
                    case 5: openInBrowser(); return true;
                    case 6: clearCache(); return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private void navigateTo(String input) {
        if (TextUtils.isEmpty(input)) return;

        String url;
        if (input.startsWith("http://") || input.startsWith("https://")) {
            url = input;
        } else if (input.contains(".") && !input.contains(" ")) {
            url = "https://" + input;
        } else {
            // Search query
            url = "https://www.google.com/search?q=" + Uri.encode(input);
        }
        webView.loadUrl(url);
    }

    private void updateNavButtons() {
        btnBack.setAlpha(webView.canGoBack() ? 1.0f : 0.4f);
        btnForward.setAlpha(webView.canGoForward() ? 1.0f : 0.4f);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void toggleDesktopSite() {
        WebSettings s = webView.getSettings();
        boolean isDesktop = !s.getUserAgentString().contains("Mobile");
        if (isDesktop) {
            s.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 10; Mobile) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
            );
            Toast.makeText(this, "Mobile site", Toast.LENGTH_SHORT).show();
        } else {
            s.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36"
            );
            Toast.makeText(this, "Desktop site", Toast.LENGTH_SHORT).show();
        }
        webView.reload();
    }

    private void openInBrowser() {
        String url = webView.getUrl();
        if (url != null) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void clearCache() {
        webView.clearCache(true);
        webView.clearHistory();
        Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "No saved files yet", Toast.LENGTH_SHORT).show();
            return;
        }

        // Sort by newest first
        files.sort((a, b) -> Long.compare(b.file.lastModified(), a.file.lastModified()));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Saved Files (" + files.size() + ")");

        // Build simple list
        String[] items = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            items[i] = files.get(i).displayName + "\n" + files.get(i).getFormattedSize();
        }

        builder.setItems(items, (dialog, which) -> {
            openSavedFile(files.get(which).file);
        });

        builder.setNegativeButton("Close", null);
        builder.setNeutralButton("Delete All", (d, w) -> {
            for (SavedFile sf : files) sf.file.delete();
            Toast.makeText(this, "All files deleted", Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }

    private void openSavedFile(File file) {
        try {
            Uri uri = Uri.fromFile(file);
            String mime = getMimeForFile(file.getName());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeForFile(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".ts")) return "video/mp2t";
        if (lower.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".aac")) return "audio/aac";
        return "*/*";
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        urlInput.clearFocus();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        webView.destroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }
}
