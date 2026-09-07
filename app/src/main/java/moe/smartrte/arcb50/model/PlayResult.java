package moe.smartrte.arcb50.model;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.Locale;

/**
 * 封装单次打歌成绩数据模型（支持 8 位分数带前导 0 规范化，支持 JSON 序列化持久化）
 */
public class PlayResult implements Serializable {
    private String songName;
    private String songId;
    private String difficulty; // Past, Present, Future, Beyond, Eternal, Inscribed
    private int score;
    private int perfect;
    private int criticalPerfect;
    private int normalPerfect;
    private int far;
    private int lost;
    private double constant;
    private double playRating;
    private int innerIndex;
    private String illustration;
    private String rank; // PM, EX+, EX, AA, A, B, C, D
    private int loseScore;
    private double percentage;
    private int objectAmount;

    public PlayResult() {
    }

    public PlayResult(String songName, String songId, String difficulty, int score,
                      int perfect, int criticalPerfect, int far, int lost,
                      double constant, double playRating, int innerIndex,
                      String illustration) {
        this.songName = songName;
        this.songId = songId;
        this.difficulty = difficulty;
        this.score = score;
        this.perfect = perfect;
        this.criticalPerfect = criticalPerfect;
        this.normalPerfect = Math.max(0, perfect - criticalPerfect);
        this.far = far;
        this.lost = lost;
        this.constant = constant;
        this.playRating = playRating;
        this.innerIndex = innerIndex;
        this.illustration = (illustration != null && !illustration.isEmpty()) ? illustration : (songId + ".jpg");
        this.objectAmount = perfect + far + lost;
        this.rank = computeRank(score, far, lost);
        this.loseScore = computeLoseScore(constant, score, objectAmount, criticalPerfect);
        if (constant > 0) {
            this.percentage = Math.max(0, (constant * 38.0 - this.loseScore) / (constant * 38.0) * 100.0);
        }
    }

    private static String computeRank(int score, int far, int lost) {
        if (score >= 10000000 && far == 0 && lost == 0) return "PM";
        if (score >= 9900000) return "EX+";
        if (score >= 9800000) return "EX";
        if (score >= 9500000) return "AA";
        if (score >= 9200000) return "A";
        if (score >= 8900000) return "B";
        if (score >= 8600000) return "C";
        return "D";
    }

    private static int computeLoseScore(double constant, int score, int totalNotes, int shiny) {
        if (totalNotes <= 0) return 0;
        int maxPossible = 10000000 + totalNotes;
        int maxReal = score + shiny;
        return Math.max(0, maxPossible - maxReal);
    }

    /**
     * 规范化 8 位格式化分数（带前导 0，例如 09'995'961 或 10'001'225）
     */
    public String getFormattedScore() {
        String s = String.format(Locale.US, "%08d", score);
        if (s.length() < 8) {
            s = ("00000000" + s).substring(s.length());
        }
        return s.substring(0, 2) + "'" + s.substring(2, 5) + "'" + s.substring(5);
    }

    public String getFormattedPlayRating() {
        return String.format(Locale.US, "%.4f", playRating);
    }

    public String getFormattedConstant() {
        return String.format(Locale.US, "%.1f", constant);
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("songName", songName);
            o.put("songId", songId);
            o.put("difficulty", difficulty);
            o.put("score", score);
            o.put("perfect", perfect);
            o.put("criticalPerfect", criticalPerfect);
            o.put("normalPerfect", normalPerfect);
            o.put("far", far);
            o.put("lost", lost);
            o.put("constant", constant);
            o.put("playRating", playRating);
            o.put("innerIndex", innerIndex);
            o.put("illustration", illustration);
            o.put("rank", rank);
            o.put("loseScore", loseScore);
            o.put("percentage", percentage);
            o.put("objectAmount", objectAmount);
        } catch (Exception ignored) {}
        return o;
    }

    public static PlayResult fromJson(JSONObject o) {
        if (o == null) return null;
        PlayResult r = new PlayResult();
        r.songName = o.optString("songName", "");
        r.songId = o.optString("songId", "");
        r.difficulty = o.optString("difficulty", "Future");
        r.score = o.optInt("score", 0);
        r.perfect = o.optInt("perfect", 0);
        r.criticalPerfect = o.optInt("criticalPerfect", 0);
        r.normalPerfect = o.optInt("normalPerfect", 0);
        r.far = o.optInt("far", 0);
        r.lost = o.optInt("lost", 0);
        r.constant = o.optDouble("constant", 0.0);
        r.playRating = o.optDouble("playRating", 0.0);
        r.innerIndex = o.optInt("innerIndex", 0);
        r.illustration = o.optString("illustration", r.songId + ".jpg");
        r.rank = o.optString("rank", computeRank(r.score, r.far, r.lost));
        r.loseScore = o.optInt("loseScore", 0);
        r.percentage = o.optDouble("percentage", 0.0);
        r.objectAmount = o.optInt("objectAmount", r.perfect + r.far + r.lost);
        return r;
    }

    // Getters and Setters
    public String getSongName() { return songName; }
    public void setSongName(String songName) { this.songName = songName; }

    public String getSongId() { return songId; }
    public void setSongId(String songId) { this.songId = songId; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getPerfect() { return perfect; }
    public void setPerfect(int perfect) { this.perfect = perfect; }

    public int getCriticalPerfect() { return criticalPerfect; }
    public void setCriticalPerfect(int criticalPerfect) { this.criticalPerfect = criticalPerfect; }

    public int getNormalPerfect() { return normalPerfect; }
    public void setNormalPerfect(int normalPerfect) { this.normalPerfect = normalPerfect; }

    public int getFar() { return far; }
    public void setFar(int far) { this.far = far; }

    public int getLost() { return lost; }
    public void setLost(int lost) { this.lost = lost; }

    public double getConstant() { return constant; }
    public void setConstant(double constant) { this.constant = constant; }

    public double getPlayRating() { return playRating; }
    public void setPlayRating(double playRating) { this.playRating = playRating; }

    public int getInnerIndex() { return innerIndex; }
    public void setInnerIndex(int innerIndex) { this.innerIndex = innerIndex; }

    public String getIllustration() { return illustration; }
    public void setIllustration(String illustration) { this.illustration = illustration; }

    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }

    public int getLoseScore() { return loseScore; }
    public void setLoseScore(int loseScore) { this.loseScore = loseScore; }

    public double getPercentage() { return percentage; }
    public void setPercentage(double percentage) { this.percentage = percentage; }

    public int getObjectAmount() { return objectAmount; }
    public void setObjectAmount(int objectAmount) { this.objectAmount = objectAmount; }
}
