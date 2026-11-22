package com.enmar.kioskbrowser;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "kiosk_prefs";
    private static final String KEY_URL = "kiosk_url";
    private static final String DEFAULT_URL = "https://example.com";
    private static final String KIOSK_PIN = "2583";

    private WebView webView;
    private LinearLayout adminPanel;
    private EditText urlInput;
    private TextView statusLabel;
    private Button unlockButton;

    private boolean isKioskLocked = true;
    private boolean isAdminUnlocked = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.kioskWebView);
        adminPanel = findViewById(R.id.adminPanel);
        urlInput = findViewById(R.id.urlInput);
        statusLabel = findViewById(R.id.statusLabel);
        unlockButton = findViewById(R.id.unlockButton);
        Button saveUrlButton = findViewById(R.id.saveUrlButton);
        Button relockButton = findViewById(R.id.relockButton);
        Button exitKioskButton = findViewById(R.id.exitKioskButton);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUrl = prefs.getString(KEY_URL, DEFAULT_URL);
        urlInput.setText("");

        configureWebView();
        webView.loadUrl(savedUrl);

        unlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isAdminUnlocked) {
                    lockAdminControls();
                } else {
                    showPinDialog(new Runnable() {
                        @Override
                        public void run() {
                            unlockAdminControls();
                        }
                    });
                }
            }
        });

        saveUrlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isAdminUnlocked) {
                    Toast.makeText(MainActivity.this, "Enter PIN 2583 first", Toast.LENGTH_SHORT).show();
                    return;
                }
                String raw = urlInput.getText().toString().trim();
                if (TextUtils.isEmpty(raw)) {
                    Toast.makeText(MainActivity.this, "URL cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
                    raw = "https://" + raw;
                }
                urlInput.setText(raw);
                prefs.edit().putString(KEY_URL, raw).apply();
                webView.loadUrl(raw);
                Toast.makeText(MainActivity.this, "Kiosk URL updated", Toast.LENGTH_SHORT).show();
            }
        });

        relockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engageKioskMode();
                Toast.makeText(MainActivity.this, "Kiosk mode locked", Toast.LENGTH_SHORT).show();
            }
        });

        exitKioskButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isAdminUnlocked) {
                    showPinDialog(new Runnable() {
                        @Override
                        public void run() {
                            exitKioskAndClose();
                        }
                    });
                } else {
                    exitKioskAndClose();
                }
            }
        });

        engageKioskMode();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlNavigation(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null && request.getUrl() != null) {
                    return handleUrlNavigation(request.getUrl().toString());
                }
                return false;
            }
        });
    }

    private boolean handleUrlNavigation(String url) {
        if (TextUtils.isEmpty(url)) {
            return true;
        }
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            return false;
        }
        Toast.makeText(this, "Blocked external scheme", Toast.LENGTH_SHORT).show();
        return true;
    }

    private void showPinDialog(final Runnable onSuccess) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Kiosk PIN Required");
        builder.setMessage("Enter PIN for admin panel:");

        final EditText pinInput = new EditText(this);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("PIN");
        builder.setView(pinInput);

        builder.setPositiveButton("Verify", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String entered = pinInput.getText().toString().trim();
                if (KIOSK_PIN.equals(entered)) {
                    onSuccess.run();
                } else {
                    Toast.makeText(MainActivity.this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void unlockAdminControls() {
        isAdminUnlocked = true;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        urlInput.setText(prefs.getString(KEY_URL, DEFAULT_URL));
        adminPanel.setVisibility(View.VISIBLE);
        unlockButton.setText("Lock Panel");
        statusLabel.setText("ADMIN UNLOCKED");
    }

    private void lockAdminControls() {
        isAdminUnlocked = false;
        urlInput.setText("");
        adminPanel.setVisibility(View.GONE);
        unlockButton.setText("PIN");
        engageKioskMode();
    }

    private void engageKioskMode() {
        isKioskLocked = true;
        isAdminUnlocked = false;
        urlInput.setText("");
        adminPanel.setVisibility(View.GONE);
        unlockButton.setText("PIN");
        statusLabel.setText("KIOSK MODE ACTIVE");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveFlags();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null && am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                    startLockTask();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void applyImmersiveFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    private void exitKioskAndClose() {
        isKioskLocked = false;
        isAdminUnlocked = false;
        urlInput.setText("");
        adminPanel.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                stopLockTask();
            } catch (Exception ignored) {
            }
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        finish();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isKioskLocked) {
            applyImmersiveFlags();
        }
    }

    @Override
    public void onBackPressed() {
        if (isKioskLocked && !isAdminUnlocked) {
            Toast.makeText(this, "Kiosk locked: enter PIN 2583 to exit", Toast.LENGTH_SHORT).show();
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
