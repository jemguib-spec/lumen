package com.lumen.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * La météo réelle dans le paysage : pluie, bruine, neige, brume, ciel couvert, orage.
 * Dessiné par-dessus le fond (paysages Lumen uniquement), très léger pour le processeur.
 */
final class WeatherFx extends View {
    static final int NONE = 0, OVERCAST = 1, FOG = 2, DRIZZLE = 3, RAIN = 4, SNOW = 5, STORM = 6;

    /** Code météo Open-Meteo (WMO) → effet. */
    static int kindFor(int code) {
        if (code < 0) return NONE;
        if (code == 3) return OVERCAST;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 57) return DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95) return STORM;
        return NONE;
    }

    private static final int MAX = 300;
    private final float[] px = new float[MAX], py = new float[MAX], ps = new float[MAX], pz = new float[MAX];
    private final Random rnd = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint veil = new Paint();
    private int kind = NONE, count;
    private long last;
    private boolean running = true;
    private float flash, nextFlash = 8f, t;
    private final float unit;

    WeatherFx(Context c, float unit) {
        super(c);
        this.unit = unit;
        setWillNotDraw(false);
    }

    void setKind(int k) {
        if (k == kind) return;
        kind = k;
        count = k == RAIN || k == STORM ? 260 : k == DRIZZLE ? 120 : k == SNOW ? 170 : 0;
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        for (int i = 0; i < count; i++) seed(i, w, h, true);
        animate().alpha(k == NONE ? 0f : 1f).setDuration(2500).start();
        last = SystemClock.uptimeMillis();
        invalidate();
    }

    int kind() { return kind; }

    void setRunning(boolean r) {
        running = r;
        last = SystemClock.uptimeMillis();
        if (r) postInvalidateOnAnimation();
    }

    private void seed(int i, int w, int h, boolean anywhere) {
        px[i] = rnd.nextFloat() * w * 1.2f - w * 0.1f;
        py[i] = anywhere ? rnd.nextFloat() * h : -rnd.nextFloat() * h * 0.2f;
        pz[i] = 0.4f + rnd.nextFloat() * 0.6f; // profondeur : taille, vitesse, opacité
        ps[i] = rnd.nextFloat() * 6.28f;
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || kind == NONE) return;
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        t += dt;

        if (kind == OVERCAST || kind == RAIN || kind == STORM || kind == DRIZZLE) {
            veil.setColor(kind == OVERCAST ? 0x2E0A0E18 : 0x3A0A0E18);
            c.drawRect(0, 0, w, h, veil);
        }
        if (kind == FOG) {
            veil.setColor(0x26C8CCD4);
            c.drawRect(0, 0, w, h, veil);
            for (int k = 0; k < 2; k++) {
                float y = h * (0.52f + k * 0.16f) + (float) Math.sin(t * 0.05f + k) * h * 0.02f;
                float bh = h * (0.18f + k * 0.06f);
                veil.setShader(new LinearGradient(0, y - bh / 2, 0, y + bh / 2,
                        new int[]{0x00D0D4DC, 0x55D0D4DC, 0x00D0D4DC}, null, Shader.TileMode.CLAMP));
                c.drawRect(0, y - bh / 2, w, y + bh / 2, veil);
                veil.setShader(null);
            }
        }

        if (kind == RAIN || kind == STORM || kind == DRIZZLE) {
            boolean light = kind == DRIZZLE;
            float speed = (light ? 700 : 1500) * unit, len = (light ? 14 : 34) * unit;
            float slant = 0.18f;
            p.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < count; i++) {
                float z = pz[i];
                py[i] += speed * z * dt;
                px[i] += speed * z * dt * slant;
                if (py[i] > h) seed(i, w, h, false);
                p.setStrokeWidth(Math.max(1f, 1.6f * unit * z));
                p.setColor(((int) ((light ? 50 : 70) * z) << 24) | 0xDCE4F0);
                c.drawLine(px[i], py[i], px[i] - len * z * slant, py[i] - len * z, p);
            }
        } else if (kind == SNOW) {
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < count; i++) {
                float z = pz[i];
                py[i] += 60 * unit * z * dt;
                px[i] += (float) Math.sin(t * 0.8f + ps[i]) * 18 * unit * z * dt;
                if (py[i] > h) seed(i, w, h, false);
                p.setColor(((int) (200 * z) << 24) | 0xFFFFFF);
                c.drawCircle(px[i], py[i], (1.2f + 3f * z) * unit, p);
            }
        }

        if (kind == STORM) {
            nextFlash -= dt;
            if (nextFlash <= 0) { flash = 1f; nextFlash = 9 + rnd.nextFloat() * 18; }
            if (flash > 0) {
                veil.setColor(((int) (70 * flash) << 24) | 0xE8ECFF);
                c.drawRect(0, 0, w, h, veil);
                flash = Math.max(0, flash - dt * 3.5f);
            }
        }
        if (running) postInvalidateOnAnimation();
    }
}
