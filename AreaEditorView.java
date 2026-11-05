package com.shake.wallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Редактор: показывает картинку, рамку видимой на экране части и список областей.
 * Область можно двигать, менять размер точками на краях, выбирать касанием.
 */
final class AreaEditorView extends View {

    interface Listener {
        void onSelect(int index);
        void onAreaChanged();
        void onCropChanged(float cx, float cy);
    }

    static final int ACCENT = 0xFFFF5C9D;
    private static final int ACCENT2 = 0xFF6EC8FF;

    private static final int M_NONE = 0, M_MOVE = 1, M_RX = 2, M_RY = 3, M_CROP = 4;

    private Bitmap bmp;
    private List<Prefs.Area> areas = new ArrayList<>();
    private int selected = -1;
    private boolean cropMode;
    private float zoom = 1f;
    private float cropCx = 0.5f, cropCy = 0.5f;
    private float deviceRatio = 0.5f;
    private Listener listener;

    private int mode = M_NONE;
    private float lastNx, lastNy;
    private final float density;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();

    AreaEditorView(Context ctx) {
        super(ctx);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        density = dm.density;
        dimPaint.setColor(0x99000000);
        framePaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(14 * density);
        setBackgroundColor(0xFF000000);
    }

    void setListener(Listener l) { listener = l; }

    void setImage(Bitmap b) {
        bmp = b;
        requestLayout();
        invalidate();
    }

    void setAreas(List<Prefs.Area> a) { areas = a; invalidate(); }

    void setSelected(int i) { selected = i; invalidate(); }

    void setCropMode(boolean c) { cropMode = c; invalidate(); }

    void setZoom(float z) { zoom = z; invalidate(); }

    void setCrop(float cx, float cy) { cropCx = cx; cropCy = cy; invalidate(); }

    void setDeviceRatio(float r) { deviceRatio = r; invalidate(); }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        if (bmp == null) {
            setMeasuredDimension(w, (int) (w * 1.2f));
            return;
        }
        float ratio = (float) bmp.getHeight() / bmp.getWidth();
        int h = (int) (w * ratio);
        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.68f);
        if (h > maxH) {
            h = maxH;
            w = (int) (h / ratio);
        }
        setMeasuredDimension(w, h);
    }

    private float[] cropRect() {
        float bw = bmp != null ? bmp.getWidth() : 1f;
        float bh = bmp != null ? bmp.getHeight() : 1f;
        float[] f = Prefs.visibleFrac(bw, bh, deviceRatio, zoom);
        float cx = Prefs.clamp(cropCx, f[0] / 2f, 1f - f[0] / 2f);
        float cy = Prefs.clamp(cropCy, f[1] / 2f, 1f - f[1] / 2f);
        return new float[]{cx, cy, f[0], f[1]};
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (bmp != null) {
            tmp.set(0, 0, w, h);
            c.drawBitmap(bmp, null, tmp, bmpPaint);
        }

        // рамка видимой части
        float[] cr = cropRect();
        float l = (cr[0] - cr[2] / 2f) * w, t = (cr[1] - cr[3] / 2f) * h;
        float r = (cr[0] + cr[2] / 2f) * w, b = (cr[1] + cr[3] / 2f) * h;
        c.drawRect(0, 0, w, t, dimPaint);
        c.drawRect(0, b, w, h, dimPaint);
        c.drawRect(0, t, l, b, dimPaint);
        c.drawRect(r, t, w, b, dimPaint);
        framePaint.setColor(cropMode ? ACCENT2 : 0xCCFFFFFF);
        framePaint.setStrokeWidth((cropMode ? 3f : 1.5f) * density);
        framePaint.setPathEffect(cropMode ? null : new DashPathEffect(new float[]{12 * density, 8 * density}, 0));
        c.drawRect(l, t, r, b, framePaint);

        // области
        for (int i = 0; i < areas.size(); i++) {
            Prefs.Area a = areas.get(i);
            tmp.set((a.cx - a.rx) * w, (a.cy - a.ry) * h, (a.cx + a.rx) * w, (a.cy + a.ry) * h);
            boolean sel = i == selected && !cropMode;
            fillPaint.setColor(sel ? 0x55FF5C9D : 0x33FFFFFF);
            c.drawOval(tmp, fillPaint);
            strokePaint.setColor(sel ? ACCENT : 0xFFFFFFFF);
            strokePaint.setStrokeWidth((sel ? 3f : 1.5f) * density);
            c.drawOval(tmp, strokePaint);
            c.drawText(String.valueOf(i + 1), a.cx * w, a.cy * h + textPaint.getTextSize() / 3f, textPaint);
            if (sel) {
                handlePaint.setColor(ACCENT);
                c.drawCircle((a.cx + a.rx) * w, a.cy * h, 8 * density, handlePaint);
                c.drawCircle(a.cx * w, (a.cy + a.ry) * h, 8 * density, handlePaint);
            }
        }
    }

    private boolean inside(Prefs.Area a, float nx, float ny) {
        float ex = (nx - a.cx) / a.rx, ey = (ny - a.cy) / a.ry;
        return ex * ex + ey * ey <= 1f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return false;
        float x = e.getX(), y = e.getY();
        float nx = x / w, ny = y / h;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                lastNx = nx;
                lastNy = ny;
                mode = M_NONE;
                if (cropMode) {
                    mode = M_CROP;
                } else {
                    float hit = 26 * density;
                    if (selected >= 0 && selected < areas.size()) {
                        Prefs.Area a = areas.get(selected);
                        if (dist(x, y, (a.cx + a.rx) * w, a.cy * h) <= hit) mode = M_RX;
                        else if (dist(x, y, a.cx * w, (a.cy + a.ry) * h) <= hit) mode = M_RY;
                    }
                    if (mode == M_NONE) {
                        for (int i = areas.size() - 1; i >= 0; i--) {
                            if (inside(areas.get(i), nx, ny)) {
                                if (i != selected) {
                                    selected = i;
                                    if (listener != null) listener.onSelect(i);
                                }
                                mode = M_MOVE;
                                break;
                            }
                        }
                    }
                }
                if (mode == M_NONE) return false; // пусть ScrollView прокручивается
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mode == M_NONE) return false;
                float dx = nx - lastNx, dy = ny - lastNy;
                lastNx = nx;
                lastNy = ny;
                if (mode == M_CROP) {
                    float[] cr = cropRect();
                    // рамка тянется за пальцем
                    cropCx = Prefs.clamp(cr[0] + dx, cr[2] / 2f, 1f - cr[2] / 2f);
                    cropCy = Prefs.clamp(cr[1] + dy, cr[3] / 2f, 1f - cr[3] / 2f);
                } else if (selected >= 0 && selected < areas.size()) {
                    Prefs.Area a = areas.get(selected);
                    if (mode == M_MOVE) {
                        a.cx = Prefs.clamp(a.cx + dx, 0f, 1f);
                        a.cy = Prefs.clamp(a.cy + dy, 0f, 1f);
                    } else if (mode == M_RX) {
                        a.rx = Prefs.clamp(Math.abs(nx - a.cx), 0.03f, 0.5f);
                    } else if (mode == M_RY) {
                        a.ry = Prefs.clamp(Math.abs(ny - a.cy), 0.03f, 0.5f);
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mode == M_CROP) {
                    if (listener != null) listener.onCropChanged(cropCx, cropCy);
                } else if (mode != M_NONE) {
                    if (listener != null) listener.onAreaChanged();
                }
                mode = M_NONE;
                return true;
            }
            default:
                return true;
        }
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
