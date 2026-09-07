package moe.smartrte.arcb50.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 汇总 B50 计算结果：Max PTT, B50 PTT, B10 PTT 及曲目清单（支持 JSON 持久化与离线缓存）
 */
public class B50Summary implements Serializable {
    private double maxPtt;
    private double b50Ptt;
    private double b10Ptt;
    private List<PlayResult> b50List = new ArrayList<>();
    private List<PlayResult> overflowList = new ArrayList<>();

    public B50Summary() {}

    public B50Summary(double maxPtt, double b50Ptt, double b10Ptt, List<PlayResult> b50List, List<PlayResult> overflowList) {
        this.maxPtt = maxPtt;
        this.b50Ptt = b50Ptt;
        this.b10Ptt = b10Ptt;
        this.b50List = (b50List != null) ? b50List : new ArrayList<>();
        this.overflowList = (overflowList != null) ? overflowList : new ArrayList<>();
    }

    public String getFormattedMaxPtt() {
        return String.format(Locale.US, "%.4f", maxPtt);
    }

    public String getFormattedB50Ptt() {
        return String.format(Locale.US, "%.4f", b50Ptt);
    }

    public String getFormattedB10Ptt() {
        return String.format(Locale.US, "%.4f", b10Ptt);
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("maxPtt", maxPtt);
            obj.put("b50Ptt", b50Ptt);
            obj.put("b10Ptt", b10Ptt);

            JSONArray b50Arr = new JSONArray();
            if (b50List != null) {
                for (PlayResult pr : b50List) {
                    b50Arr.put(pr.toJson());
                }
            }
            obj.put("b50List", b50Arr);

            JSONArray overArr = new JSONArray();
            if (overflowList != null) {
                for (PlayResult pr : overflowList) {
                    overArr.put(pr.toJson());
                }
            }
            obj.put("overflowList", overArr);
        } catch (Exception ignored) {}
        return obj;
    }

    public static B50Summary fromJson(JSONObject obj) {
        if (obj == null) return null;
        B50Summary summary = new B50Summary();
        summary.maxPtt = obj.optDouble("maxPtt", 0.0);
        summary.b50Ptt = obj.optDouble("b50Ptt", 0.0);
        summary.b10Ptt = obj.optDouble("b10Ptt", 0.0);

        JSONArray b50Arr = obj.optJSONArray("b50List");
        if (b50Arr != null) {
            for (int i = 0; i < b50Arr.length(); i++) {
                JSONObject item = b50Arr.optJSONObject(i);
                if (item != null) summary.b50List.add(PlayResult.fromJson(item));
            }
        }

        JSONArray overArr = obj.optJSONArray("overflowList");
        if (overArr != null) {
            for (int i = 0; i < overArr.length(); i++) {
                JSONObject item = overArr.optJSONObject(i);
                if (item != null) summary.overflowList.add(PlayResult.fromJson(item));
            }
        }

        return summary;
    }

    public double getMaxPtt() { return maxPtt; }
    public void setMaxPtt(double maxPtt) { this.maxPtt = maxPtt; }

    public double getB50Ptt() { return b50Ptt; }
    public void setB50Ptt(double b50Ptt) { this.b50Ptt = b50Ptt; }

    public double getB10Ptt() { return b10Ptt; }
    public void setB10Ptt(double b10Ptt) { this.b10Ptt = b10Ptt; }

    public List<PlayResult> getB50List() { return b50List; }
    public void setB50List(List<PlayResult> b50List) { this.b50List = b50List; }

    public List<PlayResult> getOverflowList() { return overflowList; }
    public void setOverflowList(List<PlayResult> overflowList) { this.overflowList = overflowList; }
}
