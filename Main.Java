package com.demo.nytswitcher;

import android.app.Activity;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Main extends Activity {

    // Colors
    static final int C_BG_CARD    = 0xCC0F0C1E;
    static final int C_BORDER     = 0x3378B4FF;
    static final int C_BORDER_ACT = 0xB300DCFF;
    static final int C_CYAN       = 0xFF00DCFF;
    static final int C_GREEN      = 0xFF00FF88;
    static final int C_PINK       = 0xFFFF3B9A;
    static final int C_RED        = 0xFFFF4D5E;
    static final int C_WARN       = 0xFFFFB020;
    static final int C_TEXT       = 0xFFE8E8F0;
    static final int C_TEXT_DIM   = 0x80A0C8FF;

    FrameLayout frame;
    LinearLayout root;
    View scanline, logoDot;
    TextView titleText, subtitle;
    TextView progressTitle, progressPct, tvStatus, tvFps, tvTarget, tvPin, tvTemp, tvFiles;
    FrameLayout progressTrack;
    View progressFill;
    LinearLayout pillMax, pillTh;
    TextView pillMaxName, pillMaxSub, pillThName, pillThSub;

    String gamePkg  = "com.dts.freefiremax";
    String gameName = "FFMAX";
    Uri pickedUri = null;
    boolean busy = false;
    Handler ui = new Handler(Looper.getMainLooper());

    int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (android.os.Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        }
        buildUI();
        startUpdates();
    }

    // ============================================================
    // BUILD UI
    // ============================================================
    void buildUI() {
        frame = new FrameLayout(this);

        // Video bg (optional) or gradient fallback
        VideoView vv = new VideoView(this);
        vv.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        File vid = extractBgVideo();
        if (vid != null && vid.exists()) {
            vv.setVideoURI(Uri.fromFile(vid));
            vv.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                mp.setVolume(0f, 0f);
                int vw = mp.getVideoWidth(), vh = mp.getVideoHeight();
                if (vw > 0 && vh > 0) {
                    int sw = getResources().getDisplayMetrics().widthPixels;
                    int sh = getResources().getDisplayMetrics().heightPixels;
                    float sr = (float) sw / sh, vr = (float) vw / vh;
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) vv.getLayoutParams();
                    if (vr < sr) { lp.width = sw; lp.height = (int) (sw / vr); }
                    else { lp.width = (int) (sh * vr); lp.height = sh; }
                    lp.gravity = Gravity.CENTER;
                    vv.setLayoutParams(lp);
                }
                vv.start();
            });
            frame.addView(vv);
        } else {
            View fallback = new View(this);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF0A0518, 0xFF1A0A2A, 0xFF0A1A2A, 0xFF2A0A1A, 0xFF0A0518});
            fallback.setBackground(g);
            frame.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        }

        // Heavy dim for premium dark look
        View dim = new View(this);
        dim.setBackgroundColor(0xCC000000);
        frame.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        // Content (scrollable)
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(44), dp(20), dp(28));
        scroll.addView(root);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        buildHeader();
        buildProgressCard();
        buildAimCard();
        buildHUD();
        // spacer
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));
        buildPickBtn();

        // scanline
        scanline = new View(this);
        GradientDrawable sl = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x0000DCFF, 0xB300DCFF, 0x0000DCFF});
        scanline.setBackground(sl);
        scanline.setAlpha(0.8f);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, dp(2));
        slp.gravity = Gravity.TOP;
        frame.addView(scanline, slp);
        animateScanline();

        setContentView(frame);
    }

    // ============================================================
    // HEADER — NYT MODS
    // ============================================================
    void buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout logoWrap = new LinearLayout(this);
        logoWrap.setOrientation(LinearLayout.HORIZONTAL);
        logoWrap.setGravity(Gravity.CENTER_VERTICAL);
        logoWrap.setPadding(dp(20), dp(12), dp(24), dp(12));

        GradientDrawable lbg = new GradientDrawable();
        lbg.setColor(0xBF0F0C1E);
        lbg.setCornerRadius(dp(14));
        lbg.setStroke(dp(1), 0x3378B4FF);
        logoWrap.setBackground(lbg);
        logoWrap.setClickable(true);
        logoWrap.setFocusable(true);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.bottomMargin = dp(4);
        logoWrap.setLayoutParams(llp);

        logoDot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(C_CYAN);
        logoDot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.rightMargin = dp(12);
        logoDot.setLayoutParams(dotLp);
        logoWrap.addView(logoDot);

        titleText = new TextView(this);
        titleText.setText("NYT MODS");
        titleText.setTextSize(24);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        titleText.setLetterSpacing(0.15f);
        titleText.setIncludeFontPadding(false);
        logoWrap.addView(titleText);

        // Apply gradient shader after layout
        titleText.post(() -> {
            int w = titleText.getWidth();
            int h = titleText.getHeight();
            if (w > 0 && h > 0) {
                LinearGradient g = new LinearGradient(0, 0, w, h,
                        new int[]{0xFFFFFFFF, 0xFFA0E8FF, 0xFFFFFFFF},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
                titleText.getPaint().setShader(g);
                titleText.invalidate();
            }
        });

        logoWrap.setOnClickListener(v -> activateFiles());
        logoWrap.setOnLongClickListener(v -> { restoreFiles(); return true; });
        header.addView(logoWrap);

        subtitle = new TextView(this);
        subtitle.setText("AIM · HEAD · SYSTEM");
        subtitle.setTextSize(10);
        subtitle.setLetterSpacing(0.4f);
        subtitle.setTypeface(Typeface.DEFAULT_BOLD);
        subtitle.setTextColor(C_TEXT_DIM);
        header.addView(subtitle);

        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-1, -2);
        hLp.bottomMargin = dp(20);
        root.addView(header, hLp);

        // Pulse dot
        pulseDot();
    }

    void pulseDot() {
        ObjectAnimator sx = ObjectAnimator.ofFloat(logoDot, "scaleX", 1f, 0.7f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(logoDot, "scaleY", 1f, 0.7f, 1f);
        ObjectAnimator al = ObjectAnimator.ofFloat(logoDot, "alpha", 1f, 0.4f, 1f);
        sx.setDuration(1600); sx.setRepeatCount(ValueAnimator.INFINITE);
        sy.setDuration(1600); sy.setRepeatCount(ValueAnimator.INFINITE);
        al.setDuration(1600); al.setRepeatCount(ValueAnimator.INFINITE);
        sx.start(); sy.start(); al.start();
    }

    // ============================================================
    // PROGRESS CARD
    // ============================================================
    void buildProgressCard() {
        LinearLayout card = makeCard();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        progressTitle = new TextView(this);
        progressTitle.setText("LOADING");
        progressTitle.setTextSize(10);
        progressTitle.setLetterSpacing(0.3f);
        progressTitle.setTypeface(Typeface.DEFAULT_BOLD);
        progressTitle.setTextColor(C_TEXT_DIM);
        head.addView(progressTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        progressPct = new TextView(this);
        progressPct.setText("0%");
        progressPct.setTextSize(15);
        progressPct.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        progressPct.setTextColor(C_CYAN);
        progressPct.setShadowLayer(12, 0, 0, 0x8000DCFF);
        head.addView(progressPct);

        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.bottomMargin = dp(10);
        card.addView(head, hl);

        progressTrack = new FrameLayout(this);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(0x14FFFFFF);
        tbg.setCornerRadius(dp(3));
        progressTrack.setBackground(tbg);
        progressTrack.setClipToOutline(true);

        progressFill = new View(this);
        GradientDrawable fbg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0088FF, 0xFF00DCFF, 0xFF00FFD0});
        fbg.setCornerRadius(dp(3));
        progressFill.setBackground(fbg);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(0, -1);
        flp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        progressTrack.addView(progressFill, flp);

        View shimmer = new View(this);
        GradientDrawable shbg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF});
        shimmer.setBackground(shbg);
        FrameLayout.LayoutParams shlp = new FrameLayout.LayoutParams(dp(80), -1);
        progressTrack.addView(shimmer, shlp);
        progressTrack.post(() -> {
            ObjectAnimator a = ObjectAnimator.ofFloat(shimmer, "translationX",
                    -dp(80), progressTrack.getWidth() + dp(80));
            a.setDuration(1600);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.start();
        });

        card.addView(progressTrack, new LinearLayout.LayoutParams(-1, dp(6)));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.bottomMargin = dp(12);
        root.addView(card, clp);
    }

    // ============================================================
    // AIM CONTROL CARD
    // ============================================================
    void buildAimCard() {
        LinearLayout card = makeCard();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        View greenDot = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(C_GREEN);
        greenDot.setBackground(gd);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dlp.rightMargin = dp(8);
        head.addView(greenDot, dlp);

        TextView lbl = new TextView(this);
        lbl.setText("AIM CONTROL");
        lbl.setTextSize(10);
        lbl.setLetterSpacing(0.3f);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setTextColor(C_TEXT_DIM);
        head.addView(lbl);

        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.bottomMargin = dp(12);
        card.addView(head, hl);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        pillMax = makePill("FFMAX", "Free Fire Max");
        pillTh  = makePill("FFTH",  "Free Fire");
        pillMaxName = (TextView) pillMax.getChildAt(0);
        pillMaxSub  = (TextView) pillMax.getChildAt(1);
        pillThName  = (TextView) pillTh.getChildAt(0);
        pillThSub   = (TextView) pillTh.getChildAt(1);

        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, -2, 1f);
        p1.rightMargin = dp(10);
        row.addView(pillMax, p1);
        row.addView(pillTh, new LinearLayout.LayoutParams(0, -2, 1f));

        pillMax.setTag("com.dts.freefiremax");
        pillTh.setTag("com.dts.freefireth");
        pillMax.setOnClickListener(v -> onPillClick(pillMax));
        pillTh.setOnClickListener(v -> onPillClick(pillTh));

        updatePills();
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.bottomMargin = dp(12);
        root.addView(card, clp);
    }

    LinearLayout makePill(String name, String sub) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(10), dp(14), dp(10), dp(14));
        p.setClickable(true);
        p.setFocusable(true);

        TextView nm = new TextView(this);
        nm.setText(name);
        nm.setTextSize(14);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        nm.setLetterSpacing(0.15f);
        nm.setGravity(Gravity.CENTER);
        p.addView(nm);

        TextView sb = new TextView(this);
        sb.setText(sub);
        sb.setTextSize(9);
        sb.setLetterSpacing(0.2f);
        sb.setTypeface(Typeface.DEFAULT_BOLD);
        sb.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = dp(3);
        p.addView(sb, slp);

        return p;
    }

    void onPillClick(LinearLayout pill) {
        String pkg = (String) pill.getTag();
        if (gamePkg.equals(pkg)) {
            // 2nd tap → launch
            launchGame(pkg);
        } else {
            gamePkg = pkg;
            gameName = pill == pillMax ? "FFMAX" : "FFTH";
            updatePills();
            setStatus("SELECTED", C_CYAN);
            tvTarget.setText(gameName);
        }
    }

    void updatePills() {
        applyPillStyle(pillMax, pillMaxName, pillMaxSub, gamePkg.equals("com.dts.freefiremax"));
        applyPillStyle(pillTh,  pillThName,  pillThSub,  gamePkg.equals("com.dts.freefireth"));
    }

    void applyPillStyle(LinearLayout p, TextView nm, TextView sb, boolean active) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        if (active) {
            g.setColor(0x2600DCFF);
            g.setStroke(dp(1), C_BORDER_ACT);
            nm.setTextColor(Color.WHITE);
            nm.setShadowLayer(20, 0, 0, 0xCC00DCFF);
            sb.setTextColor(C_CYAN);
        } else {
            g.setColor(0x0AFFFFFF);
            g.setStroke(dp(1), C_BORDER);
            nm.setTextColor(0x99DCDCF0);
            nm.setShadowLayer(0, 0, 0, 0);
            sb.setTextColor(0x59A0C8FF);
        }
        p.setBackground(g);
    }

    void launchGame(String pkg) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) { startActivity(i); setStatus("LAUNCHED", C_GREEN); }
            else setStatus("NOT FOUND", C_RED);
        } catch (Exception e) { setStatus("ERR", C_RED); }
    }

    // ============================================================
    // HUD
    // ============================================================
    void buildHUD() {
        LinearLayout card = makeCard();

        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        tvStatus = buildCell(r1, "STATUS", "STAND BY", C_CYAN, 1f);
        tvFps    = buildCell(r1, "FPS",    "--",       C_GREEN, 1f);
        card.addView(r1, new LinearLayout.LayoutParams(-1, -2));
        card.addView(makeDivider());

        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        tvTarget = buildCell(r2, "TARGET",  "FFMAX", C_TEXT, 1f);
        tvPin    = buildCell(r2, "BATTERY", "-- %",  C_TEXT, 1f);
        card.addView(r2, new LinearLayout.LayoutParams(-1, -2));
        card.addView(makeDivider());

        LinearLayout r3 = new LinearLayout(this);
        r3.setOrientation(LinearLayout.HORIZONTAL);
        tvTemp  = buildCell(r3, "TEMP", "-- °C", C_TEXT, 1f);
        tvFiles = buildCell(r3, "FILE", "--",    C_TEXT, 1f);
        card.addView(r3, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.bottomMargin = dp(12);
        root.addView(card, clp);
    }

    View makeDivider() {
        View d = new View(this);
        d.setBackgroundColor(C_BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(2);
        lp.bottomMargin = dp(2);
        d.setLayoutParams(lp);
        return d;
    }

    TextView buildCell(LinearLayout parent, String key, String val, int color, float weight) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView k = new TextView(this);
        k.setText(key);
        k.setTextSize(9);
        k.setLetterSpacing(0.2f);
        k.setTypeface(Typeface.DEFAULT_BOLD);
        k.setTextColor(C_TEXT_DIM);
        cell.addView(k);

        TextView v = new TextView(this);
        v.setText(val);
        v.setTextSize(14);
        v.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        v.setTextColor(color);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.topMargin = dp(4);
        cell.addView(v, vlp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, weight);
        parent.addView(cell, lp);
        return v;
    }

    // ============================================================
    // PICK BUTTON
    // ============================================================
    void buildPickBtn() {
        FrameLayout wrap = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFF006E, 0xFFA000FF, 0xFF0088FF});
        bg.setCornerRadius(dp(16));
        wrap.setBackground(bg);
        wrap.setClipToOutline(true);
        wrap.setClickable(true);
        wrap.setFocusable(true);
        wrap.setOnClickListener(v -> pickFiles());

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER);
        inner.setPadding(dp(20), dp(18), dp(20), dp(18));
        inner.setLayoutParams(new FrameLayout.LayoutParams(-1, -2));

        TextView icon = new TextView(this);
        icon.setText("📂");
        icon.setTextSize(18);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-2, -2);
        ilp.rightMargin = dp(10);
        inner.addView(icon, ilp);

        TextView lbl = new TextView(this);
        lbl.setText("CHỌN FILE MOD");
        lbl.setTextSize(14);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setLetterSpacing(0.15f);
        lbl.setTextColor(Color.WHITE);
        inner.addView(lbl);

        wrap.addView(inner);

        // Shimmer sweep
        View sh = new View(this);
        GradientDrawable shbg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00FFFFFF, 0x88FFFFFF, 0x00FFFFFF});
        sh.setBackground(shbg);
        FrameLayout.LayoutParams shlp = new FrameLayout.LayoutParams(dp(80), -1);
        wrap.addView(sh, shlp);
        wrap.post(() -> {
            ObjectAnimator a = ObjectAnimator.ofFloat(sh, "translationX",
                    -dp(80), wrap.getWidth() + dp(80));
            a.setDuration(2400);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.start();
        });

        root.addView(wrap, new LinearLayout.LayoutParams(-1, -2));
    }

    // ============================================================
    // HELPERS
    // ============================================================
    LinearLayout makeCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable g = new GradientDrawable();
        g.setColor(C_BG_CARD);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), C_BORDER);
        c.setBackground(g);
        return c;
    }

    void setStatus(String txt, int color) {
        tvStatus.setText(txt);
        tvStatus.setTextColor(color);
    }

    // ============================================================
    // PROGRESS
    // ============================================================
    void setProgress(int pct, String title) {
        progressTitle.setText(title);
        progressPct.setText(pct + "%");
        if (pct >= 100) {
            progressPct.setTextColor(C_GREEN);
            progressPct.setShadowLayer(12, 0, 0, 0x8000FF88);
        } else {
            progressPct.setTextColor(C_CYAN);
            progressPct.setShadowLayer(12, 0, 0, 0x8000DCFF);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
        int trackW = progressTrack.getWidth();
        if (trackW == 0) trackW = getResources().getDisplayMetrics().widthPixels - dp(72);
        int target = (int)(trackW * pct / 100f);
        ValueAnimator va = ValueAnimator.ofInt(lp.width, target);
        va.addUpdateListener(a -> {
            lp.width = (int) a.getAnimatedValue();
            progressFill.setLayoutParams(lp);
        });
        va.setDuration(350);
        va.setInterpolator(new DecelerateInterpolator());
        va.start();
    }

    // ============================================================
    // SCANLINE
    // ============================================================
    void animateScanline() {
        int h = getResources().getDisplayMetrics().heightPixels;
        scanline.setTranslationY(0);
        ObjectAnimator a = ObjectAnimator.ofFloat(scanline, "translationY", 0f, h);
        a.setDuration(4000);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.start();
    }

    // ============================================================
    // FILE PICKER
    // ============================================================
    void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/zip");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, 1001);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 1001 && res == RESULT_OK && data != null && data.getData() != null) {
            pickedUri = data.getData();
            String name = pickedUri.getLastPathSegment();
            if (name != null && name.length() > 14) name = name.substring(0, 12) + "…";
            tvFiles.setText(name != null ? name : "1 file");
            setStatus("READY", C_CYAN);
        }
    }

    // ============================================================
    // ACTIVATE
    // ============================================================
    void activateFiles() {
        if (busy) return;
        if (pickedUri == null) {
            ui.post(() -> setStatus("CHUA CHON FILE", C_WARN));
            return;
        }
        busy = true;
        final String pkg = gamePkg;
        new Thread(() -> {
            File tmpDir = new File("/sdcard/.nyt_tmp");
            File zipFile = new File("/sdcard/.nyt_mod.zip");
            try {
                ui.post(() -> { setProgress(3, "LOADING"); setStatus("LOADING", C_WARN); });

                if (zipFile.exists()) zipFile.delete();
                InputStream is = getContentResolver().openInputStream(pickedUri);
                FileOutputStream os = new FileOutputStream(zipFile);
                byte[] buf = new byte[65536]; int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                is.close(); os.close();

                ui.post(() -> setProgress(15, "LOADING"));

                if (tmpDir.exists()) deleteRecursive(tmpDir);
                tmpDir.mkdirs();

                java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile));
                byte[] bbuf = new byte[65536]; int len;
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    File outFile = new File(tmpDir, entry.getName());
                    if (entry.isDirectory()) outFile.mkdirs();
                    else {
                        outFile.getParentFile().mkdirs();
                        FileOutputStream fos = new FileOutputStream(outFile);
                        while ((len = zis.read(bbuf)) > 0) fos.write(bbuf, 0, len);
                        fos.close();
                    }
                    zis.closeEntry();
                }
                zis.close();

                ui.post(() -> setProgress(50, "LOADING"));

                try { Process p = Runtime.getRuntime().exec(new String[]{"am","force-stop",pkg}); p.waitFor(); } catch (Exception e) {}

                File dst = new File("/storage/emulated/0/Android/data/" + pkg + "/files");
                dst.mkdirs();

                File[] inner = tmpDir.listFiles();
                File srcRoot = tmpDir;
                if (inner != null && inner.length == 1 && inner[0].isDirectory()) srcRoot = inner[0];

                File filesDir = new File(srcRoot, "files");
                if (filesDir.exists() && filesDir.isDirectory()) {
                    copyRecursive(filesDir, dst, dst);
                } else {
                    File gAsset = new File(srcRoot, "gameassetbundles");
                    if (gAsset.exists() && gAsset.isDirectory()) {
                        File target = new File(dst, "contentcache/Optional/android/gameassetbundles");
                        target.mkdirs();
                        copyRecursive(gAsset, target, dst);
                    } else {
                        copyRecursive(srcRoot, dst, dst);
                    }
                }

                deleteRecursive(tmpDir);
                zipFile.delete();

                ui.post(() -> {
                    setProgress(100, "LOADING");
                    setStatus("ACTIVATED", C_GREEN);
                    busy = false;
                });
            } catch (Exception e) {
                final String msg = String.valueOf(e.getMessage());
                ui.post(() -> { setStatus("LOI", C_RED); setProgress(0, "LOADING"); busy = false; });
            }
        }).start();
    }

    // ============================================================
    // RESTORE
    // ============================================================
    void restoreFiles() {
        if (busy) return;
        busy = true;
        final String pkg = gamePkg;
        new Thread(() -> {
            try {
                File backup = new File("/sdcard/.nyt_backup/" + pkg);
                File dst = new File("/storage/emulated/0/Android/data/" + pkg + "/files");
                try { Process p = Runtime.getRuntime().exec(new String[]{"am","force-stop",pkg}); p.waitFor(); } catch (Exception e) {}

                if (!backup.exists()) {
                    ui.post(() -> { setStatus("KHONG CO BACKUP", C_WARN); setProgress(0, "RESTORE"); busy = false; });
                    return;
                }

                ui.post(() -> { setStatus("RESTORING", C_WARN); setProgress(0, "RESTORE"); });

                File[] files = backup.listFiles();
                if (files == null) { busy = false; return; }
                int done = 0;
                for (File bak : files) {
                    File target = new File(dst, bak.getName());
                    copyRecursive(bak, target, dst);
                    done++;
                    final int p = done * 100 / files.length;
                    ui.post(() -> setProgress(p, "RESTORE"));
                }
                ui.post(() -> { setStatus("RESTORED", C_GREEN); setProgress(100, "RESTORE"); busy = false; });
            } catch (Exception e) {
                final String msg = String.valueOf(e.getMessage());
                ui.post(() -> { setStatus("LOI", C_RED); busy = false; });
            }
        }).start();
    }

    // ============================================================
    // IO
    // ============================================================
    void copyRecursive(File src, File dst, File base) throws Exception {
        if (src.isDirectory()) {
            dst.mkdirs();
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) copyRecursive(k, new File(dst, k.getName()), base);
        } else {
            String rel = dst.getAbsolutePath().substring(base.getAbsolutePath().length());
            if (rel.startsWith("/")) rel = rel.substring(1);
            File bak = new File("/sdcard/.nyt_backup/" + gamePkg + "/" + rel);
            if (dst.exists() && !bak.exists()) { bak.getParentFile().mkdirs(); copyFile(dst, bak); }
            dst.getParentFile().mkdirs();
            copyFile(src, dst);
        }
    }

    void copyFile(File src, File dst) throws Exception {
        FileInputStream is = new FileInputStream(src);
        FileOutputStream os = new FileOutputStream(dst);
        byte[] buf = new byte[65536]; int n;
        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        is.close(); os.close();
    }

    void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    // ============================================================
    // LIVE UPDATES
    // ============================================================
    void startUpdates() {
        ui.postDelayed(new Runnable() {
            public void run() {
                try {
                    IntentFilter ifl = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent b = registerReceiver(null, ifl);
                    int lv = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                    int sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int pct = lv * 100 / sc;
                    tvPin.setText(pct + " %");
                    tvPin.setTextColor(pct < 20 ? C_RED : pct < 50 ? C_WARN : C_TEXT);

                    int temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                    float tC = temp / 10f;
                    tvTemp.setText(String.format("%.1f °C", tC));
                    tvTemp.setTextColor(tC > 42 ? C_RED : tC > 40 ? C_WARN : C_TEXT);

                    int fps = 55 + (int)(Math.random() * 6);
                    tvFps.setText(fps + "");
                    tvFps.setTextColor(fps < 30 ? C_RED : fps < 50 ? C_WARN : C_GREEN);
                } catch (Exception e) {}
                ui.postDelayed(this, 1000);
            }
        }, 500);
    }

    // ============================================================
    // BG VIDEO
    // ============================================================
    File extractBgVideo() {
        try {
            File f = new File(getCacheDir(), "bg.mp4");
            if (f.exists() && f.length() > 0) return f;
            InputStream is = getAssets().open("bg.mp4");
            FileOutputStream os = new FileOutputStream(f);
            byte[] buf = new byte[65536]; int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            is.close(); os.close();
            return f;
        } catch (Exception e) { return null; }
    }
}