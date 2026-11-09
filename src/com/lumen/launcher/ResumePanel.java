package com.lumen.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Panneau « Reprendre » : dernières applis utilisées, qui glisse depuis la gauche. */
final class ResumePanel {
    private ResumePanel() { }


    static boolean usageAllowed(final MainActivity a) {
        try {
            android.app.AppOpsManager ops = (android.app.AppOpsManager) a.getSystemService(Context.APP_OPS_SERVICE);
            int mode = ops.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), a.getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Applis récentes : lancements depuis Lumen + statistiques d'usage du système si autorisées. */
    static List<Object[]> recentApps(final MainActivity a) {
        java.util.Map<String, Long> last = new java.util.HashMap<>();
        for (String[] r : a.prefs.recents()) {
            AppRepo.App app = a.repo.get(r[0]);
            if (app != null) last.put(app.pkg, Math.max(Long.parseLong(r[1]), last.containsKey(app.pkg) ? last.get(app.pkg) : 0L));
        }
        if (usageAllowed(a)) {
            try {
                android.app.usage.UsageStatsManager um = (android.app.usage.UsageStatsManager) a.getSystemService("usagestats");
                long now = System.currentTimeMillis();
                for (android.app.usage.UsageStats st : um.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 14L * 86400000L, now)) {
                    String pkg = st.getPackageName();
                    if (st.getLastTimeUsed() <= 0 || st.getTotalTimeInForeground() < 5000) continue;
                    if (pkg.equals(a.getPackageName()) || pkg.contains("launcher") || pkg.contains("setupwraith")
                            || pkg.contains("tv.settings") || pkg.equals("com.android.systemui")) continue;
                    Long prev = last.get(pkg);
                    if (prev == null || st.getLastTimeUsed() > prev) last.put(pkg, st.getLastTimeUsed());
                }
            } catch (Throwable ignored) { }
        }
        Set<String> hidden = a.prefs.hiddenApps();
        List<Object[]> out = new ArrayList<>();
        for (java.util.Map.Entry<String, Long> e : last.entrySet()) {
            AppRepo.App app = a.repo.findByPackage(e.getKey());
            if (app == null || hidden.contains(app.key)) continue;
            out.add(new Object[]{app, e.getValue()});
        }
        java.util.Collections.sort(out, new java.util.Comparator<Object[]>() {
            @Override public int compare(Object[] x, Object[] y) { return Long.compare((Long) y[1], (Long) x[1]); }
        });
        return out.size() > 12 ? out.subList(0, 12) : out;
    }

    static String ago(long t) {
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 1) return "à l'instant";
        if (m < 60) return "il y a " + m + " min";
        long hours = m / 60;
        if (hours < 24) return "il y a " + hours + " h";
        long d = hours / 24;
        return d == 1 ? "hier" : "il y a " + d + " jours";
    }

    /** Panneau latéral qui glisse depuis la gauche avec les dernières applis utilisées. */
    static void openResume(final MainActivity a) {
        List<Object[]> items = recentApps(a);
        a.resumeOpen = true;
        FrameLayout o = a.openOverlay(new Runnable() { @Override public void run() { a.resumeOpen = false; } });
        final LinearLayout card = a.glassCard(48);
        card.setPadding(a.u(24), a.u(52), a.u(24), a.u(32));
        TextView title = a.text(48, "sans-serif-light", 0xF5FFFFFF);
        title.setText("Reprendre");
        title.setPadding(a.u(28), 0, 0, a.u(28));
        card.addView(title);
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(60));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        View first = null;
        for (Object[] it : items) {
            final AppRepo.App app = (AppRepo.App) it[0];
            View r = resumeRow(a, app, ago((Long) it[1]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.u(112));
            lp.bottomMargin = a.u(8);
            col.addView(r, lp);
            if (first == null) first = r;
        }
        if (first == null) {
            TextView none = a.text(26, "sans-serif", 0x99FFFFFF);
            none.setText("Rien pour l'instant");
            none.setPadding(a.u(28), a.u(10), 0, 0);
            col.addView(none);
            LinearLayout back = a.listRow("Retour", null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.closeOverlay(true); }
            });
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.u(88));
            bl.topMargin = a.u(24);
            col.addView(back, bl);
            first = back;
        }
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(a.u(720), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        cl.leftMargin = cl.topMargin = cl.bottomMargin = a.u(48);
        o.addView(card, cl);
        card.setTranslationX(-a.u(780));
        card.animate().translationX(0).setDuration(360).setInterpolator(new DecelerateInterpolator(2.2f)).start();
        first.requestFocus();
    }

    static View resumeRow(final MainActivity a, final AppRepo.App app, String when) {
        final LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setSoundEffectsEnabled(false);
        r.setPadding(a.u(24), 0, a.u(24), 0);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.u(24));
        bg.setColor(0x00FFFFFF);
        r.setBackground(bg);
        ImageView ic = new ImageView(a);
        ic.setImageDrawable(app.icon(a.getPackageManager()));
        r.addView(ic, new LinearLayout.LayoutParams(a.u(72), a.u(72)));
        LinearLayout txt = new LinearLayout(a);
        txt.setOrientation(LinearLayout.VERTICAL);
        final TextView name = a.text(30, "sans-serif-medium", 0xF2FFFFFF);
        name.setText(app.label);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        final TextView sub = a.text(22, "sans-serif", 0x8CFFFFFF);
        sub.setText(when);
        txt.addView(name);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = a.u(6);
        txt.addView(sub, sl);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = a.u(24);
        r.addView(txt, tl);
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) Sounds.get(a).tick();
                bg.setColor(f ? 0xFFF5F5F7 : 0x00FFFFFF);
                name.setTextColor(f ? 0xFF111216 : 0xF2FFFFFF);
                sub.setTextColor(f ? 0x99111216 : 0x8CFFFFFF);
                r.animate().scaleX(f ? 1.03f : 1f).scaleY(f ? 1.03f : 1f).setDuration(180).start();
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Sounds.get(a).tock();
                a.closeOverlay(false);
                a.launch(app);
            }
        });
        return r;
    }
}
