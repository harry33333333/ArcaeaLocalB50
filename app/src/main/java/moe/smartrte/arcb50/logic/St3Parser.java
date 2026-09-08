package moe.smartrte.arcb50.logic;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import moe.smartrte.arcb50.model.B50Summary;
import moe.smartrte.arcb50.model.PlayResult;

/**
 * 解析 Arcaea st3 (SQLite) 或 CSV 成绩文件并计算生成 B50 数据
 */
public class St3Parser {
    private static final String TAG = "St3Parser";

    public static B50Summary parseFile(Context context, File file) throws Exception {
        if (!file.exists()) {
            throw new Exception("文件不存在: " + file.getAbsolutePath());
        }

        // 检查文件头是否为 SQLite
        byte[] header = new byte[16];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = fis.read(header);
            if (read >= 16) {
                String headerStr = new String(header, StandardCharsets.UTF_8);
                if (headerStr.startsWith("SQLite format 3")) {
                    return parseSt3Database(context, file);
                }
            }
        }

        // 尝试作为 CSV 解析
        return parseCsvFile(context, file);
    }

    /**
     * 读取 SQLite st3 文件中的 scores 表
     */
    public static B50Summary parseSt3Database(Context context, File st3File) throws Exception {
        DataManager dm = DataManager.getInstance(context);
        dm.initialize();

        List<PlayResult> list = new ArrayList<>();
        SQLiteDatabase db = SQLiteDatabase.openDatabase(st3File.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            String query = "SELECT songId, songDifficulty, score, shinyPerfectCount, perfectCount, nearCount, missCount " +
                    "FROM scores WHERE score > 0;";
            Cursor cursor = db.rawQuery(query, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String songId = cursor.getString(0);
                    int difficultyClass = cursor.getInt(1);
                    int score = cursor.getInt(2);
                    int shinyPerfect = cursor.getInt(3);
                    int perfect = cursor.getInt(4);
                    int far = cursor.getInt(5);
                    int lost = cursor.getInt(6);

                    DataManager.SongDifficultyInfo info = dm.getSongDifficultyInfo(songId, difficultyClass);
                    if (info.constant > 0) {
                        double playRating = RatingCalculator.calculateSingleRating(score, info.constant);
                        PlayResult pr = new PlayResult(
                                info.songName,
                                songId,
                                info.difficultyName,
                                score,
                                perfect,
                                shinyPerfect,
                                far,
                                lost,
                                info.constant,
                                playRating,
                                0,
                                info.illustration
                        );
                        list.add(pr);
                    }
                }
                cursor.close();
            }
        } finally {
            db.close();
        }

        Log.i(TAG, "Parsed " + list.size() + " valid scored songs from st3");
        return RatingCalculator.calculateMax50(list);
    }

    /**
     * 读取 CSV 文件（兼容 sample/default.csv 与导出成绩）
     */
    public static B50Summary parseCsvFile(Context context, File csvFile) throws Exception {
        try (InputStream is = new FileInputStream(csvFile)) {
            return parseCsvStream(context, is);
        }
    }

    public static B50Summary parseCsvStream(Context context, InputStream inputStream) throws Exception {
        DataManager dm = DataManager.getInstance(context);
        dm.initialize();

        List<PlayResult> list = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String headerLine = reader.readLine(); // 跳过表头
        if (headerLine == null) {
            throw new Exception("CSV 文件为空");
        }

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split(",", -1);
            if (cols.length >= 8) {
                try {
                    String songName = cols[0].trim();
                    String songId = cols[1].trim();
                    String difficulty = cols[2].trim();
                    int score = Integer.parseInt(cols[3].trim());
                    int perfect = Integer.parseInt(cols[4].trim());
                    int shiny = Integer.parseInt(cols[5].trim());
                    int far = Integer.parseInt(cols[6].trim());
                    int lost = Integer.parseInt(cols[7].trim());
                    double constant = (cols.length >= 9 && !cols[8].trim().isEmpty()) ? Double.parseDouble(cols[8].trim()) : 0.0;

                    int ratingClass = getRatingClassFromDiff(difficulty);
                    DataManager.SongDifficultyInfo info = dm.getSongDifficultyInfo(songId, ratingClass);
                    if (constant <= 0 && info.constant > 0) {
                        constant = info.constant;
                    }
                    if (songName.isEmpty() && info.songName != null) {
                        songName = info.songName;
                    }

                    if (constant > 0) {
                        double playRating = RatingCalculator.calculateSingleRating(score, constant);
                        PlayResult pr = new PlayResult(
                                songName,
                                songId,
                                difficulty,
                                score,
                                perfect,
                                shiny,
                                far,
                                lost,
                                constant,
                                playRating,
                                0,
                                info.illustration
                        );
                        list.add(pr);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Skip invalid CSV line: " + line, e);
                }
            }
        }
        reader.close();
        return RatingCalculator.calculateMax50(list);
    }

    public static int getRatingClassFromDiff(String diff) {
        if ("Past".equalsIgnoreCase(diff)) return 0;
        if ("Present".equalsIgnoreCase(diff)) return 1;
        if ("Future".equalsIgnoreCase(diff)) return 2;
        if ("Beyond".equalsIgnoreCase(diff) || "Inscribed".equalsIgnoreCase(diff)) return 3;
        if ("Eternal".equalsIgnoreCase(diff)) return 4;
        return 2;
    }
}
