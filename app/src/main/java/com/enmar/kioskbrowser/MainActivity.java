package com.enmar.kioskbrowser;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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
    private static final String KEY_ZOOM = "kiosk_zoom_percent";
    private static final String DEFAULT_URL = "https://room-status-info-1.web.app/";
    private static final int DEFAULT_ZOOM = 100;
    private static final int MIN_ZOOM = 50;
    private static final int MAX_ZOOM = 250;
    private static final int ZOOM_STEP = 10;
    private static final String KIOSK_PIN = "2583";

    private WebView webView;
    private LinearLayout adminPanel;
    private EditText urlInput;
    private TextView statusLabel;
    private TextView zoomLabel;
    private Button unlockButton;

    private boolean isKioskLocked = true;
    private boolean isAdminUnlocked = false;
    private boolean isAllowingExit = false;
    private int currentZoomPercent = DEFAULT_ZOOM;

    private final Handler relockHandler = new Handler();
    private final Runnable bringBackRunnable = new Runnable() {
        @Override
        public void run() {
            if (isKioskLocked && !isAllowingExit) {
                try {
                    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    if (am != null) {
                        am.moveTaskToFront(getTaskId(), ActivityManager.MOVE_TASK_WITH_HOME);
                    }
                } catch (Exception ignored) {
                }
                Intent intent = new Intent(MainActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                try {
                    startActivity(intent);
                } catch (Exception ignored) {
                }
            }
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.kioskWebView);
        adminPanel = findViewById(R.id.adminPanel);
        urlInput = findViewById(R.id.urlInput);
        statusLabel = findViewById(R.id.statusLabel);
        zoomLabel = findViewById(R.id.zoomLabel);
        unlockButton = findViewById(R.id.unlockButton);
        Button zoomOutButton = findViewById(R.id.zoomOutButton);
        Button zoomResetButton = findViewById(R.id.zoomResetButton);
        Button zoomInButton = findViewById(R.id.zoomInButton);
        Button saveUrlButton = findViewById(R.id.saveUrlButton);
        Button relockButton = findViewById(R.id.relockButton);
        Button exitKioskButton = findViewById(R.id.exitKioskButton);

        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUrl = prefs.getString(KEY_URL, DEFAULT_URL);
        currentZoomPercent = clampZoom(prefs.getInt(KEY_ZOOM, DEFAULT_ZOOM));
        urlInput.setText("");

        configureWebView();
        applyZoom(currentZoomPercent, prefs, false);
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

        zoomOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isAdminUnlocked) {
                    Toast.makeText(MainActivity.this, "Unlock admin panel first", Toast.LENGTH_SHORT).show();
                    return;
                }
                applyZoom(currentZoomPercent - ZOOM_STEP, prefs, true);
            }
        });

        zoomResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isAdminUnlocked) {
                    Toast.makeText(MainActivity.this, "Unlock admin panel first", Toast.LENGTH_SHORT).show();
                    return;
                }
                applyZoom(DEFAULT_ZOOM, prefs, true);
            }
        });

        zoomInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isAdminUnlocked) {
                    Toast.makeText(MainActivity.this, "Unlock admin panel first", Toast.LENGTH_SHORT).show();
                    return;
                }
                applyZoom(currentZoomPercent + ZOOM_STEP, prefs, true);
            }
        });

        saveUrlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isAdminUnlocked) {
                    Toast.makeText(MainActivity.this, "Unlock admin panel first", Toast.LENGTH_SHORT).show();
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

    private int clampZoom(int value) {
        if (value < MIN_ZOOM) {
            return MIN_ZOOM;
        }
        if (value > MAX_ZOOM) {
            return MAX_ZOOM;
        }
        return value;
    }

    private void applyZoom(int zoomPercent, SharedPreferences prefs, boolean persist) {
        currentZoomPercent = clampZoom(zoomPercent);
        webView.getSettings().setTextZoom(currentZoomPercent);
        zoomLabel.setText("Page Zoom: " + currentZoomPercent + "%");
        if (persist && prefs != null) {
            prefs.edit().putInt(KEY_ZOOM, currentZoomPercent).apply();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(currentZoomPercent);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlNavigation(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    return handleUrlNavigation(request.getUrl().toString());
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.getSettings().setTextZoom(currentZoomPercent);
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
        builder.setTitle("Unlock Required");
        builder.setMessage("Enter access code:");

        final EditText pinInput = new EditText(this);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("Code");
        builder.setView(pinInput);

        builder.setPositiveButton("Verify", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String entered = pinInput.getText().toString().trim();
                if (KIOSK_PIN.equals(entered)) {
                    onSuccess.run();
                } else {
                    Toast.makeText(MainActivity.this, "Incorrect code", Toast.LENGTH_SHORT).show();
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
        unlockButton.setText("Unlock");
        engageKioskMode();
    }

    private void engageKioskMode() {
        isKioskLocked = true;
        isAdminUnlocked = false;
        isAllowingExit = false;
        urlInput.setText("");
        adminPanel.setVisibility(View.GONE);
        unlockButton.setText("Unlock");
        statusLabel.setText("KIOSK LOCKED");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveFlags();

        try {
            startLockTask();
        } catch (Exception ignored) {
        }
    }

    private void applyImmersiveFlags() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    private void exitKioskAndClose() {
        isKioskLocked = false;
        isAdminUnlocked = false;
        isAllowingExit = true;
        relockHandler.removeCallbacks(bringBackRunnable);
        urlInput.setText("");
        adminPanel.setVisibility(View.GONE);
        try {
            stopLockTask();
        } catch (Exception ignored) {
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isKioskLocked && !isAllowingExit) {
            relockHandler.removeCallbacks(bringBackRunnable);
            relockHandler.postDelayed(bringBackRunnable, 150);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        relockHandler.removeCallbacks(bringBackRunnable);
        if (isKioskLocked && !isAllowingExit) {
            applyImmersiveFlags();
        }
    }

    @Override
    protected void onDestroy() {
        relockHandler.removeCallbacks(bringBackRunnable);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isKioskLocked && !isAllowingExit) {
            applyImmersiveFlags();
        }
    }

    @Override
    public void onBackPressed() {
        if (isKioskLocked && !isAllowingExit) {
            Toast.makeText(this, "Kiosk locked", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }
}
