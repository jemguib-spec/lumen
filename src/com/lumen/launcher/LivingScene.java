package com.lumen.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Couche animée posée sur les paysages Lumen : nuages qui dérivent, halo du soleil qui respire,
 * scintillement du soleil sur l'eau, vaguelettes, brume au pied des crêtes, étoiles qui clignotent
 * et étoiles filantes la nuit. Tout est dessiné à la volée (aucun fichier), 30 images/s au plus,
 * et s'arrête dès que le fond n'est plus visible.
 */
final class LivingScene extends View {
    /** Pause globale (Lumen en arrière-plan, appli ouverte…). */
    static volatile boolean paused;

    private static final long FRAME_MS = 33;
    private final String period, scene;
    private final float sunX, sunY, waterY; // fractions de l'écran ; waterY < 0 : pas d'eau
    private final Random rnd;
    private final long t0 = SystemClock.uptimeMillis();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap cloud;
    private float[] cloudX, cloudY, cloudS, cloudV;
    private float[] starX, starY, starPh, starSz;
    private float[] glX, glY, glPh, glLen;
    private long nextShoot;
    private float shootX, shootY, shootT = -1;
    private int w, h;
    private Shader mist0, mist1;
    private android.graphics.ColorFilter cloudTint;

    /** asset : « scenes/<moment>_<scène>.jpg ». */
    static boolean handles(String asset) { return asset != null && asset.startsWith("scenes/") && asset.endsWith(".jpg"); }

    LivingScene(Context c, String asset) {
        super(c);
        String base = asset.substring(asset.lastIndexOf('/') + 1).replace(".jpg", "");
        int u = base.indexOf('_');
        period = u > 0 ? base.substring(0, u) : "jour";
        scene = u > 0 ? base.substring(u + 1) : "cretes";
        rnd = new Random(base.hashCode());
        // mêmes positions que tools/scenes.py
        switch (period) {
            case "aube": sunX = .68f; sunY = .60f; break;
            case "soir": sunX = .42f; sunY = .62f; break;
            case "nuit": sunX = .74f; sunY = .24f; break;
            default: sunX = .25f; sunY = .18f;
        }
        waterY = scene.equals("horizon") ? .50f : scene.equals("lac") ? .70f : -1f;
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        w = nw; h = nh;
        if (w == 0 || h == 0) return;
        cloud = makeCloud(Math.max(256, w / 4), Math.max(80, h / 10));
        int n = 5;
        cloudX = new float[n]; cloudY = new float[n]; cloudS = new float[n]; cloudV = new float[n];
        float top = waterY > 0 ? waterY - .08f : .42f;
        for (int i = 0; i < n; i++) {
            cloudX[i] = rnd.nextFloat() * 1.4f - .2f;
            cloudY[i] = .06f + rnd.nextFloat() * Math.max(.05f, top - .14f);
            cloudS[i] = .7f + rnd.nextFloat() * .9f;
            cloudV[i] = (.004f + rnd.nextFloat() * .006f) * (rnd.nextBoolean() ? 1 : .7f); // largeur d'écran par minute… ×10
        }
        int ns = period.equals("nuit") ? 140 : period.equals("aube") || period.equals("soir") ? 25 : 0;
        starX = new float[ns]; starY = new float[ns]; starPh = new float[ns]; starSz = new float[ns];
        float sky = waterY > 0 ? waterY - .04f : .55f;
        for (int i = 0; i < ns; i++) {
            starX[i] = rnd.nextFloat();
            starY[i] = (float) Math.pow(rnd.nextFloat(), 1.5) * sky * (period.equals("nuit") ? 1f : .35f);
            starPh[i] = rnd.nextFloat() * 6.28f;
            starSz[i] = .6f + rnd.nextFloat() * rnd.nextFloat() * 1.6f;
        }
        int ng = waterY > 0 ? 70 : 0;
        glX = new float[ng]; glY = new float[ng]; glPh = new float[ng]; glLen = new float[ng];
        for (int i = 0; i < ng; i++) {
            float d = (float) Math.pow(rnd.nextFloat(), 1.4);                 // 0 = horizon, 1 = bas
            glY[i] = waterY + .006f + d * (1 - waterY - .02f);
            float spread = .02f + .16f * d;                                   // la colonne s'élargit vers nous
            glX[i] = sunX + (float) (rnd.nextGaussian() * spread * .6f);
            glPh[i] = rnd.nextFloat() * 6.28f;
            glLen[i] = .006f + .03f * d * (.5f + rnd.nextFloat());
        }
        nextShoot = SystemClock.uptimeMillis() + 8000 + rnd.nextInt(20000);
        // objets de dessin créés une fois (aucune allocation pendant l'animation)
        float hr = h * (period.equals("nuit") ? .12f : .2f);
        glow.setShader(new RadialGradient(sunX * w, sunY * h, hr, 0xFF000000 | (tint() & 0xFFFFFF), 0x00000000 | (tint() & 0xFFFFFF), Shader.TileMode.CLAMP));
        float my = h * .66f;
        int mc = tint() & 0xFFFFFF;
        mist0 = new LinearGradient(0, my - h * .05f, 0, my + h * .05f, new int[]{mc, 0xFF000000 | mc, mc}, new float[]{0, .5f, 1}, Shader.TileMode.CLAMP);
        mist1 = new LinearGradient(0, my - h * .02f, 0, my + h * .08f, new int[]{mc, 0xFF000000 | mc, mc}, new float[]{0, .5f, 1}, Shader.TileMode.CLAMP);
        cloudTint = new android.graphics.PorterDuffColorFilter(tint(), android.graphics.PorterDuff.Mode.MULTIPLY);
    }

