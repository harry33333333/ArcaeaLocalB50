package moe.smartrte.arcb50.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.LruCache;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import moe.smartrte.arcb50.R;
import moe.smartrte.arcb50.logic.DataManager;
import moe.smartrte.arcb50.logic.RatingCalculator;
import moe.smartrte.arcb50.logic.RootExtractor;
import moe.smartrte.arcb50.logic.St3Parser;
import moe.smartrte.arcb50.model.B50Summary;
import moe.smartrte.arcb50.model.PlayResult;
import moe.smartrte.arcb50.model.UserSettings;
import moe.smartrte.arcb50.render.B50ImageDrawer;
import moe.smartrte.arcb50.render.ImageSaver;

/**
 * 主界面：纯原生 Android 架构，极致流畅，支持查分数据离线持久化缓存、9位好友码自动空格格式化与画廊式选择
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String CACHE_FILE_NAME = "cached_b50_summary.json";

    private MaterialToolbar toolbar;
    private ImageView ivAvatarPreview;
    private ImageView ivRatingPreview;
    private TextView tvUserNameDisplay;
    private TextView tvUserIdDisplay;
    private TextView tvDbVersionInfo;
    private TextView tvStatMaxPtt;
    private TextView tvStatB50Ptt;
    private TextView tvStatB10Ptt;
    private TextView tvSongCountBadge;
    private RecyclerView rvSongs;
    private SongListAdapter songAdapter;
    private FrameLayout layoutLoading;
    private TextView tvLoadingText;

    private MaterialButton btnRootLoad;
    private MaterialButton btnManualLoad;
    private MaterialButton btnGenerateImage;
    private MaterialButton btnSaveImage;

    private UserSettings userSettings;
    private B50Summary currentSummary = new B50Summary();
    private Bitmap lastGeneratedBitmap = null;

    private final ExecutorService workExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final LruCache<String, Bitmap> previewBitmapCache = new LruCache<>(80);

    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initData();
        initListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ivAvatarPreview = findViewById(R.id.iv_avatar_preview);
        ivRatingPreview = findViewById(R.id.iv_rating_preview);
        tvUserNameDisplay = findViewById(R.id.tv_user_name_display);
        tvUserIdDisplay = findViewById(R.id.tv_user_id_display);
        tvDbVersionInfo = findViewById(R.id.tv_db_version_info);
        tvStatMaxPtt = findViewById(R.id.tv_stat_max_ptt);
        tvStatB50Ptt = findViewById(R.id.tv_stat_b50_ptt);
        tvStatB10Ptt = findViewById(R.id.tv_stat_b10_ptt);
        tvSongCountBadge = findViewById(R.id.tv_song_count_badge);
        rvSongs = findViewById(R.id.rv_songs);
        layoutLoading = findViewById(R.id.layout_loading);
        tvLoadingText = findViewById(R.id.tv_loading_text);

        btnRootLoad = findViewById(R.id.btn_root_load);
        btnManualLoad = findViewById(R.id.btn_manual_load);
        btnGenerateImage = findViewById(R.id.btn_generate_image);
        btnSaveImage = findViewById(R.id.btn_save_image);

        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        songAdapter = new SongListAdapter(this);
        rvSongs.setAdapter(songAdapter);
    }

    private void initData() {
        userSettings = UserSettings.load(this);
        updateUserDisplay();

        showLoading("正在初始化离线曲目定数库...");
        workExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final DataManager dm = DataManager.getInstance(MainActivity.this);
                dm.initialize();

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        hideLoading();
                        String ver = dm.getDataVersion();
                        tvDbVersionInfo.setText("定数表版本: " + (ver.isEmpty() ? "本地缓存" : ver) + " (" + dm.getLoadedSongCount() + " 首)");
                    }
                });

                // 读取上次退出前保存的查分数据
                loadSummaryFromCache();
            }
        });

        // 注册文件选择器（全面兼容 SAF、MT 管理器、ZArchiver 等第三方管理器）
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                processPickedUri(uri);
                            }
                        }
                    }
                }
        );
    }

    private void initListeners() {
        btnRootLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performRootExtraction();
            }
        });

        // 手动选择文件：支持第三方管理器（MT管理器等）与系统选择器
        btnManualLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent getContentIntent = new Intent(Intent.ACTION_GET_CONTENT);
                getContentIntent.setType("*/*");
                getContentIntent.addCategory(Intent.CATEGORY_OPENABLE);
                Intent chooser = Intent.createChooser(getContentIntent, "选择 st3 数据库或成绩文件 (支持MT管理器)");
                filePickerLauncher.launch(chooser);
            }
        });

        btnGenerateImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateB50Image(false);
            }
        });

        btnSaveImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastGeneratedBitmap != null) {
                    saveBitmapToGallery(lastGeneratedBitmap);
                } else {
                    generateB50Image(true);
                }
            }
        });
    }

    private void updateUserDisplay() {
        tvUserNameDisplay.setText(userSettings.getUserName());
        tvUserIdDisplay.setText(userSettings.isShowUserId() ? ("ID : " + userSettings.getUserId()) : "ID : 已隐藏");

        // 刷新头像预览
        String avatarName = userSettings.getAvatarName();
        if (avatarName != null && !avatarName.contains("_icon")) {
            avatarName = avatarName.replace(".webp", "_icon.webp");
        }
        try (InputStream is = getAssets().open("img/avatar/" + avatarName)) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp != null) ivAvatarPreview.setImageBitmap(bmp);
        } catch (Exception e) {
            try (InputStream is2 = getAssets().open("img/avatar/34u_icon.webp")) {
                Bitmap bmp2 = BitmapFactory.decodeStream(is2);
                if (bmp2 != null) ivAvatarPreview.setImageBitmap(bmp2);
            } catch (Exception ignored) {}
        }

        // 刷新段位框预览
        String ratingImg = RatingCalculator.getPotentialFrameImage(currentSummary.getMaxPtt());
        try (InputStream is = getAssets().open("img/rating/" + ratingImg)) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp != null) ivRatingPreview.setImageBitmap(bmp);
        } catch (Exception ignored) {}
    }

    private void applySummary(B50Summary summary, boolean saveToCache) {
        this.currentSummary = summary;
        this.lastGeneratedBitmap = null; // 重置已生成的长图

        tvStatMaxPtt.setText(summary.getFormattedMaxPtt());
        tvStatB50Ptt.setText(summary.getFormattedB50Ptt());
        tvStatB10Ptt.setText(summary.getFormattedB10Ptt());

        List<PlayResult> displayList = new ArrayList<>();
        if (summary.getB50List() != null) displayList.addAll(summary.getB50List());
        if (summary.getOverflowList() != null) displayList.addAll(summary.getOverflowList());

        tvSongCountBadge.setText("共 " + displayList.size() + " 首成绩");
        songAdapter.setData(displayList);
        updateUserDisplay();

        if (saveToCache && summary.getB50List() != null && !summary.getB50List().isEmpty()) {
            saveSummaryToCache(summary);
        }
    }

    private void saveSummaryToCache(final B50Summary summary) {
        workExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File cacheFile = new File(getFilesDir(), CACHE_FILE_NAME);
                    try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                        fos.write(summary.toJson().toString().getBytes(StandardCharsets.UTF_8));
                        fos.flush();
                    }
                    Log.i(TAG, "B50 scores successfully cached to " + cacheFile.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to cache scores", e);
                }
            }
        });
    }

    private void loadSummaryFromCache() {
        try {
            File cacheFile = new File(getFilesDir(), CACHE_FILE_NAME);
            if (cacheFile.exists() && cacheFile.length() > 0) {
                byte[] bytes = new byte[(int) cacheFile.length()];
                try (FileInputStream fis = new FileInputStream(cacheFile)) {
                    fis.read(bytes);
                }
                String jsonStr = new String(bytes, StandardCharsets.UTF_8);
                final B50Summary cached = B50Summary.fromJson(new JSONObject(jsonStr));
                if (cached != null && cached.getB50List() != null && !cached.getB50List().isEmpty()) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            applySummary(cached, false);
                            Toast.makeText(MainActivity.this, "已自动载入历史查分记录 (" + cached.getB50List().size() + " 首)", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached scores", e);
        }
    }

    private void clearScoreCache() {
        new AlertDialog.Builder(this)
                .setTitle("确认清空缓存")
                .setMessage("确定要清空本地保存的历史查分数据吗？清空后需重新读取 st3 才能显示成绩。")
                .setPositiveButton("清空", (dialog, which) -> {
                    try {
                        File cacheFile = new File(getFilesDir(), CACHE_FILE_NAME);
                        if (cacheFile.exists()) cacheFile.delete();
                    } catch (Exception ignored) {}

                    currentSummary = new B50Summary();
                    applySummary(currentSummary, false);
                    Toast.makeText(MainActivity.this, "已清空本地成绩缓存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void performRootExtraction() {
        showLoading("正在通过 Magisk / su 授权提取 Arcaea st3 数据库...");
        RootExtractor.extractSt3(this, new RootExtractor.ExtractCallback() {
            @Override
            public void onSuccess(final File st3File) {
                showLoading("正在解析并计算 B50 成绩...");
                workExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final B50Summary summary = St3Parser.parseFile(MainActivity.this, st3File);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    hideLoading();
                                    applySummary(summary, true); // 自动保存到缓存
                                    Toast.makeText(MainActivity.this, "成功读取并缓存 Arcaea 成绩！", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (final Exception e) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    hideLoading();
                                    showErrorDialog("解析 st3 数据库失败", e.getMessage());
                                }
                            });
                        }
                    }
                });
            }

            @Override
            public void onError(final String message) {
                hideLoading();
                showErrorDialog("Root 提权失败", message);
            }
        });
    }

    private void processPickedUri(final Uri uri) {
        showLoading("正在读取所选文件并解析...");
        workExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File tempFile = new File(getCacheDir(), "picked_scores_temp.db");
                    if (tempFile.exists()) tempFile.delete();

                    InputStream is;
                    if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
                        is = new FileInputStream(new File(uri.getPath()));
                    } else {
                        is = getContentResolver().openInputStream(uri);
                    }

                    if (is == null) {
                        throw new Exception("无法打开选中的文件数据流");
                    }

                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) > 0) {
                            fos.write(buf, 0, len);
                        }
                        fos.flush();
                    } finally {
                        is.close();
                    }

                    final B50Summary summary = St3Parser.parseFile(MainActivity.this, tempFile);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            hideLoading();
                            applySummary(summary, true); // 自动保存到缓存
                            Toast.makeText(MainActivity.this, "成功解析并缓存 B50 成绩！", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "Failed to parse picked file", e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            hideLoading();
                            showErrorDialog("解析文件失败", e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void generateB50Image(final boolean saveAfterGen) {
        if (currentSummary.getB50List().isEmpty()) {
            Toast.makeText(this, "当前无有效成绩数据，请先载入 st3 或 CSV", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading("正在渲染 B50 高清长图 (1700px)...");

        workExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    B50ImageDrawer drawer = new B50ImageDrawer(MainActivity.this);
                    final Bitmap bitmap = drawer.drawB50(currentSummary, userSettings);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            hideLoading();
                            lastGeneratedBitmap = bitmap;
                            if (saveAfterGen) {
                                saveBitmapToGallery(bitmap);
                            } else {
                                Toast.makeText(MainActivity.this, "长图生成成功！(点击右下角按钮即可保存至相册)", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "Failed to draw B50 image", e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            hideLoading();
                            showErrorDialog("原生 Canvas 生成失败", e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void saveBitmapToGallery(final Bitmap bitmap) {
        showLoading("正在保存长图至系统相册 (Pictures/ArcaeaB50)...");
        workExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Uri uri = ImageSaver.saveToGallery(MainActivity.this, bitmap);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            hideLoading();
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("保存成功 🎉")
                                    .setMessage("B50 长图已成功存入系统相册！\n存储位置: Pictures/ArcaeaB50")
                                    .setPositiveButton("好", null)
                                    .show();
                        }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "Failed to save image to gallery", e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            hideLoading();
                            showErrorDialog("保存相册失败", e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void updateConstantsFromGithub() {
        showLoading("正在从 GitHub 仓库下载最新定数表...");
        DataManager.getInstance(this).updateFromGithub(new DataManager.UpdateCallback() {
            @Override
            public void onSuccess(final String version, final int count) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        hideLoading();
                        tvDbVersionInfo.setText("定数表版本: " + version + " (" + count + " 首)");
                        Toast.makeText(MainActivity.this, "定数表已更新至最新版本: " + version, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(final String message) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        hideLoading();
                        showErrorDialog("更新定数表失败", message + "\n请检查网络连接或稍后重试。");
                    }
                });
            }
        });
    }

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        final EditText etName = dialogView.findViewById(R.id.et_setting_name);
        final EditText etId = dialogView.findViewById(R.id.et_setting_id);
        final CheckBox cbShowId = dialogView.findViewById(R.id.cb_setting_show_id);

        // 段位框组件
        final ImageView ivCurrentDan = dialogView.findViewById(R.id.iv_current_dan_preview);
        final TextView tvCurrentDan = dialogView.findViewById(R.id.tv_current_dan_title);
        final Button btnSelectDan = dialogView.findViewById(R.id.btn_select_dan);

        // 头像组件
        final ImageView ivAvatarPreviewDialog = dialogView.findViewById(R.id.iv_preview_dialog_avatar);
        final TextView tvAvatarPreviewInfo = dialogView.findViewById(R.id.tv_preview_dialog_avatar_info);
        final Button btnSelectAvatar = dialogView.findViewById(R.id.btn_select_avatar);

        // 背景组件
        final ImageView ivBgPreviewDialog = dialogView.findViewById(R.id.iv_preview_dialog_bg);
        final TextView tvBgPreviewInfo = dialogView.findViewById(R.id.tv_preview_dialog_bg_info);
        final Button btnSelectBg = dialogView.findViewById(R.id.btn_select_bg);

        // 数量组件
        final Spinner spQuantityPreset = dialogView.findViewById(R.id.sp_setting_quantity_preset);
        final EditText etQuantity = dialogView.findViewById(R.id.et_setting_quantity);

        etName.setText(userSettings.getUserName());
        etId.setText(userSettings.getUserId());
        cbShowId.setChecked(userSettings.isShowUserId());
        etQuantity.setText(String.valueOf(userSettings.getUnitQuantity()));

        // 好友码输入监听：每3个数字自动加1空格，且限制为 9 位数字
        etId.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String digits = s.toString().replaceAll("[^0-9]", "");
                if (digits.length() > 9) digits = digits.substring(0, 9);

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i == 3 || i == 6) formatted.append(" ");
                    formatted.append(digits.charAt(i));
                }

                s.replace(0, s.length(), formatted.toString());
                isFormatting = false;
            }
        });

        // 暂存当前选择状态（点击保存后才持久化）
        final int[] selectedDan = {userSettings.getCourseDan()};
        final String[] selectedAvatar = {userSettings.getAvatarName()};
        final String[] selectedBg = {userSettings.getBackgroundName()};

        // 刷新段位框预览
        Runnable refreshDanPreview = new Runnable() {
            @Override
            public void run() {
                int dan = selectedDan[0];
                if (dan == 0) {
                    tvCurrentDan.setText("当前段位: 【0】不显示段位框");
                    ivCurrentDan.setImageDrawable(null);
                } else {
                    tvCurrentDan.setText("当前段位: Course Dan " + dan);
                    try (InputStream is = getAssets().open("img/course/" + dan + ".png")) {
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        ivCurrentDan.setImageBitmap(bmp);
                    } catch (Exception ignored) {}
                }
            }
        };
        refreshDanPreview.run();

        // 刷新头像预览
        Runnable refreshAvatarPreview = new Runnable() {
            @Override
            public void run() {
                String avt = selectedAvatar[0];
                if (avt != null && !avt.contains("_icon")) {
                    avt = avt.replace(".webp", "_icon.webp");
                }
                tvAvatarPreviewInfo.setText("当前选中: " + avt);
                try (InputStream is = getAssets().open("img/avatar/" + avt)) {
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    ivAvatarPreviewDialog.setImageBitmap(bmp);
                } catch (Exception ignored) {}
            }
        };
        refreshAvatarPreview.run();

        // 刷新背景预览
        Runnable refreshBgPreview = new Runnable() {
            @Override
            public void run() {
                String bg = selectedBg[0];
                tvBgPreviewInfo.setText("当前选中: " + bg);
                try (InputStream is = getAssets().open("bgs/" + bg)) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 2;
                    Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
                    ivBgPreviewDialog.setImageBitmap(bmp);
                } catch (Exception ignored) {}
            }
        };
        refreshBgPreview.run();

        // 绑定浏览画廊事件
        btnSelectDan.setOnClickListener(v -> showDanGalleryDialog(selectedDan, refreshDanPreview));
        btnSelectAvatar.setOnClickListener(v -> showAvatarGalleryDialog(selectedAvatar, refreshAvatarPreview));
        btnSelectBg.setOnClickListener(v -> showBgGalleryDialog(selectedBg, refreshBgPreview));

        // 数量快捷选项预设 (最低 50，步长为 5)
        final String[] quantityPresets = {
                "60 (50+10, 默认推荐)",
                "50 (仅 Best 50)",
                "55 (50+5)",
                "65 (50+15)",
                "70 (50+20)",
                "75 (50+25)",
                "80 (50+30)",
                "85 (50+35)",
                "90 (50+40)",
                "100 (50+50)",
                "自定义输入..."
        };
        final int[] presetValues = {60, 50, 55, 65, 70, 75, 80, 85, 90, 100, -1};
        ArrayAdapter<String> qAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, quantityPresets);
        spQuantityPreset.setAdapter(qAdapter);

        int matchIdx = 0;
        for (int i = 0; i < presetValues.length - 1; i++) {
            if (presetValues[i] == userSettings.getUnitQuantity()) {
                matchIdx = i;
                break;
            }
        }
        spQuantityPreset.setSelection(matchIdx);

        spQuantityPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int val = presetValues[position];
                if (val > 0) {
                    etQuantity.setText(String.valueOf(val));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        new AlertDialog.Builder(this)
                .setTitle("个性化设置")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newName = etName.getText().toString().trim();
                    if (!newName.isEmpty()) userSettings.setUserName(newName);

                    // 9 位好友码严格校验
                    String digits = etId.getText().toString().replaceAll("[^0-9]", "");
                    if (digits.length() != 9) {
                        Toast.makeText(MainActivity.this, "好友码必须为 9 位数字！(当前为 " + digits.length() + " 位)", Toast.LENGTH_LONG).show();
                        return;
                    }
                    userSettings.setUserId(digits);
                    userSettings.setShowUserId(cbShowId.isChecked());
                    userSettings.setCourseDan(selectedDan[0]);
                    userSettings.setAvatarName(selectedAvatar[0]);
                    userSettings.setBackgroundName(selectedBg[0]);

                    try {
                        int q = Integer.parseInt(etQuantity.getText().toString().trim());
                        if (q < 50) q = 50;
                        q = ((q + 4) / 5) * 5; // 确保 5 为步长
                        userSettings.setUnitQuantity(q);
                    } catch (Exception ignored) {
                        userSettings.setUnitQuantity(60);
                    }

                    userSettings.save(MainActivity.this);
                    updateUserDisplay();
                    Toast.makeText(MainActivity.this, "设置已保存 (好友码: " + userSettings.getUserId() + ")", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 段位框全画廊视觉选择弹窗 (0~40)
     */
    private void showDanGalleryDialog(final int[] selectedDan, final Runnable onSelectCallback) {
        ListView listView = new ListView(this);
        listView.setDivider(null);
        listView.setPadding(12, 12, 12, 12);

        final List<Integer> danList = new ArrayList<>();
        danList.add(0); // 0 = 不显示
        for (int i = 1; i <= 40; i++) danList.add(i);

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() { return danList.size(); }

            @Override
            public Object getItem(int position) { return danList.get(position); }

            @Override
            public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout layout;
                if (convertView == null) {
                    layout = new LinearLayout(MainActivity.this);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(16, 12, 16, 12);
                    layout.setBackgroundColor(Color.parseColor("#1C1A2E"));

                    TextView tv = new TextView(MainActivity.this);
                    tv.setId(View.generateViewId());
                    tv.setTextSize(13);
                    tv.setTextColor(Color.WHITE);
                    layout.addView(tv);

                    ImageView iv = new ImageView(MainActivity.this);
                    iv.setId(View.generateViewId());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2px(36));
                    lp.topMargin = dp2px(4);
                    iv.setLayoutParams(lp);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    layout.addView(iv);
                    layout.setTag(new View[]{tv, iv});
                } else {
                    layout = (LinearLayout) convertView;
                }

                View[] tags = (View[]) layout.getTag();
                TextView tv = (TextView) tags[0];
                ImageView iv = (ImageView) tags[1];

                int dan = danList.get(position);
                if (dan == 0) {
                    tv.setText("【0】不显示段位横幅 (None)");
                    iv.setImageDrawable(null);
                } else {
                    tv.setText("Course Dan " + dan);
                    Bitmap bmp = loadPreviewBitmap("img/course/" + dan + ".png", 1);
                    iv.setImageBitmap(bmp);
                }

                if (dan == selectedDan[0]) {
                    layout.setBackgroundColor(Color.parseColor("#3C2E60"));
                } else {
                    layout.setBackgroundColor(Color.parseColor("#1C1A2E"));
                }

                return layout;
            }
        };
        listView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择段位框 (全40档及不显示)")
                .setView(listView)
                .setNegativeButton("返回", null)
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            selectedDan[0] = danList.get(position);
            onSelectCallback.run();
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * 头像全画廊视觉选择弹窗 (4 列网格，全部展示)
     */
    private void showAvatarGalleryDialog(final String[] selectedAvatar, final Runnable onSelectCallback) {
        GridView gridView = new GridView(this);
        gridView.setNumColumns(4);
        gridView.setHorizontalSpacing(dp2px(8));
        gridView.setVerticalSpacing(dp2px(8));
        gridView.setPadding(dp2px(12), dp2px(12), dp2px(12), dp2px(12));
        gridView.setBackgroundColor(Color.parseColor("#151322"));

        final List<String> avatarList = new ArrayList<>();
        try {
            String[] files = getAssets().list("img/avatar");
            if (files != null) {
                for (String f : files) {
                    if (f.endsWith("_icon.webp")) avatarList.add(f);
                }
            }
        } catch (Exception ignored) {}
        if (avatarList.isEmpty()) {
            avatarList.add("34u_icon.webp");
            avatarList.add("0_icon.webp");
        } else {
            Collections.sort(avatarList);
        }

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() { return avatarList.size(); }

            @Override
            public Object getItem(int position) { return avatarList.get(position); }

            @Override
            public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ImageView iv;
                if (convertView == null) {
                    iv = new ImageView(MainActivity.this);
                    iv.setLayoutParams(new GridView.LayoutParams(dp2px(64), dp2px(64)));
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setPadding(dp2px(2), dp2px(2), dp2px(2), dp2px(2));
                } else {
                    iv = (ImageView) convertView;
                }

                String avt = avatarList.get(position);
                Bitmap bmp = loadPreviewBitmap("img/avatar/" + avt, 2);
                iv.setImageBitmap(bmp);

                if (avt.equals(selectedAvatar[0])) {
                    iv.setBackgroundColor(Color.parseColor("#FFF58200")); // 选中高亮框
                } else {
                    iv.setBackgroundColor(Color.parseColor("#28243E"));
                }
                return iv;
            }
        };
        gridView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择头像 (共 " + avatarList.size() + " 款角色)")
                .setView(gridView)
                .setNegativeButton("返回", null)
                .create();

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            selectedAvatar[0] = avatarList.get(position);
            onSelectCallback.run();
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * 长图背景全画廊视觉选择弹窗 (2 列宽屏卡片，全部展示)
     */
    private void showBgGalleryDialog(final String[] selectedBg, final Runnable onSelectCallback) {
        GridView gridView = new GridView(this);
        gridView.setNumColumns(2);
        gridView.setHorizontalSpacing(dp2px(8));
        gridView.setVerticalSpacing(dp2px(8));
        gridView.setPadding(dp2px(12), dp2px(12), dp2px(12), dp2px(12));
        gridView.setBackgroundColor(Color.parseColor("#151322"));

        final List<String> bgList = new ArrayList<>();
        try {
            String[] files = getAssets().list("bgs");
            if (files != null) {
                for (String f : files) {
                    if (f.endsWith(".webp") || f.endsWith(".jpg") || f.endsWith(".png")) bgList.add(f);
                }
            }
        } catch (Exception ignored) {}
        if (bgList.isEmpty()) {
            bgList.add("s9.webp");
            bgList.add("1.webp");
        } else {
            Collections.sort(bgList);
        }

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() { return bgList.size(); }

            @Override
            public Object getItem(int position) { return bgList.get(position); }

            @Override
            public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout layout;
                if (convertView == null) {
                    layout = new LinearLayout(MainActivity.this);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setGravity(Gravity.CENTER_HORIZONTAL);
                    layout.setPadding(dp2px(4), dp2px(4), dp2px(4), dp2px(4));

                    ImageView iv = new ImageView(MainActivity.this);
                    iv.setId(View.generateViewId());
                    iv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2px(72)));
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    layout.addView(iv);

                    TextView tv = new TextView(MainActivity.this);
                    tv.setId(View.generateViewId());
                    tv.setTextSize(11);
                    tv.setTextColor(Color.parseColor("#C0C0D8"));
                    tv.setGravity(Gravity.CENTER);
                    layout.addView(tv);
                    layout.setTag(new View[]{iv, tv});
                } else {
                    layout = (LinearLayout) convertView;
                }

                View[] tags = (View[]) layout.getTag();
                ImageView iv = (ImageView) tags[0];
                TextView tv = (TextView) tags[1];

                String bg = bgList.get(position);
                tv.setText(bg);
                Bitmap bmp = loadPreviewBitmap("bgs/" + bg, 4);
                iv.setImageBitmap(bmp);

                if (bg.equals(selectedBg[0])) {
                    layout.setBackgroundColor(Color.parseColor("#FFF58200")); // 选中高亮
                } else {
                    layout.setBackgroundColor(Color.parseColor("#201E32"));
                }
                return layout;
            }
        };
        gridView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择长图背景 (共 " + bgList.size() + " 款)")
                .setView(gridView)
                .setNegativeButton("返回", null)
                .create();

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            selectedBg[0] = bgList.get(position);
            onSelectCallback.run();
            dialog.dismiss();
        });

        dialog.show();
    }

    private Bitmap loadPreviewBitmap(String assetPath, int sampleSize) {
        String cacheKey = assetPath + "@" + sampleSize;
        Bitmap cached = previewBitmapCache.get(cacheKey);
        if (cached != null) return cached;

        try (InputStream is = getAssets().open(assetPath)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
            if (bmp != null) {
                previewBitmapCache.put(cacheKey, bmp);
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private int dp2px(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void showLoading(String text) {
        tvLoadingText.setText(text);
        layoutLoading.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        layoutLoading.setVisibility(View.GONE);
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_update_db) {
            updateConstantsFromGithub();
            return true;
        } else if (id == R.id.action_clear_cache) {
            clearScoreCache();
            return true;
        } else if (id == R.id.action_settings) {
            showSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
