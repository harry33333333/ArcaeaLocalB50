package moe.smartrte.arcb50.model;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 玩家个性化设置存储（昵称、好友码、段位框、头像、背景、出图数量等）
 */
public class UserSettings {
    private static final String PREF_NAME = "smartrte_settings";

    private String userName = "Hikari";
    private String userId = "100 000 001";
    private int courseDan = 1; // 0 = 不显示, 1~40 = 对应段位框
    private String avatarName = "34u_icon.webp";
    private String backgroundName = "s9.webp";
    private int unitQuantity = 60; // 默认 50 + 10 = 60
    private boolean showUserId = true;
    private String customAvatarPath = null;
    private String customBgPath = null;

    public static UserSettings load(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        UserSettings s = new UserSettings();
        s.userName = sp.getString("user_name", "Hikari");
        s.userId = sp.getString("user_id", "100 000 001");
        s.courseDan = sp.getInt("course_dan", 1);
        String savedAvatar = sp.getString("avatar_name", "34u_icon.webp");
        if (savedAvatar != null && !savedAvatar.contains("_icon")) {
            savedAvatar = savedAvatar.replace(".webp", "_icon.webp");
        }
        s.avatarName = savedAvatar;
        s.backgroundName = sp.getString("background_name", "s9.webp");
        int q = sp.getInt("unit_quantity", 60);
        if (q < 50) q = 50;
        q = (q / 5) * 5;
        s.unitQuantity = q;
        s.showUserId = sp.getBoolean("show_user_id", true);
        s.customAvatarPath = sp.getString("custom_avatar_path", null);
        s.customBgPath = sp.getString("custom_bg_path", null);
        return s;
    }

    public void save(Context context) {
        SharedPreferences.Editor ed = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        ed.putString("user_name", userName);
        ed.putString("user_id", userId);
        ed.putInt("course_dan", courseDan);
        ed.putString("avatar_name", avatarName);
        ed.putString("background_name", backgroundName);
        if (unitQuantity < 50) unitQuantity = 50;
        unitQuantity = (unitQuantity / 5) * 5;
        ed.putInt("unit_quantity", unitQuantity);
        ed.putBoolean("show_user_id", showUserId);
        ed.putString("custom_avatar_path", customAvatarPath);
        ed.putString("custom_bg_path", customBgPath);
        ed.apply();
    }

    // Getters and Setters
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) {
        if (userId == null) {
            this.userId = "000 000 001";
            return;
        }
        String digits = userId.replaceAll("[^0-9]", "");
        if (digits.length() > 9) digits = digits.substring(0, 9);
        if (digits.length() == 9) {
            this.userId = digits.substring(0, 3) + " " + digits.substring(3, 6) + " " + digits.substring(6, 9);
        } else {
            this.userId = userId;
        }
    }

    public int getCourseDan() { return courseDan; }
    public void setCourseDan(int courseDan) { this.courseDan = courseDan; }

    public String getAvatarName() { return avatarName; }
    public void setAvatarName(String avatarName) { this.avatarName = avatarName; }

    public String getBackgroundName() { return backgroundName; }
    public void setBackgroundName(String backgroundName) { this.backgroundName = backgroundName; }

    public int getUnitQuantity() { return unitQuantity; }
    public void setUnitQuantity(int unitQuantity) {
        if (unitQuantity < 50) unitQuantity = 50;
        this.unitQuantity = (unitQuantity / 5) * 5;
    }

    public boolean isShowUserId() { return showUserId; }
    public void setShowUserId(boolean showUserId) { this.showUserId = showUserId; }

    public String getCustomAvatarPath() { return customAvatarPath; }
    public void setCustomAvatarPath(String customAvatarPath) { this.customAvatarPath = customAvatarPath; }

    public String getCustomBgPath() { return customBgPath; }
    public void setCustomBgPath(String customBgPath) { this.customBgPath = customBgPath; }
}
