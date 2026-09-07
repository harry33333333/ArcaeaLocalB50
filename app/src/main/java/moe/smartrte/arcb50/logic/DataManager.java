package moe.smartrte.arcb50.logic;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 管理定数表与曲目元数据（支持离线内置与在线从 GitHub 仓库热更新）
 */
public class DataManager {
    private static final String TAG = "DataManager";
    private static DataManager instance;

    private static final String GITHUB_RAW_BASE = "https://raw.githubusercontent.com/SmartRTE/SmartRTE.github.io/main/";
    private static final String CONSTANTS_URL = GITHUB_RAW_BASE + "json/constants.json";
    private static final String SONGLIST_URL = GITHUB_RAW_BASE + "json/songlist";

    public static class SongDifficultyInfo {
        public String songName;
        public String difficultyName;
        public double constant;
        public String illustration;

        public SongDifficultyInfo(String songName, String difficultyName, double constant, String illustration) {
            this.songName = songName;
            this.difficultyName = difficultyName;
            this.constant = constant;
            this.illustration = illustration;
        }
    }

    public static class DiffMeta {
        public int ratingClass;
        public int ratingClassAlias;
        public boolean jacketOverride;
        public String diffTitle;
    }

    public static class SongMeta {
        public int idx;
        public String id;
        public String baseTitle;
        public Map<Integer, DiffMeta> difficulties = new HashMap<>();
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean initialized = false;

    private String dataVersion = "";
    private String dataUpdatedAt = "";
    // idx -> { "PST": 1.5, "PRS": 4.5, "FTR": 7.0, ... }
    private final Map<String, Map<String, Double>> constantsByIdx = new HashMap<>();
    // songId -> SongMeta
    private final Map<String, SongMeta> songsById = new HashMap<>();

    private static final Map<String, String> TITLE_FALLBACKS = new HashMap<>();
    static {
        TITLE_FALLBACKS.put("ii", "II");
        TITLE_FALLBACKS.put("particlearts", "Particle Arts");
    }

    private DataManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized DataManager getInstance(Context context) {
        if (instance == null) {
            instance = new DataManager(context);
        }
        return instance;
    }

    public synchronized void initialize() {
        if (initialized) return;
        loadConstants();
        loadSonglist();
        initialized = true;
    }

