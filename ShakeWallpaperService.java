package com.shake.wallpaper;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.service.wallpaper.WallpaperService;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Живые обои. Каждая область — отдельное «желе» со своей пружинной физикой
 * и своими параметрами (чувствительность, амплитуда, длительность, скорость, касание).
 */
public class ShakeWallpaperService extends WallpaperService {

    static final int MESH_W = 28;
    static final int MESH_H = 48;

    @Override
    public Engine onCreateEngine() {
        return new ShakeEngine();
    }

    /** Состояние пружин одной области: смещение x,y и «объём» s. */
    private static final class St {
        float x, y, s, vx, vy, vs;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float dead(float v, float thr) {
        float a = Math.abs(v);
        return a < thr ? 0f : Math.signum(v) * (a - thr);
    }

    private static float weightAt(float acx, float acy, float arx, float ary, float x, float y) {
        float ex = (x - acx) / arx, ey = (y - acy) / ary;
        float d = (float) Math.sqrt(ex * ex + ey * ey);
        if (d >= 1f) return 0f;
        float t = 1f - d;
        return t * t * (3f - 2f * t);
    }

    private final class ShakeEngine extends Engine implements SensorEventListener {

        private SharedPreferences sp;
        private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
        private SurfaceHolder holder;
        private Choreographer choreographer;
        private SensorManager sensorManager;

        private int W, H;
        private boolean visible;
        private boolean animating;
        private long lastNanos;

        private Bitmap source;
        private Bitmap viewBmp;
        private String loadedKey = "";
        private String viewKey = "";

        private float zoom = 1f, cropX = 0.5f, cropY = 0.5f;
        private int mapL, mapT;
        private float mapSx = 1f, mapSy = 1f;

        private List<Prefs.Area> areas = new ArrayList<>();
        private St[] st = new St[0];
        private float[] aCx = new float[0], aCy = new float[0], aRx = new float[0], aRy = new float[0];
        private float[][] weights;
        private float[] baseVerts, verts;

        private float gx, gy, gz;
        private boolean gInit;
        private float linX, linY, linZ;
        private float lastTx, lastTy;

