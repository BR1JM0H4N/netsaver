package com.mohan.netsaver;

import android.os.Bundle;
import android.os.Environment;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText urlInput;
    private Button goButton;

    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        urlInput = findViewById(R.id.urlInput);
        goButton = findViewById(R.id.goButton);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {

                String url = request.getUrl().toString();

                if (
                        url.contains(".m3u8") ||
                        url.contains(".ts") ||
                        url.contains(".mp4") ||
                        url.contains("video")
                ) {

                    return interceptAndSave(url);
                }

                return super.shouldInterceptRequest(view, request);
            }
        });

        goButton.setOnClickListener(v -> {

            String url = urlInput.getText().toString();

            if (!url.startsWith("http")) {
                url = "https://" + url;
            }

            webView.loadUrl(url);
        });

        webView.loadUrl("https://example.com");
    }

    private WebResourceResponse interceptAndSave(String url) {

        try {

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            Response response = client.newCall(request).execute();

            ResponseBody body = response.body();

            if (body == null) {
                return null;
            }

            File mediaDir = new File(
                    getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    "NetSaver"
            );

            if (!mediaDir.exists()) {
                mediaDir.mkdirs();
            }

            String fileName = url.substring(url.lastIndexOf("/") + 1);

            if (fileName.contains("?")) {
                fileName = fileName.substring(0, fileName.indexOf("?"));
            }

            if (fileName.isEmpty()) {
                fileName = "media_" + System.currentTimeMillis() + ".ts";
            }

            File file = new File(mediaDir, fileName);

            InputStream inputStream = body.byteStream();

            FileOutputStream outputStream = new FileOutputStream(file);

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.flush();
            outputStream.close();
            inputStream.close();

            InputStream replayStream = file.inputStream();

            String mimeType = response.header(
                    "content-type",
                    "video/mp2t"
            );

            String encoding = response.header(
                    "content-encoding",
                    "utf-8"
            );

            return new WebResourceResponse(
                    mimeType,
                    encoding,
                    replayStream
            );

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void onBackPressed() {

        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}