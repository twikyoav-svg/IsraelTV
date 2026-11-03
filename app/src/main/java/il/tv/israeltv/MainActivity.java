package il.tv.israeltv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.webkit.JavascriptInterface;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "IPTV-style" launcher for the official Israeli channel live pages.
 * One WebView plays the channel's own live page; the remote switches channels.
 *
 * Remote:
 *   CH+/CH- or Up/Down  -> next / previous channel
 *   digits 1-9 (0=10)   -> jump to channel number
 *   OK / Menu           -> channel list & settings
 *   Back                -> channel list; Back again -> exit
 *   Play/Pause          -> play / pause the video on the page
 *   Mouse mode          -> arrows move a pointer, OK clicks (for play buttons / login)
 */
public class MainActivity extends Activity {

    private static final String PREFS = "israeltv";
    // v3: channels carry a direct stream + type (tv / radio). Older saved lists are ignored.
    private static final String KEY_CHANNELS = "channels_v3";
    private static final String KEY_LAST = "last";
    private static final String KEY_FILL = "fill";
    private static final String KEY_DESKTOP = "desktop";
    private static final String KEY_BLOCK = "blockSlow";

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    private static final String KAN_HLS = "https://kancdn.medonecdn.net/livehls/oil/kancdn-live/live/";

    /**
     * Default channel list: {name, site page, stream, type}.
     * stream = candidates separated by " | ": a direct URL, or "sniff" = find the stream on the
     * site page at play time. If every candidate fails, the site page itself is shown.
     * Editable inside the app (long-press OK on a channel).
     */
    private static final String[][] DEFAULT_CHANNELS = {
            {"כאן 11", "https://www.kan.org.il/live/",
                    KAN_HLS + "kan11/live.livx/playlist.m3u8?renditions&fmp4&relativePaths | sniff", "tv"},
            {"קשת 12", "https://www.mako.co.il/mako-vod-live-tv/VOD-6540b8dcb64fd31006.htm", "sniff", "tv"},
            {"רשת 13", "https://13tv.co.il/live/",
                    "https://d2xg1g9o5vns8m.cloudfront.net/out/v1/0855d703f7d5436fae6a9c7ce8ca5075/index.m3u8 | sniff", "tv"},
            {"עכשיו 14", "https://www.c14.co.il/live",
                    "https://r.il.cdn-redge.media/livehls/oil/ch14/live/ch14/live.livx/playlist.m3u8 | sniff", "tv"},
            {"i24NEWS", "https://www.i24news.tv/he/tv/live",
                    "https://i24newshebrew-cdn.encoders.immergo.tv/master.m3u8 | sniff", "tv"},
            {"כאן חינוכית", "https://www.kankids.org.il/live",
                    KAN_HLS + "kan_edu/live.livx/playlist.m3u8?renditions&fmp4 | sniff", "tv"},
            {"ערוץ הכנסת", "https://www.knesset.tv/live/", "sniff", "tv"},

            {"גלגלצ", "https://glz.co.il/גלגלצ",
                    "https://glzwizzlv.bynetcdn.com/glglz_mp3?awCollectionId=misc&awEpisodeId=glglz"
                            + " | https://api.bynetcdn.com/Redirector/glz/glglz/ICE-LIVE", "radio"},
            {"גלצ", "https://glz.co.il/גלצ",
                    "https://api.bynetcdn.com/Redirector/glz/glz/ICE-LIVE"
                            + " | https://glzwizzlv.bynetcdn.com/glz_mp3?awCollectionId=misc&awEpisodeId=glz", "radio"},
            {"99FM", "https://eco99fm.maariv.co.il/",
                    "https://99.mediacast.co.il/99fm_aac | https://99.mediacast.co.il/99fm?.mp3", "radio"},
            {"רשת ב", "https://www.kan.org.il/live/",
                    KAN_HLS + "radio/kan_reshet_bet/live.livx/playlist.m3u8?renditions", "radio"},
            {"רשת ג", "https://www.kan.org.il/live/",
                    KAN_HLS + "radio/kan_gimel/live.livx/playlist.m3u8?renditions", "radio"},
            {"88FM", "https://www.kan.org.il/live/",
                    KAN_HLS + "radio/kan_88/live.livx/playlist.m3u8?renditions", "radio"},
            {"100FM", "https://www.100fm.co.il/live/",
                    "https://cdn.cybercdn.live/Radios_100FM/Audio/playlist.m3u8"
                            + " | https://cdn.cybercdn.live/Radios_100FM/Audio/icecast.audio", "radio"},
    };

    /** Channel fields. */
    private static final int C_NAME = 0, C_PAGE = 1, C_STREAM = 2, C_TYPE = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String[]> channels = new ArrayList<>();
    private SharedPreferences prefs;

    private FrameLayout root;
    private WebView webView;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private LinearLayout overlay;
    private LinearLayout channelListView;
    private TextView infoBanner;
    private View cursor;

    private int current = -1;
    private boolean fillMode;
    private boolean desktopMode;
    private volatile boolean blockSlow;
    private boolean okPressed;
    private boolean okLong;
    private boolean cursorMode = false;
    private float cursorX, cursorY;
    private long lastBack = 0;

    private final Runnable hideBanner = () -> infoBanner.setVisibility(View.GONE);

    // ---- VOD state
    /** One row in a VOD list. */
    static class Item {
        String title, url, sub;
        boolean enabled = true;

        Item(String title, String url, String sub) {
            this.title = title;
            this.url = url;
            this.sub = sub;
        }
    }

    private FrameLayout vodScreen;
    private TextView vodHeader;
    private TextView vodStatus;
    private LinearLayout vodList;
    private ScrollView vodScroll;
    private WebView scraper;
    private final List<Runnable> vodStack = new ArrayList<>();
    private final Map<String, List<Item>> vodCache = new HashMap<>();
    private int vodToken = 0;
    private boolean vodPlaying = false;
    private String scrapeKey;
    private int scrapeId = 0;
    private boolean scrapeStarted;
    private ScrapeCallback scrapeCallback;

    interface ScrapeCallback {
        void done(List<Item> items);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        fillMode = prefs.getBoolean(KEY_FILL, true);
        desktopMode = prefs.getBoolean(KEY_DESKTOP, true);
        blockSlow = prefs.getBoolean(KEY_BLOCK, true);
        loadChannels();
        buildUi();

        int last = prefs.getInt(KEY_LAST, -1);
        if (last >= 0 && last < channels.size()) {
            playChannel(last);
        } else {
            showOverlay();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        if (resumeNative && current >= 0) playChannel(current); // live: jump back to "now"
        resumeNative = false;
    }

    private boolean resumeNative = false;

    @Override
    protected void onPause() {
        if (nativeActive) {
            resumeNative = true;
            stopNative();
        }
        if (webView != null) {
            webView.evaluateJavascript(
                    "document.querySelectorAll('video').forEach(function(v){v.pause();});", null);
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- channels storage

    private void loadChannels() {
        channels.clear();
        String json = prefs.getString(KEY_CHANNELS, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    channels.add(new String[]{o.getString("name"), o.getString("url"),
                            o.optString("stream", ""), o.optString("type", "tv")});
                }
            } catch (Exception ignored) {
                channels.clear();
            }
        }
        if (channels.isEmpty()) {
            for (String[] c : DEFAULT_CHANNELS) channels.add(c.clone());
        }
    }

