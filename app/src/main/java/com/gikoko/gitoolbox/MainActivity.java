package com.gikoko.gitoolbox;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int FILE_REQ = 1001;
    private ValueCallback<Uri[]> filePathCb;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        webView.setBackgroundColor(0xFF04150E);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCb != null) filePathCb.onReceiveValue(null);
                filePathCb = cb;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQ);
                } catch (Exception e) {
                    filePathCb = null;
                    return false;
                }
                return true;
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String contentDisposition, String mime, long len) {
                if (url.startsWith("data:")) saveDataUrl(url);
                else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void saveDataUrl(String url) {
        try {
            String meta = url.substring(url.indexOf(',') + 1);
            byte[] data = Base64.decode(meta, Base64.DEFAULT);
            String ext = "png";
            if (url.startsWith("data:image/jpeg")) ext = "jpg";
            else if (url.startsWith("data:image/webp")) ext = "webp";
            else if (url.startsWith("data:application/pdf")) ext = "pdf";
            String name = "GIToolbox_" + System.currentTimeMillis() + "." + ext;
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(android.provider.MediaStore.Downloads.MIME_TYPE,
                        url.substring(5, url.indexOf(';')));
                Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(data);
                os.close();
                Toast.makeText(this, "Saved to Downloads: " + name, Toast.LENGTH_LONG).show();
            } else {
                File f = new File(getExternalFilesDir(null), name);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(data);
                fos.close();
                Toast.makeText(this, "Saved: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQ && filePathCb != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
            filePathCb.onReceiveValue(results);
            filePathCb = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