        private final Choreographer.FrameCallback frameCb = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                frame(frameTimeNanos);
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            holder = surfaceHolder;
            choreographer = Choreographer.getInstance();
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            sp = Prefs.sp(ShakeWallpaperService.this);
            prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences p, String key) {
                    loadSettings();
                    draw();
                    kick();
                }
            };
            sp.registerOnSharedPreferenceChangeListener(prefListener);
            loadSettings();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (sp != null && prefListener != null) sp.unregisterOnSharedPreferenceChangeListener(prefListener);
            unregisterSensor();
            choreographer.removeFrameCallback(frameCb);
            if (viewBmp != null) viewBmp.recycle();
            if (source != null) source.recycle();
            viewBmp = null;
            source = null;
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder h) {
            super.onSurfaceCreated(h);
            holder = h;
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder h, int format, int width, int height) {
            super.onSurfaceChanged(h, format, width, height);
            holder = h;
            W = width;
            H = height;
            int vc = (MESH_W + 1) * (MESH_H + 1);
            baseVerts = new float[vc * 2];
            verts = new float[vc * 2];
            for (int j = 0; j <= MESH_H; j++) {
                for (int i = 0; i <= MESH_W; i++) {
                    int idx = (j * (MESH_W + 1) + i) * 2;
                    baseVerts[idx] = W * (float) i / MESH_W;
                    baseVerts[idx + 1] = H * (float) j / MESH_H;
                }
            }
            viewKey = "";
            loadSettings();
            draw();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder h) {
            super.onSurfaceDestroyed(h);
            visible = false;
            animating = false;
            choreographer.removeFrameCallback(frameCb);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            if (v) {
                registerSensor();
                draw();
            } else {
                unregisterSensor();
                animating = false;
                choreographer.removeFrameCallback(frameCb);
            }
        }

        // ---------------------------------------------------------------- сенсор

        private void registerSensor() {
            if (sensorManager == null) return;
            Sensor acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (acc != null) sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME);
        }

        private void unregisterSensor() {
            if (sensorManager != null) sensorManager.unregisterListener(this);
            gInit = false;
            linX = linY = linZ = 0f;
        }

        @Override
        public void onSensorChanged(SensorEvent e) {
            float[] v = e.values;
            if (!gInit) {
                gx = v[0];
                gy = v[1];
                gz = v[2];
                gInit = true;
            }
            gx = 0.85f * gx + 0.15f * v[0];
            gy = 0.85f * gy + 0.15f * v[1];
            gz = 0.85f * gz + 0.15f * v[2];
            linX = v[0] - gx;
            linY = v[1] - gy;
            linZ = v[2] - gz;
            if (!areas.isEmpty() && (Math.abs(linX) > 0.4f || Math.abs(linY) > 0.4f || Math.abs(linZ) > 0.4f)) {
                kick();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        // ---------------------------------------------------------------- касание

        @Override
        public void onTouchEvent(MotionEvent e) {
            super.onTouchEvent(e);
            if (areas.isEmpty() || W <= 0 || weights == null) return;
            float x = e.getX(), y = e.getY();
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                lastTx = x;
                lastTy = y;
                for (int i = 0; i < areas.size() && i < st.length && i < aCx.length; i++) {
                    float w = weightAt(aCx[i], aCy[i], aRx[i], aRy[i], x, y);
                    if (w <= 0f) continue;
                    float tn = areas.get(i).touch / 100f;
                    float dx = aCx[i] - x, dy = aCy[i] - y;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len < 1f) {
                        dx = 0f;
                        dy = 1f;
                        len = 1f;
                    }
                    float k = lerp(0f, 12f, tn) * (0.3f + 0.7f * w);
                    st[i].vx += dx / len * k;
                    st[i].vy += dy / len * k;
                    st[i].vs -= k * 0.3f;
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                float m = Math.min(W, H);
                float mdx = (x - lastTx) / m, mdy = (y - lastTy) / m;
                lastTx = x;
                lastTy = y;
                for (int i = 0; i < areas.size() && i < st.length && i < aCx.length; i++) {
                    float w = weightAt(aCx[i], aCy[i], aRx[i], aRy[i], x, y);
                    if (w <= 0f) continue;
                    float tn = areas.get(i).touch / 100f;
                    float k = lerp(0f, 60f, tn) * w;
                    st[i].vx += mdx * k;
                    st[i].vy += mdy * k;
                }
            }
            kick();
        }

        // ---------------------------------------------------------------- настройки / картинка

        private void loadSettings() {
            areas = Prefs.loadAreas(sp);
            zoom = Prefs.getZoom(sp);
            cropX = sp.getFloat(Prefs.K_CROP_X, 0.5f);
            cropY = sp.getFloat(Prefs.K_CROP_Y, 0.5f);

            String imgKey = Prefs.imageKey(sp);
            if (source == null || !imgKey.equals(loadedKey)) {
                loadSource();
                loadedKey = imgKey;
            }

            St[] ns = new St[areas.size()];
            for (int i = 0; i < ns.length; i++) {
                ns[i] = (i < st.length && st[i] != null) ? st[i] : new St();
            }
            st = ns;

            String vk = W + "x" + H + ":" + zoom + ":" + cropX + ":" + cropY + ":" + loadedKey;
            if (!vk.equals(viewKey) || viewBmp == null) {
                rebuildView();
                viewKey = vk;
            }
            rebuildWeights();
        }

        private void loadSource() {
            Bitmap b = null;
            if (sp.getInt(Prefs.K_IMAGE_MODE, 0) == 1) {
                File f = new File(getFilesDir(), Prefs.CUSTOM_FILE);
                if (f.exists()) b = Prefs.decodeFile(f.getPath(), 2048);
            }
            if (b == null) b = Prefs.decodeBuiltin(getResources(), 2048);
            if (source != null && source != b) source.recycle();
            source = b;
            if (viewBmp != null) {
                viewBmp.recycle();
                viewBmp = null;
            }
        }

        private void rebuildView() {
            if (source == null || W <= 0 || H <= 0) return;
            int bw = source.getWidth(), bh = source.getHeight();
            float[] f = Prefs.visibleFrac(bw, bh, (float) W / H, zoom);
            int cw = Math.max(1, Math.min(bw, Math.round(f[0] * bw)));
            int ch = Math.max(1, Math.min(bh, Math.round(f[1] * bh)));
            int l = Math.round(Prefs.clamp(cropX * bw - cw / 2f, 0f, bw - cw));
            int t = Math.round(Prefs.clamp(cropY * bh - ch / 2f, 0f, bh - ch));
            l = Math.max(0, Math.min(l, bw - cw));
            t = Math.max(0, Math.min(t, bh - ch));

            Bitmap crop = Bitmap.createBitmap(source, l, t, cw, ch);
            Bitmap scaled = Bitmap.createScaledBitmap(crop, W, H, true);
            if (crop != source && crop != scaled) crop.recycle();
            if (scaled == source) scaled = source.copy(Bitmap.Config.ARGB_8888, false);

            if (viewBmp != null && viewBmp != scaled) viewBmp.recycle();
            viewBmp = scaled;
            mapL = l;
            mapT = t;
            mapSx = (float) W / cw;
            mapSy = (float) H / ch;
        }

        private void rebuildWeights() {
            if (source == null || baseVerts == null || W <= 0) {
                weights = null;
                return;
            }
            int bw = source.getWidth(), bh = source.getHeight();
            int n = areas.size();
            int vc = baseVerts.length / 2;
            weights = new float[n][vc];
            aCx = new float[n];
            aCy = new float[n];
            aRx = new float[n];
            aRy = new float[n];
            for (int i = 0; i < n; i++) {
                Prefs.Area a = areas.get(i);
                aCx[i] = (a.cx * bw - mapL) * mapSx;
                aCy[i] = (a.cy * bh - mapT) * mapSy;
                aRx[i] = Math.max(1f, a.rx * bw * mapSx);
                aRy[i] = Math.max(1f, a.ry * bh * mapSy);
                for (int v = 0; v < vc; v++) {
                    weights[i][v] = weightAt(aCx[i], aCy[i], aRx[i], aRy[i], baseVerts[v * 2], baseVerts[v * 2 + 1]);
                }
            }
        }

        // ---------------------------------------------------------------- анимация

        private void kick() {
            if (!animating && visible) {
                animating = true;
                lastNanos = 0;
                choreographer.postFrameCallback(frameCb);
            }
        }

        private void frame(long nanos) {
            if (!visible) {
                animating = false;
                return;
            }
            float dt = lastNanos == 0 ? 1f / 60f : Math.min((nanos - lastNanos) / 1e9f, 0.033f);
            lastNanos = nanos;
            boolean active = step(dt);
            draw();
            if (active) {
                choreographer.postFrameCallback(frameCb);
            } else {
                animating = false;
                lastNanos = 0;
            }
        }

        /** Один шаг физики. Возвращает true, пока хоть одна область ещё движется. */
        private boolean step(float dt) {
            boolean active = false;
            final int sub = 2;
            final float h = dt / sub;
            for (int i = 0; i < areas.size() && i < st.length; i++) {
                Prefs.Area a = areas.get(i);
                St s = st[i];

                float sensN = a.sens / 100f;
                float thr = lerp(2.5f, 0.4f, sensN);
                float gain = lerp(1.5f, 8f, sensN);
                float fx = -dead(linX, thr) * gain;
                float fy = dead(linY, thr) * gain;
                float fz = dead(linZ, thr) * gain * 0.5f;

                float omega = lerp(5f, 24f, a.speed / 100f);
                float zeta = lerp(0.45f, 0.02f, a.damp / 100f);
                float k = omega * omega;
                float c = 2f * zeta * omega;
                float omegaS = omega * 1.4f;
                float ks = omegaS * omegaS;
                float cs = 2f * zeta * omegaS;

                for (int j = 0; j < sub; j++) {
                    s.vx += (fx - k * s.x - c * s.vx) * h;
                    s.x += s.vx * h;
                    s.vy += (fy - k * s.y - c * s.vy) * h;
                    s.y += s.vy * h;
                    s.vs += (fz - ks * s.s - cs * s.vs) * h;
                    s.s += s.vs * h;
                }
                s.x = Prefs.clamp(s.x, -1.6f, 1.6f);
                s.y = Prefs.clamp(s.y, -1.6f, 1.6f);
                s.s = Prefs.clamp(s.s, -1.6f, 1.6f);

                if (Math.abs(s.x) + Math.abs(s.y) + Math.abs(s.s) > 0.003f
                        || Math.abs(s.vx) + Math.abs(s.vy) + Math.abs(s.vs) > 0.03f
                        || fx != 0f || fy != 0f || fz != 0f) {
                    active = true;
                }
            }
            return active;
        }

        private void computeVerts() {
            System.arraycopy(baseVerts, 0, verts, 0, baseVerts.length);
            float unit = 0.10f * Math.min(W, H);
            int vc = baseVerts.length / 2;
            for (int i = 0; i < areas.size() && i < st.length && i < weights.length; i++) {
                Prefs.Area a = areas.get(i);
                St s = st[i];
                float ampN = a.amp / 100f;
                float ox = s.x * ampN * unit;
                float oy = s.y * ampN * unit;
                float rs = s.s * 0.2f * ampN;
                float[] wt = weights[i];
                for (int v = 0; v < vc; v++) {
                    float w = wt[v];
                    if (w <= 0f) continue;
                    float px = baseVerts[v * 2], py = baseVerts[v * 2 + 1];
                    verts[v * 2] += (ox + (px - aCx[i]) * rs) * w;
                    verts[v * 2 + 1] += (oy + (py - aCy[i]) * rs) * w;
                }
            }
        }

        private void draw() {
            if (!visible || holder == null || viewBmp == null || weights == null || baseVerts == null) return;
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c == null) return;
                c.drawColor(Color.BLACK);
                computeVerts();
                c.drawBitmapMesh(viewBmp, MESH_W, MESH_H, verts, 0, null, 0, null);
            } catch (Exception ignored) {
            } finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