    /** Nuage doux : plusieurs halos ovales superposés, blanc avec transparence. */
    private Bitmap makeCloud(int cw, int ch) {
        Bitmap b = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random r = new Random(7);
        for (int i = 0; i < 9; i++) {
            float cx = cw * (.18f + .64f * r.nextFloat()), cy = ch * (.45f + .2f * r.nextFloat());
            float rad = ch * (.28f + .25f * r.nextFloat());
            q.setShader(new RadialGradient(cx, cy, rad, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.save();
            c.scale(1.8f, 1f, cx, cy);
            c.drawCircle(cx, cy, rad, q);
            c.restore();
        }
        return b;
    }

    private int tint() {
        switch (period) {
            case "aube": return 0xFFFFD3C4;
            case "soir": return 0xFFFFB98F;
            case "nuit": return 0xFF8090B8;
            default: return 0xFFFFFFFF;
        }
    }

    @Override protected void onDraw(Canvas c) {
        if (w == 0 || cloud == null) return;
        float t = (SystemClock.uptimeMillis() - t0) / 1000f;

        // 1. halo du soleil (ou de la lune) qui respire très lentement
        float breathe = .5f + .5f * (float) Math.sin(t * .35);
        float hr = h * (period.equals("nuit") ? .12f : .2f);
        glow.setAlpha((int) (28 + 26 * breathe));
        c.drawCircle(sunX * w, sunY * h, hr, glow);

        // 2. étoiles qui clignotent
        p.setShader(null);
        for (int i = 0; i < starX.length; i++) {
            float a = .35f + .65f * (.5f + .5f * (float) Math.sin(t * (1.1f + starSz[i]) + starPh[i]));
            p.setColor(((int) (a * (period.equals("nuit") ? 200 : 90)) << 24) | 0xEAF0FF);
            c.drawCircle(starX[i] * w, starY[i] * h, starSz[i] * h / 1080f * 1.6f, p);
        }

        // 3. étoile filante, la nuit, de temps en temps
        if (period.equals("nuit")) {
            long now = SystemClock.uptimeMillis();
            if (shootT < 0 && now > nextShoot) {
                shootT = 0; shootX = .2f + rnd.nextFloat() * .6f; shootY = .05f + rnd.nextFloat() * .2f;
                nextShoot = now + 25000 + rnd.nextInt(35000);
            }
            if (shootT >= 0) {
                shootT += FRAME_MS / 1000f;
                float k = shootT / .9f;
                if (k > 1) shootT = -1;
                else {
                    float x1 = (shootX + k * .16f) * w, y1 = (shootY + k * .07f) * h;
                    float x0 = x1 - .07f * w, y0 = y1 - .03f * h;
                    p.setShader(new LinearGradient(x0, y0, x1, y1, 0x00FFFFFF, ((int) (230 * (1 - k)) << 24) | 0xFFFFFF, Shader.TileMode.CLAMP));
                    p.setStrokeWidth(h / 540f);
                    c.drawLine(x0, y0, x1, y1, p);
                    p.setShader(null);
                }
            }
        }

        // 4. nuages qui dérivent (et reviennent par l'autre côté)
        int cloudA = period.equals("nuit") ? 70 : period.equals("jour") ? 150 : 120;
        p.setColorFilter(cloudTint);
        p.setAlpha(cloudA);
        for (int i = 0; i < cloudX.length; i++) {
            float span = 1.6f;
            float x = ((cloudX[i] + t * cloudV[i] / 10f) % span + span) % span - .3f;
            float sw = cloud.getWidth() * cloudS[i], sh = cloud.getHeight() * cloudS[i];
            c.save();
            c.translate(x * w, cloudY[i] * h);
            c.scale(cloudS[i], cloudS[i]);
            c.drawBitmap(cloud, -sw / cloudS[i] / 2, 0, p);
            c.restore();
        }
        p.setColorFilter(null);
        p.setAlpha(255);

        // 5. brume qui glisse au pied des crêtes
        if (waterY < 0) {
            float my = h * .66f;
            for (int k = 0; k < 2; k++) {
                p.setShader(k == 0 ? mist0 : mist1);
                p.setAlpha((int) (22 + 12 * Math.sin(t * .2 + k)));
                float y0 = k == 0 ? my - h * .05f : my - h * .02f, y1 = k == 0 ? my + h * .05f : my + h * .08f;
                c.drawRect(0, y0, w, y1, p);
            }
            p.setShader(null);
            p.setAlpha(255);
        }

        // 6. eau : vaguelettes lumineuses qui descendent, et scintillement dans le reflet du soleil
        if (waterY > 0) {
            float wy = waterY * h, depth = h - wy;
            p.setStrokeWidth(Math.max(1, h / 900f));
            for (int k = 0; k < 6; k++) {
                float f = ((t * .025f + k / 6f) % 1f);
                float y = wy + depth * f * f;
                int a = (int) (20 * Math.sin(Math.PI * f));
                p.setColor((a << 24) | (tint() & 0xFFFFFF));
                c.drawLine(0, y, w, y, p);
            }
            int sunA = period.equals("nuit") ? 150 : period.equals("jour") ? 120 : 210;
            for (int i = 0; i < glX.length; i++) {
                float s = (float) Math.sin(t * (2.2f + (i % 5) * .5f) + glPh[i]);
                if (s < .3f) continue;
                int a = (int) (sunA * (s - .3f) / .7f);
                p.setColor((a << 24) | (tint() & 0xFFFFFF));
                p.setStrokeWidth(Math.max(1, h / 700f));
                float x = glX[i] * w, y = glY[i] * h, half = glLen[i] * w / 2;
                c.drawLine(x - half, y, x + half, y, p);
            }
        }

        if (!paused && isShown()) postInvalidateDelayed(FRAME_MS);
    }

    /** Reprend l'animation après une pause. */
    void resume() { if (isShown()) postInvalidateDelayed(FRAME_MS); }
}
