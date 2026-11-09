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

/** Liste des entrées de la TV (HDMI, chaînes) et bascule directe. */
final class InputsPanel {
    private InputsPanel() { }


    static void openInputs(final MainActivity a) {
        List<android.media.tv.TvInputInfo> list = new ArrayList<>();
        try {
            android.media.tv.TvInputManager tim = (android.media.tv.TvInputManager) a.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) for (android.media.tv.TvInputInfo i : tim.getTvInputList()) {
                try { if ((Boolean) i.getClass().getMethod("isHidden", Context.class).invoke(i, a)) continue; } catch (Throwable ignored) { }
                list.add(i);
            }
        } catch (Throwable ignored) { }
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.glassCard(44);
        card.setPadding(a.u(24), a.u(40), a.u(24), a.u(24));
        TextView title = a.text(40, "sans-serif-light", 0xF5FFFFFF);
        title.setText("Entrées");
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, a.u(30));
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout first = null;
        for (final android.media.tv.TvInputInfo info : list) {
            CharSequence lbl = null;
            try { lbl = (CharSequence) info.getClass().getMethod("loadCustomLabel", Context.class).invoke(info, a); } catch (Throwable ignored) { }
            if (lbl == null || lbl.length() == 0) lbl = info.loadLabel(a);
            String type = info.getType() == android.media.tv.TvInputInfo.TYPE_HDMI ? "HDMI"
                    : info.getType() == android.media.tv.TvInputInfo.TYPE_TUNER ? "Chaînes TV"
                    : info.getType() == android.media.tv.TvInputInfo.TYPE_COMPOSITE ? "AV" : "";
            LinearLayout r = a.listRow(String.valueOf(lbl), new MainActivity.Value() {
                final String t = type;
                @Override public String get() { return t; }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) { a.closeOverlay(false); switchInput(a, info); }
            });
            card.addView(r, a.rowLp());
            if (first == null) first = r;
        }
        LinearLayout settingsRow = a.listRow(list.isEmpty() ? "Aucune entrée trouvée · ouvrir les paramètres" : "Paramètres des entrées", null,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        a.closeOverlay(false);
                        try {
                            Intent i = new Intent("android.settings.INPUT_SETTINGS");
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            a.startActivity(i);
                        } catch (Exception e) {
                            try {
                                Intent i = new Intent(Settings.ACTION_SETTINGS);
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                a.startActivity(i);
                            } catch (Exception ignored) { }
                        }
                    }
                });
        card.addView(settingsRow, a.rowLp());
        if (first == null) first = settingsRow;
        o.addView(card, new FrameLayout.LayoutParams(a.u(680), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        first.requestFocus();
    }

    /** Bascule sur une entrée : l'appli TV du téléviseur s'ouvre directement dessus. */
    static void switchInput(final MainActivity a, android.media.tv.TvInputInfo info) {
        Uri uri = info.isPassthroughInput()
                ? android.media.tv.TvContract.buildChannelUriForPassthroughInput(info.getId())
                : android.media.tv.TvContract.buildChannelsUriForInput(info.getId());
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.Channels.CONTENT_URI);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(i);
            } catch (Exception e2) {
                a.showNotice("Impossible d'ouvrir cette entrée");
            }
        }
    }

}
