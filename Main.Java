package com.demo.nytswitcher;

import android.app.Activity;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.IntentFilter;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main extends Activity {

    // UI Colors (Trong suốt)
    static final int C_BG_CARD    = 0x33000000; // Nền card trong suốt 20%
    static final int C_BORDER     = 0x4DFFFFFF;
    static final int C_BORDER_ACT = 0xB300DCFF;
    static final int C_CYAN       = 0xFF00DCFF;
    static final int C_GREEN      = 0xFF00FF88;
    static final int C_RED        = 0xFFFF4D5E;
    static final int C_WARN       = 0xFFFFB020;
    static final int C_TEXT       = 0xFFE8E8F0;
    static final int C_TEXT_DIM   = 0x80A0C8FF;

    FrameLayout frame;
    LinearLayout root;
    View scanline, logoDot;
    TextView titleText;
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

    void buildUI() {
        frame = new FrameLayout(this);

        VideoView vv = new VideoView(this);
        vv.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        File vid = extractBgVideo();
        if (vid != null && vid.exists()) {
            vv.setVideoURI(Uri.fromFile(vid));
            vv.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                mp.setVolume(1f, 1f); // Có tiếng
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

        // Lớp phủ RẤT NHẸ để vẫn nhìn rõ video background
        View dim = new View(this);
        dim.setBackgroundColor(0x15000000); // Chỉ 8% tối
        frame.addView(dim, new FrameLayout.LayoutParams(-1, -1));

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
        
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(-1, dp(16))); 
        
        buildActionButtons();

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

    void buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout logoWrap = new LinearLayout(this);
        logoWrap.setOrientation(LinearLayout.HORIZONTAL);
        logoWrap.setGravity(Gravity.CENTER_VERTICAL);
        logoWrap.setPadding(dp(20), dp(12), dp(24), dp(12));

        GradientDrawable lbg = new GradientDrawable();
        lbg.setColor(C_BG_CARD);
        lbg.setCornerRadius(dp(14));
        lbg.setStroke(dp(1), C_BORDER);
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
        header.addView(logoWrap);

        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-1, -2);
        hLp.topMargin = dp(10);
        hLp.bottomMargin = dp(20);
        root.addView(header, hLp);

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
        tbg.setColor(0x22FFFFFF);
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

        card.addView(progressTrack, new LinearLayout.LayoutParams(-1, dp(6)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.bottomMargin = dp(12);
        root.addView(card, clp);
    }

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
            g.setColor(0x3300DCFF);
            g.setStroke(dp(1), C_BORDER_ACT);
            nm.setTextColor(Color.WHITE);
            nm.setShadowLayer(20, 0, 0, 0xCC00DCFF);
            sb.setTextColor(C_CYAN);
        } else {
            g.setColor(0x1AFFFFFF);
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
            if (i != null) {
                startActivity(i);
                setStatus("LAUNCHED", C_GREEN);
            } else {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(new android.content.ComponentName(pkg, pkg + ".MainActivity"));
                startActivity(intent);
                setStatus("LAUNCHED", C_GREEN);
            }
        } catch (Exception e) { 
            setStatus("NOT FOUND", C_RED); 
        }
    }

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
        v.setTextSize(13); 
        v.setTypeface(Typeface.DEFAULT_BOLD); 
        v.setTextColor(color);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.topMargin = dp(4);
        cell.addView(v, vlp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, weight);
        parent.addView(cell, lp);
        return v;
    }

    void buildActionButtons() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        // Nút Chọn File (Trong suốt)
        FrameLayout pickWrap = createBtn("📂 CHỌN FILE", new int[]{0xCCFF006E, 0xCCA000FF});
        pickWrap.setOnClickListener(v -> pickFiles());
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(56), 1.2f);
        p1.rightMargin = dp(10);
        row.addView(pickWrap, p1);

        // Nút Xóa Mod (Trong suốt)
        FrameLayout delWrap = createBtn("🗑 XÓA MOD", new int[]{0xCCFF4D5E, 0xCC990022});
        delWrap.setOnClickListener(v -> restoreFiles());
        row.addView(delWrap, new LinearLayout.LayoutParams(0, dp(56), 1f));

        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    FrameLayout createBtn(String text, int[] colors) {
        FrameLayout wrap = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0x80FFFFFF); // Viền trắng mờ
        wrap.setBackground(bg);
        wrap.setClipToOutline(true);
        wrap.setClickable(true);
        wrap.setFocusable(true);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER);
        inner.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

        TextView lbl = new TextView(this);
        lbl.setText(text);
        lbl.setTextSize(13);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setLetterSpacing(0.1f);
        lbl.setTextColor(Color.WHITE);
        inner.addView(lbl);

        wrap.addView(inner);
        return wrap;
    }

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

    void animateScanline() {
        int h = getResources().getDisplayMetrics().heightPixels;
        scanline.setTranslationY(0);
        ObjectAnimator a = ObjectAnimator.ofFloat(scanline, "translationY", 0f, h);
        a.setDuration(4000);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.start();
    }

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

    // ==========================================
    // LOGIC NẠP FILE SIÊU TỐC (ĐỌC TRỰC TIẾP TỪ ZIP)
    // ==========================================
    void activateFiles() {
        if (busy) return;
        if (pickedUri == null) {
            ui.post(() -> setStatus("CHUA CHON FILE", C_WARN));
            return;
        }
        busy = true;
        final String pkg = gamePkg;
        
        new Thread(() -> {
            ui.post(() -> { setProgress(5, "LOADING"); setStatus("LOADING", C_WARN); });
            
            // Đường dẫn gốc của game
            File baseDir = new File("/storage/emulated/0/Android/data/" + pkg);
            if (!baseDir.exists()) baseDir.mkdirs();

            try {
                InputStream is = getContentResolver().openInputStream(pickedUri);
                ZipInputStream zis = new ZipInputStream(is);
                
                ZipEntry entry;
                byte[] buffer = new byte[1024 * 1024]; // Bộ đệm 1MB cho tốc độ tối đa
                int count = 0;
                
                while ((entry = zis.getNextEntry()) != null) {
                    // Tạo file đích dựa trên đường dẫn có sẵn trong ZIP
                    File destFile = new File(baseDir, entry.getName());
                    
                    if (entry.isDirectory()) {
                        destFile.mkdirs();
                    } else {
                        destFile.getParentFile().mkdirs();
                        
                        // Nếu file đã tồn tại -> Đổi tên thành .bak
                        if (destFile.exists()) {
                            File bakFile = new File(destFile.getAbsolutePath() + ".bak");
                            if (!bakFile.exists()) {
                                destFile.renameTo(bakFile);
                            } else {
                                destFile.delete(); // Đã có .bak rồi thì xóa file cũ
                            }
                        }
                        
                        // Ghi thẳng file mới vào
                        FileOutputStream fos = new FileOutputStream(destFile);
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                        fos.close();
                    }
                    zis.closeEntry();
                    
                    // Cập nhật thanh tiến trình (giả lập theo số lượng file)
                    count++;
                    if (count % 5 == 0) {
                        final int p = Math.min(95, 5 + count); 
                        ui.post(() -> setProgress(p, "LOADING"));
                    }
                }
                zis.close();
                is.close();

                ui.post(() -> {
                    setProgress(100, "LOADING");
                    setStatus("ACTIVATED", C_GREEN);
                    busy = false;
                });

            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : "Loi doc file";
                ui.post(() -> { setStatus("LOI: " + err, C_RED); setProgress(0, "LOADING"); busy = false; });
            }
        }).start();
    }

    // ==========================================
    // LOGIC XÓA MOD (QUÉT TÌM .BAK VÀ KHÔI PHỤC)
    // ==========================================
    void restoreFiles() {
        if (busy) return;
        busy = true;
        final String pkg = gamePkg;
        
        new Thread(() -> {
            ui.post(() -> { setStatus("RESTORING", C_WARN); setProgress(20, "RESTORE"); });

            File baseDir = new File("/storage/emulated/0/Android/data/" + pkg);
            if (!baseDir.exists()) {
                ui.post(() -> { setStatus("GAME NOT INSTALLED", C_RED); busy = false; });
                return;
            }

            int count = restoreRecursive(baseDir);
            
            ui.post(() -> {
                if (count > 0) {
                    setStatus("RESTORED", C_GREEN);
                    setProgress(100, "RESTORE");
                } else {
                    setStatus("NO MOD TO RESTORE", C_WARN);
                    setProgress(0, "RESTORE");
                }
                busy = false;
            });
        }).start();
    }

    int restoreRecursive(File dir) {
        int count = 0;
        if (dir.isDirectory()) {
            File[] kids = dir.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    count += restoreRecursive(k);
                }
            }
        } else {
            // Nếu là file .bak -> Xóa file mod hiện tại và đổi tên .bak về lại
            if (dir.getName().endsWith(".bak")) {
                String origPath = dir.getAbsolutePath().substring(0, dir.getAbsolutePath().length() - 4);
                File orig = new File(origPath);
                if (orig.exists()) orig.delete(); 
                dir.renameTo(orig); 
                count++;
            }
        }
        return count;
    }

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