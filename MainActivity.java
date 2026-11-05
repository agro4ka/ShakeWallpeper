package com.shake.wallpaper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_IMAGE = 1;
    private static final int MAX_AREAS = 12;

    private static final int BG = 0xFF101114;
    private static final int CARD = 0xFF1B1D22;
    private static final int BTN = 0xFF2A2D35;
    private static final int ACCENT = AreaEditorView.ACCENT;
    private static final int TEXT = 0xFFF2F2F5;
    private static final int SUB = 0xFF9A9CA6;

    private static final String[] PARAM_KEYS = {"sens", "amp", "damp", "speed", "touch"};

    private SharedPreferences sp;
    private List<Prefs.Area> areas;
    private int sel = -1;
    private boolean cropMode = false;

    private AreaEditorView editor;
    private TextView hintView;
    private LinearLayout chips;
    private LinearLayout settingsCard;
    private TextView settingsTitle;
    private TextView noAreaView;
    private LinearLayout slidersBox;
    private Button btnCrop;
    private final SeekBar[] bars = new SeekBar[Prefs.PARAM_COUNT];
    private final TextView[] vals = new TextView[Prefs.PARAM_COUNT];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = Prefs.sp(this);
        Strings.setLang(sp.getString(Prefs.K_LANG, Strings.defaultLang()));
        areas = Prefs.loadAreas(sp);
        sel = areas.isEmpty() ? -1 : 0;
        buildUi();
    }

    // ------------------------------------------------------------------ UI helpers

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, boolean accent, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(s);
        bt.setAllCaps(false);
        bt.setTextColor(TEXT);
        bt.setTextSize(14);
        bt.setBackground(round(accent ? ACCENT : BTN, 12));
        bt.setPadding(dp(14), dp(8), dp(14), dp(8));
        bt.setStateListAnimator(null);
        bt.setOnClickListener(l);
        return bt;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(CARD, 16));
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(6);
        return lp;
    }

    /** Блок «название + описание + значение + ползунок». Возвращает SeekBar, значение лежит в tag. */
    private SeekBar slider(LinearLayout parent, String titleKey, String descKey, int max, int progress,
                           String initialValueText, SeekBar.OnSeekBarChangeListener l, TextView[] valOut, int valIdx) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(Strings.t(titleKey), 15, TEXT, true);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = text(initialValueText, 14, ACCENT, true);
        head.addView(val);
        parent.addView(head);
        parent.addView(text(Strings.t(descKey), 12, SUB, false));

        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(progress);
        sb.setOnSeekBarChangeListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        parent.addView(sb, lp);
        if (valOut != null) valOut[valIdx] = val;
        sb.setTag(val);
        return sb;
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(28), dp(14), dp(24));
        sv.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- заголовок + язык
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(Strings.t("title"), 22, TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(button(Strings.t("lang_btn"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().putString(Prefs.K_LANG, Strings.isRu() ? "en" : "ru").apply();
                recreate();
            }
        }));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(12);
        root.addView(header, hlp);

        // --- редактор
        LinearLayout edCard = card(root);
        editor = new AreaEditorView(this);
        editor.setDeviceRatio(deviceRatio());
        editor.setZoom(Prefs.getZoom(sp));
        editor.setCrop(sp.getFloat(Prefs.K_CROP_X, 0.5f), sp.getFloat(Prefs.K_CROP_Y, 0.5f));
        editor.setAreas(areas);
        editor.setSelected(sel);
        editor.setImage(loadPreview());
        editor.setListener(new AreaEditorView.Listener() {
            @Override
            public void onSelect(int index) {
                sel = index;
                refreshChips();
                bindSliders();
            }

            @Override
            public void onAreaChanged() {
                Prefs.saveAreas(sp, areas);
            }

            @Override
            public void onCropChanged(float cx, float cy) {
                sp.edit().putFloat(Prefs.K_CROP_X, cx).putFloat(Prefs.K_CROP_Y, cy).apply();
            }
        });
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.gravity = Gravity.CENTER_HORIZONTAL;
        edCard.addView(editor, elp);

        hintView = text("", 12, SUB, false);
        hintView.setPadding(0, dp(8), 0, dp(8));
        edCard.addView(hintView);
        updateHint();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button add = button(Strings.t("add_area"), true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addArea();
            }
        });
        Button del = button(Strings.t("del_area"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteArea();
            }
        });
        btnCrop = button(Strings.t("mode_move"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cropMode = !cropMode;
                editor.setCropMode(cropMode);
                btnCrop.setText(Strings.t(cropMode ? "mode_areas" : "mode_move"));
                updateHint();
            }
        });
        row.addView(add, rowLp());
        row.addView(del, rowLp());
        row.addView(btnCrop, rowLp());
        edCard.addView(row);

        // --- чипы областей
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(chips);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        edCard.addView(hsv, clp);

        // --- настройки выбранной области
        settingsCard = card(root);
        settingsTitle = text("", 17, TEXT, true);
        settingsTitle.setPadding(0, 0, 0, dp(8));
        settingsCard.addView(settingsTitle);
        noAreaView = text(Strings.t("no_area"), 13, SUB, false);
        settingsCard.addView(noAreaView);

        slidersBox = new LinearLayout(this);
        slidersBox.setOrientation(LinearLayout.VERTICAL);
        settingsCard.addView(slidersBox);
        String[] descKeys = {"sens_d", "amp_d", "damp_d", "speed_d", "touch_d"};
        for (int i = 0; i < Prefs.PARAM_COUNT; i++) {
            bars[i] = slider(slidersBox, PARAM_KEYS[i], descKeys[i], 100, 50, "50",
                    areaListener(i), vals, i);
        }
        Button applyAll = button(Strings.t("apply_all"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sel < 0 || sel >= areas.size()) return;
                for (Prefs.Area a : areas) {
                    if (a != areas.get(sel)) a.copySettings(areas.get(sel));
                }
                Prefs.saveAreas(sp, areas);
            }
        });
        slidersBox.addView(applyAll);

        // --- общие настройки (зум)
        LinearLayout common = card(root);
        TextView ct = text(Strings.t("common"), 17, TEXT, true);
        ct.setPadding(0, 0, 0, dp(8));
        common.addView(ct);
        float zoom = Prefs.getZoom(sp);
        slider(common, "zoom", "zoom_d", 200, Math.round((zoom - 1f) * 100f),
                String.format("%.1f×", zoom), new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                        float z = 1f + p / 100f;
                        ((TextView) sb.getTag()).setText(String.format("%.1f×", z));
                        if (!fromUser) return;
                        sp.edit().putFloat(Prefs.K_ZOOM, z).apply();
                        editor.setZoom(z);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar sb) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar sb) {
                    }
                }, null, 0);

        // --- картинка
        LinearLayout imgCard = card(root);
        TextView it = text(Strings.t("image"), 17, TEXT, true);
        it.setPadding(0, 0, 0, dp(8));
        imgCard.addView(it);
        LinearLayout imgRow = new LinearLayout(this);
        imgRow.setOrientation(LinearLayout.HORIZONTAL);
        imgRow.addView(button(Strings.t("builtin"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().putInt(Prefs.K_IMAGE_MODE, 0)
                        .putInt(Prefs.K_IMAGE_VER, sp.getInt(Prefs.K_IMAGE_VER, 0) + 1)
                        .putFloat(Prefs.K_CROP_X, 0.5f).putFloat(Prefs.K_CROP_Y, 0.5f).apply();
                editor.setCrop(0.5f, 0.5f);
                editor.setImage(loadPreview());
            }
        }), rowLp());
        imgRow.addView(button(Strings.t("custom"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i, REQ_IMAGE);
            }
        }), rowLp());
        imgCard.addView(imgRow);

        // --- низ
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(button(Strings.t("reset"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showResetDialog();
            }
        }), rowLp());
        bottom.addView(button(Strings.t("set_wp"), true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setWallpaper();
            }
        }), rowLp());
        root.addView(bottom);

        setContentView(sv);
        refreshChips();
        bindSliders();
    }

    private SeekBar.OnSeekBarChangeListener areaListener(final int idx) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                ((TextView) sb.getTag()).setText(String.valueOf(p));
                if (!fromUser || sel < 0 || sel >= areas.size()) return;
                areas.get(sel).set(idx, p);
                Prefs.saveAreas(sp, areas);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        };
    }

    private void updateHint() {
        hintView.setText(Strings.t(cropMode ? "hint_crop" : "hint_edit"));
    }

    private void refreshChips() {
        chips.removeAllViews();
        for (int i = 0; i < areas.size(); i++) {
            final int idx = i;
            Button b = button(Strings.t("area_n", i + 1), i == sel, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sel = idx;
                    editor.setSelected(idx);
                    refreshChips();
                    bindSliders();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            chips.addView(b, lp);
        }
    }

    private void bindSliders() {
        boolean has = sel >= 0 && sel < areas.size();
        slidersBox.setVisibility(has ? View.VISIBLE : View.GONE);
        noAreaView.setVisibility(has ? View.GONE : View.VISIBLE);
        settingsTitle.setText(has ? Strings.t("settings_for", sel + 1) : Strings.t("areas"));
        if (!has) return;
        Prefs.Area a = areas.get(sel);
        for (int i = 0; i < Prefs.PARAM_COUNT; i++) {
            bars[i].setProgress(a.get(i));
            vals[i].setText(String.valueOf(a.get(i)));
        }
    }

    // ------------------------------------------------------------------ действия

    private void addArea() {
        if (areas.size() >= MAX_AREAS) {
            Toast.makeText(this, Strings.t("max_areas"), Toast.LENGTH_SHORT).show();
            return;
        }
        float cx = sp.getFloat(Prefs.K_CROP_X, 0.5f);
        float cy = sp.getFloat(Prefs.K_CROP_Y, 0.5f);
        areas.add(new Prefs.Area(cx, cy, 0.15f, 0.10f));
        sel = areas.size() - 1;
        Prefs.saveAreas(sp, areas);
        if (cropMode) {
            cropMode = false;
            editor.setCropMode(false);
            btnCrop.setText(Strings.t("mode_move"));
            updateHint();
        }
        editor.setSelected(sel);
        refreshChips();
        bindSliders();
    }

    private void deleteArea() {
        if (sel < 0 || sel >= areas.size()) return;
        areas.remove(sel);
        sel = areas.isEmpty() ? -1 : Math.min(sel, areas.size() - 1);
        Prefs.saveAreas(sp, areas);
        editor.setSelected(sel);
        refreshChips();
        bindSliders();
    }

    private void showResetDialog() {
        String[] items = {
                Strings.t("reset_area"), Strings.t("reset_areas"),
                Strings.t("reset_image"), Strings.t("reset_all")};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Strings.t("reset_q"))
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        doReset(which);
                    }
                })
                .setNegativeButton(Strings.t("cancel"), null)
                .show();
    }

    private void doReset(int which) {
        switch (which) {
            case 0:
                if (sel >= 0 && sel < areas.size()) {
                    areas.get(sel).resetSettings();
                    Prefs.saveAreas(sp, areas);
                    bindSliders();
                }
                break;
            case 1:
                areas.clear();
                sel = -1;
                Prefs.saveAreas(sp, areas);
                editor.setSelected(-1);
                refreshChips();
                bindSliders();
                break;
            case 2:
                resetImagePrefs();
                recreate();
                break;
            case 3:
                resetImagePrefs();
                sp.edit().remove(Prefs.K_AREAS).apply();
                recreate();
                break;
        }
    }

    private void resetImagePrefs() {
        sp.edit().putInt(Prefs.K_IMAGE_MODE, 0)
                .putInt(Prefs.K_IMAGE_VER, sp.getInt(Prefs.K_IMAGE_VER, 0) + 1)
                .putFloat(Prefs.K_ZOOM, 1f)
                .putFloat(Prefs.K_CROP_X, 0.5f)
                .putFloat(Prefs.K_CROP_Y, 0.5f).apply();
    }

    private void setWallpaper() {
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, ShakeWallpaperService.class));
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
        }
    }

    // ------------------------------------------------------------------ картинки

    private float deviceRatio() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return (float) dm.widthPixels / dm.heightPixels;
    }

    private Bitmap loadPreview() {
        Bitmap b = null;
        if (sp.getInt(Prefs.K_IMAGE_MODE, 0) == 1) {
            File f = new File(getFilesDir(), Prefs.CUSTOM_FILE);
            if (f.exists()) b = Prefs.decodeFile(f.getPath(), 1024);
        }
        if (b == null) b = Prefs.decodeBuiltin(getResources(), 1024);
        return b;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMAGE || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = importImage(uri);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!ok) {
                            Toast.makeText(MainActivity.this, Strings.t("img_fail"), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        sp.edit().putInt(Prefs.K_IMAGE_MODE, 1)
                                .putInt(Prefs.K_IMAGE_VER, sp.getInt(Prefs.K_IMAGE_VER, 0) + 1)
                                .putFloat(Prefs.K_CROP_X, 0.5f).putFloat(Prefs.K_CROP_Y, 0.5f).apply();
                        editor.setCrop(0.5f, 0.5f);
                        editor.setImage(loadPreview());
                    }
                });
            }
        }).start();
    }

    /** Копирует выбранную картинку во внутреннее хранилище (уменьшая до 2048 px, учитывая поворот EXIF). */
    private boolean importImage(Uri uri) {
        File tmp = new File(getFilesDir(), "import.tmp");
        File out = new File(getFilesDir(), Prefs.CUSTOM_FILE);
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return false;
            OutputStream os = new FileOutputStream(tmp);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.close();
            in.close();

            Bitmap bmp = Prefs.decodeFile(tmp.getPath(), 2048);
            if (bmp == null) return false;

            int rot = 0;
            try {
                int o = new ExifInterface(tmp.getPath()).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
            } catch (Exception ignored) {
            }
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                Bitmap r = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                if (r != bmp) bmp.recycle();
                bmp = r;
            }

            FileOutputStream fo = new FileOutputStream(out);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
            fo.close();
            bmp.recycle();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