    private void saveChannels() {
        JSONArray arr = new JSONArray();
        try {
            for (String[] c : channels) {
                JSONObject o = new JSONObject();
                o.put("name", c[C_NAME]);
                o.put("url", c[C_PAGE]);
                o.put("stream", c[C_STREAM]);
                o.put("type", c[C_TYPE]);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs.edit().putString(KEY_CHANNELS, arr.toString()).apply();
    }

    // ---------------------------------------------------------------- UI

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // --- WebView player
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setTextZoom(100);
        applyUserAgent();

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.BLACK);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                // Keep http(s) inside the app; ignore intent://, market:// etc.
                return !("http".equals(scheme) || "https".equals(scheme));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                // Start looking for the video right away instead of waiting for every ad/tracker to finish.
                if (!"about:blank".equals(url)) schedulePlayerTweaks();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return blockSlow && isSlowHost(request.getUrl().getHost()) ? emptyResponse() : null;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                fullscreenContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onHideCustomView() {
                exitCustomView();
            }

            @Override
            public Bitmap getDefaultVideoPoster() {
                // Hides the grey "play" placeholder some pages show before the video starts.
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildPlayerUi();

        // --- container for HTML5 fullscreen video
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.setVisibility(View.GONE);
        root.addView(fullscreenContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // --- channel name banner
        infoBanner = new TextView(this);
        infoBanner.setTextColor(Color.WHITE);
        infoBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        infoBanner.setTypeface(Typeface.DEFAULT_BOLD);
        infoBanner.setPadding(dp(20), dp(10), dp(20), dp(10));
        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(0xCC0B1E3F);
        bannerBg.setCornerRadius(dp(10));
        infoBanner.setBackground(bannerBg);
        infoBanner.setVisibility(View.GONE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        blp.setMargins(dp(24), dp(24), dp(24), dp(24));
        root.addView(infoBanner, blp);

        // --- mouse pointer
        cursor = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xDDFFFFFF);
        dot.setStroke(dp(3), 0xFF1F5FBF);
        cursor.setBackground(dot);
        cursor.setVisibility(View.GONE);
        cursor.setElevation(dp(20));
        root.addView(cursor, new FrameLayout.LayoutParams(dp(22), dp(22)));

        // --- overlay: channel list + settings
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(20), dp(20), dp(20), dp(20));
        overlay.setBackgroundColor(0xEE081428);
        overlay.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText("ערוצים");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(10));
        overlay.addView(title);

        ScrollView scroll = new ScrollView(this);
        channelListView = new LinearLayout(this);
        channelListView.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(channelListView);
        overlay.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("OK ארוך על ערוץ = עריכה  ·  בצפייה: למעלה/למטה = ערוץ, ימינה/שמאלה = עכבר  ·  חזור פעמיים = יציאה");
        hint.setTextColor(0xFFAFC4E6);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setPadding(0, dp(10), 0, 0);
        overlay.addView(hint);

        FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(
                dp(420), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        root.addView(overlay, olp);

        buildVodUi();
        setContentView(root);
        rebuildList();
    }

    private void applyUserAgent() {
        WebSettings s = webView.getSettings();
        s.setUserAgentString(desktopMode ? DESKTOP_UA : null);
    }

    private Button makeButton(String text, boolean highlighted) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);

        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(dp(8));
        normal.setColor(highlighted ? 0xFF16335F : 0x00000000);
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(dp(8));
        focused.setColor(0xFF1F5FBF);
        focused.setStroke(dp(2), Color.WHITE);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{android.R.attr.state_pressed}, focused);
        bg.addState(new int[]{}, normal);
        b.setBackground(bg);
        b.setStateListAnimator(null);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, dp(3));
        b.setLayoutParams(lp);
        return b;
    }

    private final List<View> channelButtons = new ArrayList<>();

    private boolean isRadio(int idx) {
        return idx >= 0 && idx < channels.size() && "radio".equals(channels.get(idx)[C_TYPE]);
    }

    private void rebuildList() {
        channelListView.removeAllViews();
        channelButtons.clear();
        addSectionLabel("📺  טלוויזיה");
        boolean radioHeader = false;
        for (int i = 0; i < channels.size(); i++) {
            final int idx = i;
            if (isRadio(i) && !radioHeader) {
                addSectionLabel("📻  רדיו");
                radioHeader = true;
            }
            String label = (i + 1) + "   " + channels.get(i)[C_NAME] + (i == current ? "   ▶" : "");
            Button b = makeButton(label, i == current);
            channelButtons.add(b);
            b.setOnClickListener(v -> {
                hideOverlay();
                playChannel(idx);
            });
            b.setOnLongClickListener(v -> {
                editChannelDialog(idx);
                return true;
            });
            channelListView.addView(b);
        }

        addSectionLabel("פעולות");

        if (current >= 0 && !isRadio(current)) {
            Button site = makeButton(nativeActive
                    ? "🌐  פתיחת הערוץ דרך האתר (אם יש בעיה בנגן)"
                    : "▶  ניסיון בנגן הישיר", false);
            site.setOnClickListener(v -> {
                hideOverlay();
                if (nativeActive) playWeb(current, null);
                else playChannel(current);
            });
            channelListView.addView(site);
        }

        Button vod = makeButton("🎬  ספריית VOD – לפי זמנים / לפי תכניות", false);
        vod.setOnClickListener(v -> {
            hideOverlay();
            openVodHome();
        });
        channelListView.addView(vod);

        Button mouse = makeButton("🖱  מצב עכבר (ללחיצה על כפתורים באתר)", false);
        mouse.setOnClickListener(v -> {
            hideOverlay();
            setCursorMode(true);
        });
        channelListView.addView(mouse);

        Button reload = makeButton("⟳  טעינה מחדש של הערוץ", false);
        reload.setOnClickListener(v -> {
            hideOverlay();
            if (current >= 0) playChannel(current);
        });
        channelListView.addView(reload);

        Button fill = makeButton("⛶  וידאו במסך מלא: " + (fillMode ? "פועל" : "כבוי"), false);
        fill.setOnClickListener(v -> {
            fillMode = !fillMode;
            prefs.edit().putBoolean(KEY_FILL, fillMode).apply();
            rebuildList();
            focusFirst();
            if (current >= 0) playChannel(current);
        });
        channelListView.addView(fill);

        Button ua = makeButton("💻  תצוגת אתר: " + (desktopMode ? "מחשב" : "נייד"), false);
        ua.setOnClickListener(v -> {
            desktopMode = !desktopMode;
            prefs.edit().putBoolean(KEY_DESKTOP, desktopMode).apply();
            applyUserAgent();
            rebuildList();
            focusFirst();
            if (current >= 0) playChannel(current);
        });
        channelListView.addView(ua);

        Button fast = makeButton("⚡  טעינה מהירה (דילוג על רכיבי מעקב): " + (blockSlow ? "פועל" : "כבוי"), false);
        fast.setOnClickListener(v -> {
            blockSlow = !blockSlow;
            prefs.edit().putBoolean(KEY_BLOCK, blockSlow).apply();
            rebuildList();
            focusFirst();
            if (current >= 0) playChannel(current);
        });
        channelListView.addView(fast);

        Button add = makeButton("＋  הוספת ערוץ", false);
        add.setOnClickListener(v -> editChannelDialog(-1));
        channelListView.addView(add);

        Button reset = makeButton("↺  איפוס רשימת הערוצים", false);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("לאפס את רשימת הערוצים לברירת המחדל?")
                .setPositiveButton("איפוס", (d, w) -> {
                    prefs.edit().remove(KEY_CHANNELS).remove(KEY_LAST).apply();
                    loadChannels();
                    current = -1;
                    rebuildList();
                    focusFirst();
                })
                .setNegativeButton("ביטול", null)
                .show());
        channelListView.addView(reset);
    }

    private void addSectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFAFC4E6);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setPadding(dp(6), dp(18), 0, dp(4));
        channelListView.addView(t);
    }

    private void focusFirst() {
        if (channelButtons.isEmpty()) return;
        int target = (current >= 0 && current < channelButtons.size()) ? current : 0;
        channelButtons.get(target).requestFocus();
    }

    private void showOverlay() {
        if (cursorMode) setCursorMode(false);
        rebuildList();
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
        handler.post(this::focusFirst);
    }

    private void hideOverlay() {
        overlay.setVisibility(View.GONE);
        webView.requestFocus();
    }

    private void showBanner(String text) {
        infoBanner.setText(text);
        infoBanner.setVisibility(View.VISIBLE);
        infoBanner.bringToFront();
        handler.removeCallbacks(hideBanner);
        handler.postDelayed(hideBanner, 3500);
    }

    // ---------------------------------------------------------------- edit dialog

    private void editChannelDialog(int idx) {
        final boolean isNew = idx < 0;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), 0);

        final EditText name = new EditText(this);
        name.setHint("שם הערוץ");
        name.setSingleLine(true);
        final EditText url = new EditText(this);
        url.setHint("https://...");
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText stream = new EditText(this);
        stream.setHint("קישור ישיר לשידור (לא חובה) – .m3u8 / mp3");
        stream.setSingleLine(true);
        stream.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final android.widget.CheckBox radio = new android.widget.CheckBox(this);
        radio.setText("תחנת רדיו");
        if (!isNew) {
            name.setText(channels.get(idx)[C_NAME]);
            url.setText(channels.get(idx)[C_PAGE]);
            stream.setText(channels.get(idx)[C_STREAM]);
            radio.setChecked(isRadio(idx));
        }
        url.setHint("כתובת האתר של הערוץ https://...");
        box.addView(name);
        box.addView(url);
        box.addView(stream);
        box.addView(radio);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "ערוץ חדש" : "עריכת ערוץ")
                .setView(box)
                .setPositiveButton("שמירה", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String u = url.getText().toString().trim();
                    if (n.isEmpty() || u.isEmpty()) return;
                    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
                    String[] ch = {n, u, stream.getText().toString().trim(), radio.isChecked() ? "radio" : "tv"};
                    if (isNew) channels.add(ch);
                    else channels.set(idx, ch);
                    streamCache.remove(n);
                    saveChannels();
                    rebuildList();
                    focusFirst();
                })
                .setNegativeButton("ביטול", null);
        if (!isNew) {
            b.setNeutralButton("מחיקה", (d, w) -> {
                channels.remove(idx);
                if (current == idx) current = -1;
                else if (current > idx) current--;
                prefs.edit().putInt(KEY_LAST, current).apply();
                saveChannels();
                rebuildList();
                focusFirst();
            });
        }
        b.show();
    }

    // ---------------------------------------------------------------- playback

    /**
     * Plays a channel: first the direct-stream candidates in the native player (instant, no site),
     * and if they all fail, the channel's web page.
     */
    private void playChannel(int idx) {
        if (channels.isEmpty()) return;
        idx = ((idx % channels.size()) + channels.size()) % channels.size();
        exitCustomView();
        vodPlaying = false;
        current = idx;
        prefs.edit().putInt(KEY_LAST, idx).apply();
        handler.removeCallbacks(switchRunner);
        pendingChannel = -1;
        String[] c = channels.get(idx);
        List<String> cands = new ArrayList<>();
        for (String part : c[C_STREAM].split("\\|")) {
            String t = part.trim();
            if (!t.isEmpty()) cands.add(t);
        }
        if (cands.isEmpty()) {
            playWeb(idx, null);
            return;
        }
        // Stop the web player while the native one starts.
        handler.removeCallbacks(tweakRunner);
        webView.stopLoading();
        webView.loadUrl("about:blank");
        exitCustomView();
        showBanner((idx + 1) + "  ·  " + c[C_NAME] + (isRadio(idx) ? "" : "\nOK = רשימת ערוצים"));
        tryCandidate(++playGen, idx, cands, 0);
    }

    /** Shows the channel's web page in the WebView (the original mode). */
    private void playWeb(int idx, String note) {
        stopNative();
        if (idx < 0 || idx >= channels.size()) return;
        String[] c = channels.get(idx);
        showBanner((note != null ? note + "\n" : "") + (idx + 1) + "  ·  " + c[C_NAME]
                + "\n← → = מצב עכבר  ·  OK = רשימת ערוצים");
        handler.removeCallbacks(tweakRunner);
        webView.setVisibility(View.VISIBLE);
        webView.stopLoading();
        webView.loadUrl(c[C_PAGE]);
    }

    // ================================================================ native player

    private androidx.media3.exoplayer.ExoPlayer player;
    private androidx.media3.ui.PlayerView playerView;
    private LinearLayout radioPanel;
    private TextView radioName, radioStatus;
    private boolean nativeActive = false;
    private int playGen = 0;
    /** Streams found on a site ("sniff"), reused for a while because they usually carry a token. */
    private final Map<String, String[]> streamCache = new HashMap<>(); // name -> {url, timeMillis}
    private static final long STREAM_CACHE_MS = 15 * 60 * 1000;

    private void buildPlayerUi() {
        playerView = new androidx.media3.ui.PlayerView(this);
        playerView.setUseController(false);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setKeepContentOnPlayerReset(false);
        playerView.setFocusable(false);
        playerView.setVisibility(View.GONE);
        // index 1: above the WebView, below everything else
        root.addView(playerView, 1, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        radioPanel = new LinearLayout(this);
        radioPanel.setOrientation(LinearLayout.VERTICAL);
        radioPanel.setGravity(Gravity.CENTER);
        radioPanel.setBackgroundColor(0xFF081428);
        radioPanel.setVisibility(View.GONE);
        TextView icon = new TextView(this);
        icon.setText("📻");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
        icon.setGravity(Gravity.CENTER);
        radioPanel.addView(icon);
        radioName = new TextView(this);
        radioName.setTextColor(Color.WHITE);
        radioName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        radioName.setTypeface(Typeface.DEFAULT_BOLD);
        radioName.setGravity(Gravity.CENTER);
        radioPanel.addView(radioName);
        radioStatus = new TextView(this);
        radioStatus.setTextColor(0xFFAFC4E6);
        radioStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        radioStatus.setGravity(Gravity.CENTER);
        radioStatus.setPadding(0, dp(12), 0, 0);
        radioPanel.addView(radioStatus);
        TextView hint = new TextView(this);
        hint.setText("למעלה/למטה = תחנה הבאה  ·  OK = רשימה  ·  חזור = יציאה");
        hint.setTextColor(0xFF6F84A8);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(40), 0, 0);
        radioPanel.addView(hint);
        root.addView(radioPanel, 2, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private androidx.media3.exoplayer.ExoPlayer ensurePlayer() {
        if (player == null) {
            player = new androidx.media3.exoplayer.ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
        }
        return player;
    }

    private void stopNative() {
        playGen++;
        handler.removeCallbacks(nativeTimeout);
        nativeActive = false;
        if (player != null) {
            player.stop();
            player.clearMediaItems();
            if (playerListener != null) player.removeListener(playerListener);
            playerListener = null;
        }
        stopSniff();
        if (playerView != null) playerView.setVisibility(View.GONE);
        if (radioPanel != null) radioPanel.setVisibility(View.GONE);
    }

    private androidx.media3.common.Player.Listener playerListener;
    private Runnable nativeTimeout = () -> { };

    private void tryCandidate(int gen, int idx, List<String> cands, int i) {
        if (gen != playGen) return;
        if (i >= cands.size()) {
            if (isRadio(idx)) {
                stopNative();
                showRadioPanel(idx, "לא הצלחתי לנגן את התחנה כרגע. אפשר לנסות שוב בעוד רגע (OK ← טעינה מחדש).");
            } else {
                playWeb(idx, "הנגן הישיר לא זמין – פותח את אתר הערוץ");
            }
            return;
        }
        String cand = cands.get(i);
        String name = channels.get(idx)[C_NAME];
        String page = channels.get(idx)[C_PAGE];
        if ("sniff".equalsIgnoreCase(cand)) {
            String[] cached = streamCache.get(name);
            if (cached != null && System.currentTimeMillis() - Long.parseLong(cached[1]) < STREAM_CACHE_MS) {
                playNative(gen, idx, cached[0], page, () -> {
                    streamCache.remove(name);
                    sniffThen(gen, idx, cands, i);
                });
            } else {
                sniffThen(gen, idx, cands, i);
            }
        } else {
            playNative(gen, idx, cand, page, () -> tryCandidate(gen, idx, cands, i + 1));
        }
    }

    private void sniffThen(int gen, int idx, List<String> cands, int i) {
        if (isRadio(idx)) setRadioStatus("מחפש את השידור באתר…");
        sniff(channels.get(idx)[C_PAGE], url -> {
            if (gen != playGen) return;
            if (url == null) {
                tryCandidate(gen, idx, cands, i + 1);
                return;
            }
            streamCache.put(channels.get(idx)[C_NAME],
                    new String[]{url, String.valueOf(System.currentTimeMillis())});
            playNative(gen, idx, url, channels.get(idx)[C_PAGE], () -> tryCandidate(gen, idx, cands, i + 1));
        });
    }

    /** Plays url natively; calls onFail if it errors or does not start within the timeout. */
    private void playNative(int gen, int idx, String url, String referer, Runnable onFail) {
        if (gen != playGen) return;
        androidx.media3.exoplayer.ExoPlayer p = ensurePlayer();
        if (playerListener != null) p.removeListener(playerListener);
        handler.removeCallbacks(nativeTimeout);

        Map<String, String> headers = new HashMap<>();
        if (referer != null && referer.startsWith("http")) {
            headers.put("Referer", referer);
            try {
                java.net.URL r = new java.net.URL(referer);
                headers.put("Origin", r.getProtocol() + "://" + r.getHost());
            } catch (Exception ignored) {
            }
        }
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null) headers.put("Cookie", cookies);
        androidx.media3.datasource.DefaultHttpDataSource.Factory http =
                new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setUserAgent(DESKTOP_UA)
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(10000)
                        .setReadTimeoutMs(15000)
                        .setDefaultRequestProperties(headers);
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory msf =
                new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(http);
        androidx.media3.common.MediaItem.Builder mi = new androidx.media3.common.MediaItem.Builder().setUri(url);
        if (url.toLowerCase(Locale.US).contains(".m3u8")) mi.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8);

        final boolean[] done = {false};
        playerListener = new androidx.media3.common.Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (gen != playGen) return;
                if (state == androidx.media3.common.Player.STATE_READY && !done[0]) {
                    done[0] = true;
                    handler.removeCallbacks(nativeTimeout);
                    if (isRadio(idx)) setRadioStatus("מנגן ♪");
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                if (gen != playGen) return;
                handler.removeCallbacks(nativeTimeout);
                if (!done[0]) {
                    done[0] = true;
                    onFail.run();
                } else {
                    // Was playing and dropped (network hiccup): restart this channel.
                    handler.postDelayed(() -> {
                        if (gen == playGen) playChannel(idx);
                    }, 1500);
                }
            }
        };
        p.addListener(playerListener);
        nativeTimeout = () -> {
            if (gen == playGen && !done[0]) {
                done[0] = true;
                p.stop();
                onFail.run();
            }
        };
        handler.postDelayed(nativeTimeout, 15000);

        nativeActive = true;
        if (isRadio(idx)) {
            playerView.setVisibility(View.GONE);
            showRadioPanel(idx, "מתחבר…");
        } else {
            radioPanel.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);
        }
        p.setMediaSource(msf.createMediaSource(mi.build()));
        p.setPlayWhenReady(true);
        p.prepare();
    }

    private void showRadioPanel(int idx, String status) {
        radioName.setText(channels.get(idx)[C_NAME]);
        radioStatus.setText(status);
        radioPanel.setVisibility(View.VISIBLE);
        playerView.setVisibility(View.GONE);
    }

    private void setRadioStatus(String s) {
        if (radioStatus != null) radioStatus.setText(s);
    }

    // ---- "sniff": open the site in the hidden WebView and catch the first video playlist it requests

    interface SniffCallback {
        void found(String url);
    }

    private volatile boolean sniffing = false;
    private int sniffId = 0;
    private SniffCallback sniffCallback;

    private void sniff(String page, SniffCallback cb) {
        cancelScrape();
        stopSniff();
        final int id = ++sniffId;
        sniffCallback = cb;
        sniffing = true;
        scraper.getSettings().setMediaPlaybackRequiresUserGesture(false);
        scraper.loadUrl(page);
        // Nudge players that wait for a click.
        Runnable nudge = new Runnable() {
            int n = 0;

            @Override
            public void run() {
                if (id != sniffId || !sniffing) return;
                scraper.evaluateJavascript("(function(){try{" + COOKIE_SCRIPT
                        + "document.querySelectorAll('video').forEach(function(v){v.muted=true;"
                        + "var p=v.play&&v.play();if(p&&p.catch)p.catch(function(){});});"
                        + "var b=document.querySelector('.vjs-big-play-button,.jw-display-icon-container,"
                        + "button[aria-label*=\"play\" i],button[aria-label*=\"נגן\"],[class*=\"bigPlay\" i],[class*=\"play-button\" i]');"
                        + "if(b&&!b.__itvS){b.__itvS=1;b.click();}}catch(e){}})();", null);
                if (++n < 20) handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(nudge, 1500);
        handler.postDelayed(() -> {
            if (id == sniffId && sniffing) onSniffResult(id, null);
        }, 20000);
    }

    /** Called from the scraper's network thread for every request while sniffing. */
    private void sniffRequest(String url) {
        if (!sniffing || url == null) return;
        String u = url.toLowerCase(Locale.US);
        if (!u.contains(".m3u8")) return;
        if (u.contains("/ads") || u.contains("advert") || u.contains("preroll") || u.contains("doubleclick")) return;
        final int id = sniffId;
        sniffing = false;
        handler.post(() -> onSniffResult(id, url));
    }

    private void onSniffResult(int id, String url) {
        if (id != sniffId) return;
        SniffCallback cb = sniffCallback;
        stopSniff();
        if (cb != null) cb.found(url);
    }

    private void stopSniff() {
        sniffId++;
        sniffing = false;
        sniffCallback = null;
        if (scraper != null) {
            scraper.stopLoading();
            scraper.loadUrl("about:blank");
            scraper.getSettings().setMediaPlaybackRequiresUserGesture(true);
        }
    }

    // ---- fast zapping: show the target immediately, load only when the user stops pressing.
    private int pendingChannel = -1;
    private final Runnable switchRunner = () -> {
        if (pendingChannel >= 0) {
            int p = pendingChannel;
            pendingChannel = -1;
            playChannel(p);
        }
    };

    private void zap(int delta) {
        if (channels.isEmpty()) return;
        int n = channels.size();
        int base = pendingChannel >= 0 ? pendingChannel : Math.max(current, 0);
        boolean radio = isRadio(base);
        int target = base;
        for (int k = 0; k < n; k++) { // next channel of the same kind (TV / radio)
            target = ((target + delta) % n + n) % n;
            if (isRadio(target) == radio) break;
        }
        pendingChannel = target;
        showBanner((target + 1) + "  ·  " + channels.get(target)[0]);
        handler.removeCallbacks(switchRunner);
        handler.postDelayed(switchRunner, 450);
    }

    // ---- skip loading trackers / recommendation widgets that slow the sites down
    private static final String[] SLOW_HOSTS = {
            "taboola.com", "outbrain.com", "outbrainimg.com", "connect.facebook.net", "facebook.com",
            "hotjar.com", "hotjar.io", "clarity.ms", "scorecardresearch.com", "chartbeat.com",
            "chartbeat.net", "heyday.io", "criteo.com", "criteo.net", "teads.tv", "mgid.com",
            "tiktok.com", "analytics.tiktok.com", "doubleverify.com", "moatads.com", "quantserve.com",
            "newrelic.com", "nr-data.net", "sentry.io", "onesignal.com", "pushwoosh.com",
            "twitter.com", "platform.twitter.com", "instagram.com", "linkedin.com", "pinterest.com",
            "yandex.ru", "mc.yandex.ru", "glassbox.com", "cdn.glassboxcdn.com", "survicate.com",
    };

    private static boolean isSlowHost(String host) {
        if (host == null) return false;
        for (String h : SLOW_HOSTS) {
            if (host.equals(h) || host.endsWith("." + h)) return true;
        }
        return false;
    }

    private static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    private int tweakCount = 0;
    private final Runnable tweakRunner = new Runnable() {
        @Override
        public void run() {
            webView.evaluateJavascript(playerScript(), null);
            int n = ++tweakCount;
            if (n < 45) handler.postDelayed(this, n < 15 ? 700 : 2000);
        }
    };

    /** Runs the autoplay / fill-screen / cookie-banner script repeatedly while the page loads. */
    private void schedulePlayerTweaks() {
        handler.removeCallbacks(tweakRunner);
        tweakCount = 0;
        handler.postDelayed(tweakRunner, 500);
    }

    /**
     * Closes cookie / consent banners. Prefers "reject" / "necessary only", then "close",
     * and only then "accept".
     */
    private static final String COOKIE_SCRIPT =
            "function itvVis(e){try{var r=e.getBoundingClientRect();var s=getComputedStyle(e);"
            + "return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none';}catch(x){return false;}}"
            + "function itvCookies(){"
            + "var known=['#onetrust-reject-all-handler','.fc-cta-do-not-consent','#CybotCookiebotDialogBodyButtonDecline',"
            + "'#onetrust-close-btn-container button','.onetrust-close-btn-handler','#onetrust-accept-btn-handler',"
            + "'.fc-cta-consent','#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll'];"
            + "for(var i=0;i<known.length;i++){var k=document.querySelector(known[i]);"
            + "if(k&&itvVis(k)&&!k.__itvc){k.__itvc=1;k.click();return;}}"
            + "var pri=[/^(דחה|דחייה|דחיה|דחה הכל|סרב|לא מסכים|רק הכרחיות|רק עוגיות הכרחיות|reject|reject all|decline|necessary only)$/i,"
            + "/^(סגור|סגירה|×|✕|✖|x|close)$/i,"
            + "/^(אישור|אשר|אשר הכל|אני מסכים|אני מסכימה|מסכים|מאשר|אני מאשר|הבנתי|קבל|קבלה|המשך|accept|accept all|agree|i agree|ok|got it)$/i];"
            + "var all=Array.prototype.slice.call(document.querySelectorAll('button,a,[role=button],[onclick],input[type=button],input[type=submit]'));"
            + "var c=all.filter(function(b){if(b.__itvc||!itvVis(b))return false;var p=b;"
            + "for(var d=0;d<7&&p;d++){p=p.parentElement;if(!p)break;var t=p.innerText||'';"
            + "if(t.length<2500&&/עוגיות|עוגייה|cookie|קוקיז/i.test(t))return true;}return false;});"
            + "for(var j=0;j<pri.length;j++){for(var m=0;m<c.length;m++){"
            + "var tx=((c[m].innerText||c[m].value||'')+'').replace(/\\s+/g,' ').trim();"
            + "if(!tx)tx=(c[m].getAttribute('aria-label')||c[m].getAttribute('title')||'').trim();"
            + "if(pri[j].test(tx)){c[m].__itvc=1;c[m].click();return;}}}}"
            + "try{itvCookies();}catch(x){}";

    private String playerScript() {
        return "(function(){" + COOKIE_SCRIPT
                + "var fill=" + fillMode + ";"
                + "function big(list){var best=null,area=0;for(var i=0;i<list.length;i++){"
                + "var r=list[i].getBoundingClientRect();var a=r.width*r.height;"
                + "if(a>area){area=a;best=list[i];}}return best;}"
                + "function cover(el){if(!el||el.__itv)return;el.__itv=1;"
                + "var st='position:fixed!important;top:0!important;left:0!important;"
                + "width:100vw!important;height:100vh!important;max-width:none!important;"
                + "max-height:none!important;z-index:2147483646!important;background:#000!important;"
                + "margin:0!important;transform:none!important;';"
                + "if(el.tagName==='VIDEO')st+='object-fit:contain!important;';"
                + "el.setAttribute('style',(el.getAttribute('style')||'')+';'+st);"
                + "document.documentElement.style.overflow='hidden';document.body.style.overflow='hidden';}"
                + "var v=big(document.querySelectorAll('video'));"
                + "if(v){if(v.paused&&v.play){var p=v.play();if(p&&p.catch)p.catch(function(){});}"
                + "if(fill)cover(v);return 'video';}"
                + "var btn=document.querySelector('.vjs-big-play-button,.jw-display-icon-container,"
                + "button[aria-label*=\"play\" i],button[aria-label*=\"נגן\"],[class*=\"bigPlay\" i],[class*=\"play-button\" i]');"
                + "if(btn&&!btn.__itvClicked){btn.__itvClicked=1;btn.click();}"
                + "if(fill){var fr=document.querySelectorAll('iframe');var f=big(fr);"
                + "if(f){var r=f.getBoundingClientRect();if(r.width>=320&&r.height>=180)cover(f);}}"
                + "return 'none';})();";
    }

    private void togglePlayPause() {
        if (nativeActive && player != null) {
            player.setPlayWhenReady(!player.getPlayWhenReady());
            showBanner(player.getPlayWhenReady() ? "▶" : "⏸");
            return;
        }
        webView.evaluateJavascript("(function(){var vs=document.querySelectorAll('video');"
                + "vs.forEach(function(v){if(v.paused)v.play();else v.pause();});})();", null);
    }

    private void exitCustomView() {
        if (customView == null) return;
        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        customView = null;
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }

    // ---------------------------------------------------------------- mouse mode

    private void setCursorMode(boolean on) {
        if (on && nativeActive) {
            // Nothing to click: the channel plays in the built-in player, not a web page.
            if (current >= 0) showBanner((current + 1) + "  ·  " + channels.get(current)[C_NAME]
                    + "\nOK = רשימת ערוצים  ·  למעלה/למטה = החלפה");
            return;
        }
        cursorMode = on;
        if (on) {
            if (cursorX == 0 && cursorY == 0) {
                cursorX = root.getWidth() / 2f;
                cursorY = root.getHeight() / 2f;
            }
            moveCursor(0, 0);
            cursor.setVisibility(View.VISIBLE);
            cursor.bringToFront();
            showBanner("מצב עכבר · חיצים = הזזה · OK = לחיצה · חזור = יציאה");
        } else {
            cursor.setVisibility(View.GONE);
        }
    }

    private void moveCursor(float dx, float dy) {
        int w = root.getWidth(), h = root.getHeight();
        cursorX += dx;
        cursorY += dy;
        int edge = dp(4);
        // Scroll the page when pushing past the top / bottom edge.
        if (cursorY < edge) {
            webView.scrollBy(0, (int) (cursorY - edge));
            cursorY = edge;
        } else if (cursorY > h - edge) {
            webView.scrollBy(0, (int) (cursorY - (h - edge)));
            cursorY = h - edge;
        }
        cursorX = Math.max(edge, Math.min(w - edge, cursorX));
        int half = dp(11);
        cursor.setTranslationX(cursorX - half);
        cursor.setTranslationY(cursorY - half);
    }

    private void clickAtCursor() {
        View target = customView != null ? customView : webView;
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, cursorX, cursorY, 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(down);
        target.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    // ================================================================ VOD

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void buildVodUi() {
        // Hidden WebView that loads site pages and extracts lists of programs / episodes.
        scraper = new WebView(this);
        WebSettings s = scraper.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(DESKTOP_UA);
        CookieManager.getInstance().setAcceptThirdPartyCookies(scraper, true);
        scraper.setVisibility(View.INVISIBLE);
        scraper.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void result(int id, String json) {
                handler.post(() -> onScrapeResult(id, json));
            }
        }, "ITVScraper");
        scraper.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                return !("http".equals(scheme) || "https".equals(scheme));
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (sniffing) sniffRequest(request.getUrl().toString());
                return blockSlow && isSlowHost(request.getUrl().getHost()) ? emptyResponse() : null;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (scrapeCallback == null || scrapeStarted || "about:blank".equals(url)) return;
                scrapeStarted = true;
                final int id = scrapeId;
                handler.postDelayed(() -> {
                    if (id == scrapeId && scrapeCallback != null) {
                        scraper.evaluateJavascript(extractScript(id, scrapeKey), null);
                    }
                }, 2500);
            }
        });
        root.addView(scraper, 0, new FrameLayout.LayoutParams(dp(1280), dp(720)));

        // Full-screen VOD browser.
        vodScreen = new FrameLayout(this);
        vodScreen.setBackgroundColor(0xF5081428);
        vodScreen.setVisibility(View.GONE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(40), dp(24), dp(40), dp(16));

        vodHeader = new TextView(this);
        vodHeader.setTextColor(Color.WHITE);
        vodHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        vodHeader.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(vodHeader);

        vodStatus = new TextView(this);
        vodStatus.setTextColor(0xFFAFC4E6);
        vodStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        vodStatus.setPadding(0, dp(4), 0, dp(10));
        col.addView(vodStatus);

        vodScroll = new ScrollView(this);
        vodList = new LinearLayout(this);
        vodList.setOrientation(LinearLayout.VERTICAL);
        vodScroll.addView(vodList);
        col.addView(vodScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("OK = בחירה  ·  חזור = מסך קודם");
        hint.setTextColor(0xFFAFC4E6);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setPadding(0, dp(8), 0, 0);
        col.addView(hint);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                dp(900), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
        vodScreen.addView(col, clp);
        root.addView(vodScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---- navigation stack

    private void vodPush(Runnable screen) {
        vodStack.add(screen);
        screen.run();
    }

    private void vodBack() {
        cancelScrape();
        if (!vodStack.isEmpty()) vodStack.remove(vodStack.size() - 1);
        if (vodStack.isEmpty()) {
            closeVod();
        } else {
            vodStack.get(vodStack.size() - 1).run();
        }
    }

    private void closeVod() {
        cancelScrape();
        vodStack.clear();
        vodScreen.setVisibility(View.GONE);
        // Leaving the library returns to live TV.
        if (current >= 0) {
            playChannel(current);
        } else {
            vodPlaying = false;
            webView.loadUrl("about:blank");
            showOverlay();
        }
    }

    private void showVodScreen() {
        if (cursorMode) setCursorMode(false);
        overlay.setVisibility(View.GONE);
        vodScreen.setVisibility(View.VISIBLE);
        vodScreen.bringToFront();
    }

    /** Starts a fresh screen; returns its token so async results can check they are still wanted. */
    private int beginScreen(String header, String status) {
        showVodScreen();
        vodToken++;
        vodHeader.setText(header);
        vodStatus.setText(status == null ? "" : status);
        vodList.removeAllViews();
        vodScroll.scrollTo(0, 0);
        return vodToken;
    }

    private Button addVodRow(String text, String sub, boolean enabled, View.OnClickListener click) {
        Button b = makeButton(sub == null || sub.isEmpty() ? text : sub + "   ·   " + text, false);
        if (!enabled) b.setTextColor(0xFF6F84A8);
        if (click != null) b.setOnClickListener(click);
        vodList.addView(b);
        return b;
    }

    private void focusVodRow(int index) {
        handler.post(() -> {
            View v = vodList.getChildAt(Math.max(0, Math.min(index, vodList.getChildCount() - 1)));
            if (v != null) v.requestFocus();
        });
    }

    // ---- screens

    private void openVodHome() {
        vodStack.clear();
        vodPush(() -> {
            beginScreen("ספריית VOD", "בחר ערוץ");
            int focus = 0;
            for (int i = 0; i < VodSource.ALL.length; i++) {
                VodSource src = VodSource.ALL[i];
                addVodRow(src.name, null, true, v -> vodPush(() -> showSourceMenu(src)));
                if (current >= 0 && channels.get(current)[0].equals(src.name)) focus = i;
            }
            focusVodRow(focus);
        });
    }

    private void showSourceMenu(VodSource src) {
        beginScreen(src.name, "איך לחפש?");
        addVodRow("📅  לפי זמנים – מה שודר לאחרונה", null, true, v -> openByTime(src));
        addVodRow("📺  לפי תכניות", null, true,
                v -> vodPush(() -> showList(src.name + " · תכניות", src.programsUrl, src.programRegex,
                        false, item -> vodPush(() -> showEpisodes(src, item, null)))));
        focusVodRow(0);
    }

    private void openByTime(VodSource src) {
        switch (src.timeMode) {
            case VodSource.TIME_KAN_SCHEDULE:
                vodPush(() -> showDays(src));
                break;
            case VodSource.TIME_LATEST:
                vodPush(() -> showList(src.name + " · שודר לאחרונה", src.timeArg, src.latestRegex,
                        src.sortById, item -> playVod(item)));
                break;
            default:
                playVod(new Item(src.name, src.timeArg, null));
                setCursorMode(true);
        }
    }

    private void showDays(VodSource src) {
        beginScreen(src.name + " · לפי זמנים", "בחר יום");
        SimpleDateFormat label = new SimpleDateFormat("EEEE  dd/MM", new Locale("iw"));
        SimpleDateFormat param = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
        Calendar c = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            final String day = param.format(c.getTime());
            String text = i == 0 ? "היום" : i == 1 ? "אתמול" : label.format(c.getTime());
            final String title = text;
            addVodRow(text, null, true, v -> vodPush(() -> showKanDay(src, day, title)));
            c.add(Calendar.DAY_OF_MONTH, -1);
        }
        focusVodRow(0);
    }

    /** Kan broadcast schedule for one day; past items open the show's episodes. */
    private void showKanDay(VodSource src, String day, String dayLabel) {
        final String header = src.name + " · " + dayLabel;
        final String key = "kan:" + src.timeArg + ":" + day;
        List<Item> cached = vodCache.get(key);
        final int token = beginScreen(header, cached == null ? "טוען לוח שידורים…" : null);
        if (cached != null) {
            renderKanDay(src, cached);
            return;
        }
        new Thread(() -> {
            List<Item> items = null;
            try {
                int offset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000;
                String html = httpGet("https://www.kan.org.il/umbraco/surface/LoadBroadcastSchedule/LoadSchedule"
                        + "?day=" + day + "&channelId=" + src.timeArg + "&currentPageId=1517", offset);
                items = parseKanSchedule(html);
            } catch (Exception ignored) {
            }
            final List<Item> result = items;
            handler.post(() -> {
                if (token != vodToken) return;
                if (result == null || result.isEmpty()) {
                    vodStatus.setText("לא הצלחתי לטעון את לוח השידורים. אפשר לנסות 'לפי תכניות'.");
                    addVodRow("פתיחת לוח השידורים באתר", null, true, v ->
                            openPageWithMouse("https://www.kan.org.il/tv-guide/?channelId=" + src.timeArg));
                    focusVodRow(0);
                    return;
                }
                vodCache.put(key, result);
                vodStatus.setText("");
                renderKanDay(src, result);
            });
        }).start();
    }

    private void renderKanDay(VodSource src, List<Item> items) {
        int focus = -1;
        for (Item it : items) {
            final Item item = it;
            Button b = addVodRow(it.title + (it.enabled ? "" : "   (לא זמין לצפייה)"), it.sub, it.enabled,
                    it.enabled ? v -> vodPush(() -> showEpisodes(src, item, item.title)) : null);
            if (it.enabled) focus = vodList.indexOfChild(b); // last available = most recent
        }
        if (items.isEmpty()) vodStatus.setText("אין שידורים ביום הזה");
        focusVodRow(focus < 0 ? 0 : focus);
    }

    private List<Item> parseKanSchedule(String html) {
        List<Item> out = new ArrayList<>();
        SimpleDateFormat in = new SimpleDateFormat("d.M.yyyy HH:mm:ss", Locale.US);
        in.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        long now = System.currentTimeMillis();
        Pattern href = Pattern.compile("class=\"mobile-holder[^\"]*\"\\s+href=\"([^\"]*)\"");
        Pattern date = Pattern.compile("data-date-utc=\"([^\"]+)\"");
        Pattern title = Pattern.compile("class=\"program-title\">([\\s\\S]*?)</h3>");
        String[] parts = html.split("class=\"results-item");
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            Matcher mh = href.matcher(p), md = date.matcher(p), mt = title.matcher(p);
            if (!mt.find()) continue;
            String t = decodeHtml(mt.group(1).replaceAll("<[^>]+>", "").trim());
            String link = mh.find() ? mh.group(1) : "#";
            String time = "";
            long start = 0;
            if (md.find()) {
                try {
                    Date d = in.parse(md.group(1));
                    start = d.getTime();
                    time = hm.format(d);
                } catch (Exception ignored) {
                }
            }
            if (link.startsWith("/")) link = "https://www.kan.org.il" + link;
            Item it = new Item(t, link, time);
            it.enabled = !"#".equals(link) && !link.isEmpty() && start < now;
            out.add(it);
        }
        return out;
    }

    private static String decodeHtml(String s) {
        return s.replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
    }

    private String httpGet(String url, int offsetMinutes) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", DESKTOP_UA);
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        c.setRequestProperty("X-Time-Offset", String.valueOf(offsetMinutes));
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null) c.setRequestProperty("Cookie", cookies);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    interface ItemClick {
        void open(Item item);
    }

    /** Generic list screen filled by scraping a page. */
    private void showList(String header, String url, String regex, boolean sortById, ItemClick click) {
        final String key = url + "|" + regex + "|" + sortById;
        List<Item> cached = vodCache.get(key);
        final int token = beginScreen(header, cached == null ? "טוען…" : null);
        if (cached != null) {
            renderList(cached, click, null, url);
            return;
        }
        scrape(url, regex, items -> {
            if (token != vodToken) return;
            if (sortById) sortByIdDesc(items);
            if (!items.isEmpty()) vodCache.put(key, items);
            vodStatus.setText(items.isEmpty() ? "לא נמצאו פריטים באופן אוטומטי." : items.size() + " פריטים");
            renderList(items, click, null, url);
        });
    }

    private void renderList(List<Item> items, ItemClick click, String hint, String pageUrl) {
        int focus = 0;
        for (int i = 0; i < items.size(); i++) {
            final Item it = items.get(i);
            addVodRow(it.title, it.sub, true, v -> click.open(it));
            if (hint != null && focus == 0 && matchesHint(it.title, hint)) focus = i;
        }
        addVodRow("🌐  פתיחת הדף באתר (מצב עכבר)", null, true, v -> openPageWithMouse(pageUrl));
        focusVodRow(focus);
    }

    private static boolean matchesHint(String title, String hint) {
        String t = title.trim();
        if (t.length() < 3) return false;
        return hint.contains(t) || t.contains(hint);
    }

    private void showEpisodes(VodSource src, Item program, String hint) {
        final String key = program.url + "|" + src.episodeRegex;
        List<Item> cached = vodCache.get(key);
        final int token = beginScreen(program.title, cached == null ? "טוען פרקים…" : null);
        if (cached != null) {
            renderList(cached, this::playVod, hint, program.url);
            return;
        }
        scrape(program.url, src.episodeRegex, items -> {
            if (token != vodToken) return;
            // Drop links back to the program page itself.
            List<Item> eps = new ArrayList<>();
            for (Item it : items) if (!sameUrl(it.url, program.url)) eps.add(it);
            if (!eps.isEmpty()) vodCache.put(key, eps);
            vodStatus.setText(eps.isEmpty()
                    ? "לא נמצאו פרקים באופן אוטומטי – אפשר לפתוח את הדף באתר."
                    : eps.size() + " פרקים");
            renderList(eps, this::playVod, hint, program.url);
        });
    }

    private static boolean sameUrl(String a, String b) {
        return a.replaceAll("/+$", "").equalsIgnoreCase(b.replaceAll("/+$", ""));
    }

    private static void sortByIdDesc(List<Item> items) {
        Pattern p = Pattern.compile("(\\d+)/?$");
        Collections.sort(items, (x, y) -> Long.compare(idOf(p, y.url), idOf(p, x.url)));
    }

    private static long idOf(Pattern p, String url) {
        Matcher m = p.matcher(url);
        try {
            return m.find() ? Long.parseLong(m.group(1)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- playback of VOD items

    private void playVod(Item item) {
        stopNative();
        cancelScrape();
        exitCustomView();
        vodPlaying = true;
        vodScreen.setVisibility(View.GONE);
        handler.removeCallbacks(tweakRunner);
        webView.stopLoading();
        webView.loadUrl(item.url);
        webView.requestFocus();
        showBanner(item.title + "\nOK = הפעלה/השהיה · ימינה/שמאלה = דילוג · למעלה/למטה או OK ארוך = מצב עכבר");
    }

    private void openPageWithMouse(String url) {
        playVod(new Item("", url, null));
        handler.postDelayed(() -> setCursorMode(true), 300);
    }

    private void seek(int seconds) {
        webView.evaluateJavascript("(function(){var vs=document.querySelectorAll('video');"
                + "vs.forEach(function(v){try{v.currentTime=Math.max(0,v.currentTime+(" + seconds + "));}catch(e){}});})();",
                null);
        showBanner(seconds > 0 ? "⏩ +" + seconds + " שניות" : "⏪ " + seconds + " שניות");
    }

    // ---- scraping

    private void scrape(String url, String regex, ScrapeCallback cb) {
        cancelScrape();
        scrapeId++;
        scrapeKey = regex;
        scrapeStarted = false;
        scrapeCallback = cb;
        final int id = scrapeId;
        scraper.loadUrl(url);
        // Safety timeout.
        handler.postDelayed(() -> {
            if (id == scrapeId && scrapeCallback != null) {
                if (!scrapeStarted) {
                    scrapeStarted = true;
                    scraper.evaluateJavascript(extractScript(id, scrapeKey), null);
                    handler.postDelayed(() -> {
                        if (id == scrapeId && scrapeCallback != null) onScrapeResult(id, "[]");
                    }, 15000);
                } else {
                    onScrapeResult(id, "[]");
                }
            }
        }, 30000);
    }

    private void cancelScrape() {
        scrapeId++;
        scrapeCallback = null;
        if (scraper != null) {
            scraper.stopLoading();
            scraper.loadUrl("about:blank");
        }
    }

    private void onScrapeResult(int id, String json) {
        if (id != scrapeId || scrapeCallback == null) return;
        ScrapeCallback cb = scrapeCallback;
        scrapeCallback = null;
        List<Item> items = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                items.add(new Item(o.optString("t"), o.optString("u"), o.optString("s", null)));
            }
        } catch (Exception ignored) {
        }
        scraper.loadUrl("about:blank");
        cb.done(items);
    }

    /**
     * Scrolls the page a few times (to trigger lazy loading), then collects links.
     * With a regex: links whose absolute URL matches. Without: same-site links that contain a picture.
     */
    private static String extractScript(int id, String regex) {
        String re = regex == null ? "null" : JSONObject.quote(regex);
        return "(function(){var RE=" + re + ";var n=0;"
                + "function clean(t){t=(t||'').replace(/\\s+/g,' ').trim();"
                + "if(/[\\u0590-\\u05FF]/.test(t)){t=t.replace(/^((poster|image|small|large|big|main|\\d+x\\d+|[\\d_]+)\\s*)+/ig,'');}"
                + "t=t.replace(/^(סט|פוסטר)\\s+/,'').replace(/\\s+-\\s+(HD|רקע|חיים אתגר|ז'אנר|ז׳אנר).*$/,'')"
                + ".replace(/(^|\\s)(יחצ|יח\"צ|פוסטר)(?=\\s|$)/g,' ').replace(/\\s+/g,' ').trim();"
                + "return t.slice(0,100);}"
                + "function titleOf(a){var c=a.querySelector('h1,h2,h3,h4,h5,[class*=\"title\" i]');"
                + "var cands=[c&&c.innerText,a.getAttribute('aria-label'),a.getAttribute('title')];"
                + "var img=a.querySelector('img');if(img){cands.push(img.getAttribute('alt'));cands.push(img.getAttribute('title'));}"
                + "cands.push((a.innerText||'').split('\\n')[0]);"
                + "for(var i=0;i<cands.length;i++){var t=clean(cands[i]);"
                + "if(t&&t.length>1&&!/^(image description|לחצו|צפו|play|watch)$/i.test(t))return t;}return '';}"
                + "function host(h){try{return new URL(h).hostname.split('.').slice(-3).join('.');}catch(e){return '';}}"
                + "function collect(){var rx=RE?new RegExp(RE,'i'):null;var seen={},out=[];"
                + "var me=location.href.replace(/[#?].*$/,'').replace(/\\/+$/,'');var myHost=host(location.href);"
                + "document.querySelectorAll('a[href]').forEach(function(a){var h=a.href;if(!h||h.indexOf('http')!==0)return;"
                + "var bare=h.replace(/#.*$/,'');"
                + "if(rx){if(!rx.test(bare))return;}"
                + "else{if(!a.querySelector('img'))return;if(host(bare)!==myHost&&bare.indexOf(myHost.split('.').slice(-2).join('.'))<0)return;"
                + "if(/login|auth|live|privacy|terms|contact|about|search|facebook|twitter|instagram|whatsapp|youtube|tiktok/i.test(bare))return;"
                + "if(bare.replace(/[?].*$/,'').replace(/\\/+$/,'')===me)return;"
                + "if(new URL(bare).pathname.length<3)return;}"
                + "var key=bare.replace(/\\/+$/,'');var t=titleOf(a);"
                + "if(seen[key]){if(!seen[key].t&&t)seen[key].t=t;return;}"
                + "var o={u:bare,t:t};seen[key]=o;out.push(o);});"
                + "return out.filter(function(o){if(!o.t)o.t=decodeURIComponent(o.u.replace(/\\/+$/,'').split('/').pop()).replace(/[-_]/g,' ');return true;});}"
                + "function step(){window.scrollTo(0,document.body?document.body.scrollHeight:0);"
                + "if(++n<6)setTimeout(step,1300);else{window.scrollTo(0,0);"
                + "try{ITVScraper.result(" + id + ",JSON.stringify(collect()));}catch(e){ITVScraper.result(" + id + ",'[]');}}}"
                + "step();})();";
    }

    // ---------------------------------------------------------------- remote control

    /** OK key: short press runs shortAction (on release), holding it opens mouse mode. */
    private boolean handleOk(KeyEvent e, Runnable shortAction) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                okPressed = true;
                okLong = false;
            } else if (okPressed && !okLong && e.getRepeatCount() >= 2) {
                okLong = true;
                setCursorMode(true);
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            if (okPressed && !okLong) shortAction.run();
            okPressed = false;
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        // ---- VOD browser visible
        if (vodScreen.getVisibility() == View.VISIBLE) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
                if (down && e.getRepeatCount() == 0) vodBack();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }

        // ---- overlay visible: normal list navigation
        if (overlay.getVisibility() == View.VISIBLE) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
                if (down && e.getRepeatCount() == 0) {
                    // Back pressed again shortly after opening the list with Back -> exit.
                    if (code == KeyEvent.KEYCODE_BACK
                            && (SystemClock.uptimeMillis() - lastBack < 2500 || current < 0)) {
                        finish();
                        return true;
                    }
                    if (current >= 0) hideOverlay();
                }
                return true;
            }
            return super.dispatchKeyEvent(e);
        }

        // ---- mouse mode
        if (cursorMode) {
            int step = dp(e.getRepeatCount() > 6 ? 45 : e.getRepeatCount() > 2 ? 25 : 15);
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down) moveCursor(0, -step);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down) moveCursor(0, step);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down) moveCursor(-step, 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) moveCursor(step, 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down && e.getRepeatCount() == 0) clickAtCursor();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (down && e.getRepeatCount() == 0) setCursorMode(false);
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    if (down && e.getRepeatCount() == 0) showOverlay();
                    return true;
                default:
                    // let typing etc. reach the page (e.g. login forms)
                    return super.dispatchKeyEvent(e);
            }
        }

        // ---- watching VOD
        if (vodPlaying) {
            boolean first = down && e.getRepeatCount() == 0;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    if (down) seek(e.getRepeatCount() > 3 ? -60 : -10);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    if (down) seek(e.getRepeatCount() > 3 ? 60 : 10);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    // short press = play/pause, long press = mouse mode
                    return handleOk(e, this::togglePlayPause);
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    if (first) togglePlayPause();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (first) setCursorMode(true);
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    if (first) setCursorMode(true);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (first) {
                        if (customView != null) {
                            exitCustomView();
                        } else if (!vodStack.isEmpty()) {
                            webView.evaluateJavascript(
                                    "document.querySelectorAll('video').forEach(function(v){v.pause();});", null);
                            webView.stopLoading();
                            vodStack.get(vodStack.size() - 1).run();
                        } else {
                            lastBack = SystemClock.uptimeMillis();
                            showOverlay();
                        }
                    }
                    return true;
                default:
                    break; // channel keys / digits fall through to live-TV handling
            }
        }

        // ---- watching
        switch (code) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (down && e.getRepeatCount() % 3 == 0) zap(1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (down && e.getRepeatCount() % 3 == 0) zap(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // short press = channel list, long press = mouse mode
                return handleOk(e, this::showOverlay);
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_GUIDE:
                if (down && e.getRepeatCount() == 0) showOverlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // Xiaomi-style remotes have no menu key: side arrows open the pointer
                // so you can click things on the page (cookie notices, play buttons...).
                if (down && e.getRepeatCount() == 0) setCursorMode(true);
                return true;
            case KeyEvent.KEYCODE_INFO:
                if (down && e.getRepeatCount() == 0 && current >= 0)
                    showBanner((current + 1) + "  ·  " + channels.get(current)[0]);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (down && e.getRepeatCount() == 0) togglePlayPause();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (down && e.getRepeatCount() == 0) {
                    if (customView != null) {
                        exitCustomView();
                    } else {
                        lastBack = SystemClock.uptimeMillis();
                        showOverlay();
                        Toast.makeText(this, "לחץ שוב 'חזור' ליציאה", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            default:
                if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                    if (down && e.getRepeatCount() == 0) {
                        int n = code - KeyEvent.KEYCODE_0;
                        int target = (n == 0 ? 10 : n) - 1;
                        if (target < channels.size()) playChannel(target);
                    }
                    return true;
                }
                return super.dispatchKeyEvent(e);
        }
    }
}
