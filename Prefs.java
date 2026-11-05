package com.shake.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Хранение настроек: список областей (у каждой свои параметры) + общие настройки картинки. */
final class Prefs {
    static final String FILE = "shake_prefs";
    static final String K_AREAS = "areas";
    static final String K_ZOOM = "zoom";
    static final String K_CROP_X = "crop_cx";
    static final String K_CROP_Y = "crop_cy";
    static final String K_IMAGE_MODE = "image_mode"; // 0 = встроенная, 1 = своя
    static final String K_IMAGE_VER = "image_ver";
    static final String K_LANG = "lang";
    static final String CUSTOM_FILE = "custom.png";

    static final int PARAM_COUNT = 5; // sens, amp, damp, speed, touch

    /** Одна область «желе». Координаты нормированы к размеру картинки (0..1). */
    static final class Area {
        float cx, cy, rx, ry;
        int sens = 50, amp = 50, damp = 50, speed = 50, touch = 50;

        Area(float cx, float cy, float rx, float ry) {
            this.cx = cx;
            this.cy = cy;
            this.rx = rx;
            this.ry = ry;
        }

        void resetSettings() {
            sens = amp = damp = speed = touch = 50;
        }

        void copySettings(Area o) {
            sens = o.sens;
            amp = o.amp;
            damp = o.damp;
            speed = o.speed;
            touch = o.touch;
        }

        int get(int i) {
            switch (i) {
                case 0: return sens;
                case 1: return amp;
                case 2: return damp;
                case 3: return speed;
                default: return touch;
            }
        }

        void set(int i, int v) {
            switch (i) {
                case 0: sens = v; break;
                case 1: amp = v; break;
                case 2: damp = v; break;
                case 3: speed = v; break;
                default: touch = v; break;
            }
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("cx", cx);
            o.put("cy", cy);
            o.put("rx", rx);
            o.put("ry", ry);
            o.put("sens", sens);
            o.put("amp", amp);
            o.put("damp", damp);
            o.put("speed", speed);
            o.put("touch", touch);
            return o;
        }

        static Area fromJson(JSONObject o) {
            Area a = new Area(
                    (float) o.optDouble("cx", 0.5),
                    (float) o.optDouble("cy", 0.5),
                    (float) o.optDouble("rx", 0.15),
                    (float) o.optDouble("ry", 0.10));
            a.sens = o.optInt("sens", 50);
            a.amp = o.optInt("amp", 50);
            a.damp = o.optInt("damp", 50);
            a.speed = o.optInt("speed", 50);
            a.touch = o.optInt("touch", 50);
            return a;
        }
    }

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Area defaultArea() {
        return new Area(0.48f, 0.40f, 0.17f, 0.09f);
    }

    static List<Area> loadAreas(SharedPreferences sp) {
        List<Area> out = new ArrayList<>();
        String s = sp.getString(K_AREAS, null);
        if (s == null) {
            out.add(defaultArea());
            return out;
        }
        try {
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                out.add(Area.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            out.clear();
            out.add(defaultArea());
        }
        return out;
    }

    static void saveAreas(SharedPreferences sp, List<Area> areas) {
        JSONArray arr = new JSONArray();
        try {
            for (Area a : areas) arr.put(a.toJson());
        } catch (JSONException ignored) {
        }
        sp.edit().putString(K_AREAS, arr.toString()).apply();
    }

    static float getZoom(SharedPreferences sp) {
        return Math.max(1f, Math.min(3f, sp.getFloat(K_ZOOM, 1f)));
    }

    static String imageKey(SharedPreferences sp) {
        return sp.getInt(K_IMAGE_MODE, 0) + ":" + sp.getInt(K_IMAGE_VER, 0);
    }

    /** Доля картинки (0..1 по ширине/высоте), которая видна на экране при данном зуме. */
    static float[] visibleFrac(float bw, float bh, float deviceRatio, float zoom) {
        float ir = bw / bh;
        float fw, fh;
        if (ir > deviceRatio) {
            fh = 1f;
            fw = deviceRatio / ir;
        } else {
            fw = 1f;
            fh = ir / deviceRatio;
        }
        return new float[]{fw / zoom, fh / zoom};
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static Bitmap decodeFile(String path, int maxDim) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0) return null;
        int ss = 1;
        while (Math.max(o.outWidth, o.outHeight) / ss > maxDim) ss *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = ss;
        return BitmapFactory.decodeFile(path, o2);
    }

    static Bitmap decodeBuiltin(Resources r, int maxDim) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(r, R.drawable.girl, o);
        int ss = 1;
        while (Math.max(o.outWidth, o.outHeight) / ss > maxDim) ss *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = ss;
        return BitmapFactory.decodeResource(r, R.drawable.girl, o2);
    }
}