    private void loadConstants() {
        try {
            String jsonStr = readLocalOrAsset("constants.json", "json/constants.json");
            JSONObject root = new JSONObject(jsonStr);
            dataVersion = root.optString("version", "");
            dataUpdatedAt = root.optString("updatedAt", "");
            JSONObject songConsts = root.optJSONObject("songConstants");
            constantsByIdx.clear();
            if (songConsts != null) {
                JSONArray keys = songConsts.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String idx = keys.getString(i);
                        JSONObject cObj = songConsts.getJSONObject(idx);
                        Map<String, Double> map = new HashMap<>();
                        JSONArray cKeys = cObj.names();
                        if (cKeys != null) {
                            for (int j = 0; j < cKeys.length(); j++) {
                                String diffKey = cKeys.getString(j);
                                map.put(diffKey, cObj.optDouble(diffKey, 0.0));
                            }
                        }
                        constantsByIdx.put(idx, map);
                    }
                }
            }
            Log.i(TAG, "Constants loaded: " + constantsByIdx.size() + " songs, version=" + dataVersion);
        } catch (Exception e) {
            Log.e(TAG, "Error loading constants", e);
        }
    }

    private void loadSonglist() {
        try {
            String jsonStr = readLocalOrAsset("songlist", "json/songlist");
            JSONObject root = new JSONObject(jsonStr);
            JSONArray songs = root.optJSONArray("songs");
            songsById.clear();
            if (songs != null) {
                for (int i = 0; i < songs.length(); i++) {
                    JSONObject songObj = songs.getJSONObject(i);
                    SongMeta meta = new SongMeta();
                    meta.idx = songObj.optInt("idx", -1);
                    meta.id = songObj.optString("id", "");

                    // Title
                    JSONObject tl = songObj.optJSONObject("title_localized");
                    String title = "";
                    if (tl != null) {
                        title = tl.optString("en", tl.optString("ja", ""));
                    }
                    if (TITLE_FALLBACKS.containsKey(meta.id)) {
                        title = TITLE_FALLBACKS.get(meta.id);
                    } else if (title.isEmpty()) {
                        title = meta.id;
                    }
                    meta.baseTitle = title;

                    // Difficulties
                    JSONArray diffs = songObj.optJSONArray("difficulties");
                    if (diffs != null) {
                        for (int j = 0; j < diffs.length(); j++) {
                            JSONObject dObj = diffs.getJSONObject(j);
                            DiffMeta dm = new DiffMeta();
                            dm.ratingClass = dObj.optInt("ratingClass", 0);
                            dm.ratingClassAlias = dObj.optInt("ratingClassAlias", 0);
                            dm.jacketOverride = dObj.optBoolean("jacketOverride", false);

                            JSONObject dTl = dObj.optJSONObject("title_localized");
                            if (dTl != null && dTl.has("en")) {
                                dm.diffTitle = dTl.optString("en", meta.baseTitle);
                            } else {
                                dm.diffTitle = meta.baseTitle;
                            }
                            meta.difficulties.put(dm.ratingClass, dm);
                        }
                    }
                    songsById.put(meta.id, meta);
                }
            }
            Log.i(TAG, "Songlist loaded: " + songsById.size() + " songs");
        } catch (Exception e) {
            Log.e(TAG, "Error loading songlist", e);
        }
    }

    private String readLocalOrAsset(String localFileName, String assetPath) throws Exception {
        File localFile = new File(context.getFilesDir(), localFileName);
        InputStream is;
        if (localFile.exists() && localFile.length() > 0) {
            is = new FileInputStream(localFile);
        } else {
            is = context.getAssets().open(assetPath);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        is.close();
        return sb.toString();
    }

    /**
     * 根据 songId 和 ratingClass 查询对应难度的曲名、难度名、定数和曲绘路径
     */
    public SongDifficultyInfo getSongDifficultyInfo(String songId, int ratingClass) {
        SongMeta song = songsById.get(songId);
        String baseName = (song != null) ? song.baseTitle : songId;
        String diffName = getDifficultyName(ratingClass, song);
        double constant = 0.0;
        String illustration = songId + ".jpg";

        if (song != null) {
            DiffMeta dm = song.difficulties.get(ratingClass);
            if (dm != null) {
                if (dm.diffTitle != null && !dm.diffTitle.isEmpty()) {
                    baseName = dm.diffTitle;
                }
                if (dm.jacketOverride) {
                    illustration = songId + "_" + ratingClass + ".jpg";
                }
            }

            Map<String, Double> constMap = constantsByIdx.get(String.valueOf(song.idx));
            if (constMap != null) {
                String constKey = getConstKey(diffName);
                if (constMap.containsKey(constKey)) {
                    constant = constMap.get(constKey);
                } else if ("Inscribed".equals(diffName) && constMap.containsKey("BYD")) {
                    constant = constMap.get("BYD");
                }
            }
        }

        return new SongDifficultyInfo(baseName, diffName, constant, illustration);
    }

    private String getDifficultyName(int ratingClass, SongMeta song) {
        switch (ratingClass) {
            case 0: return "Past";
            case 1: return "Present";
            case 2: return "Future";
            case 3:
                if (song != null) {
                    DiffMeta dm = song.difficulties.get(3);
                    if (dm != null && dm.ratingClassAlias == 1) {
                        return "Inscribed";
                    }
                }
                return "Beyond";
            case 4: return "Eternal";
            default: return "Future";
        }
    }

    private String getConstKey(String diffName) {
        switch (diffName) {
            case "Past": return "PST";
            case "Present": return "PRS";
            case "Future": return "FTR";
            case "Beyond": return "BYD";
            case "Eternal": return "ETR";
            case "Inscribed": return "INS";
            default: return "FTR";
        }
    }

    public interface UpdateCallback {
        void onSuccess(String version, int count);
        void onError(String message);
    }

    /**
     * 在线从 GitHub 仓库下载最新的定数表与 songlist
     */
    public void updateFromGithub(final UpdateCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String constData = downloadString(CONSTANTS_URL);
                    String songlistData = downloadString(SONGLIST_URL);

                    // 简单校验 JSON 合法性
                    JSONObject constJson = new JSONObject(constData);
                    JSONObject songlistJson = new JSONObject(songlistData);

                    // 写入私有目录
                    saveToFile("constants.json", constData);
                    saveToFile("songlist", songlistData);

                    // 重新载入内存
                    loadConstants();
                    loadSonglist();

                    final String ver = dataVersion;
                    final int count = songsById.size();
                    if (callback != null) {
                        callback.onSuccess(ver, count);
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Failed to update from Github", e);
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                }
            }
        });
    }

    private String downloadString(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("GET");
        if (conn.getResponseCode() != 200) {
            throw new Exception("HTTP " + conn.getResponseCode() + " from " + urlStr);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    private void saveToFile(String fileName, String content) throws Exception {
        File file = new File(context.getFilesDir(), fileName);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(content.getBytes(StandardCharsets.UTF_8));
        fos.flush();
        fos.close();
    }

    public String getDataVersion() { return dataVersion; }
    public String getDataUpdatedAt() { return dataUpdatedAt; }
    public int getLoadedSongCount() { return songsById.size(); }
}
