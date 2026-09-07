package moe.smartrte.arcb50.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import moe.smartrte.arcb50.model.B50Summary;
import moe.smartrte.arcb50.model.PlayResult;

/**
 * Arcaea 单曲PTT与B50算法计算核心（严格遵循 7.0.0 规范与仓库算法）
 */
public class RatingCalculator {

    /**
     * 计算单曲潜力值
     * @param score 分数
     * @param constant 定数
     * @return 单曲潜力值
     */
    public static double calculateSingleRating(int score, double constant) {
        if (constant <= 0 || score <= 0) return 0.0;
        double rt;
        if (score >= 10000000) {
            rt = constant + 2.0;
        } else if (score > 9800000) {
            rt = constant + 1.0 + (score - 9800000.0) / 200000.0;
        } else {
            rt = constant + (score - 9500000.0) / 300000.0;
            rt = Math.max(0.0, rt);
        }
        // 7.0.0 起：达到 7,000,000 分（通关）最终单曲潜力值 +0.2
        if (score >= 7000000) {
            rt += 0.2;
        }
        return rt;
    }

    /**
     * 7.0.0 新版 B50 统计计算：
     * best10: 前10单曲PTT平均值
     * best50: 前50单曲PTT平均值
     * max50: (best50总和 + best10总和) / 60
     * @param results 所有有效成绩
     * @return 封装好的 B50Summary
     */
    public static B50Summary calculateMax50(List<PlayResult> results) {
        if (results == null || results.isEmpty()) {
            return new B50Summary(0.0, 0.0, 0.0, new ArrayList<>(), new ArrayList<>());
        }

        // 排序：先按 playRating 降序；若相同按 score 降序
        List<PlayResult> sorted = new ArrayList<>(results);
        Collections.sort(sorted, new Comparator<PlayResult>() {
            @Override
            public int compare(PlayResult a, PlayResult b) {
                int cmp = Double.compare(b.getPlayRating(), a.getPlayRating());
                if (cmp != 0) return cmp;
                return Integer.compare(b.getScore(), a.getScore());
            }
        });

        // 重新编号 1-based innerIndex
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setInnerIndex(i + 1);
        }

        int count = sorted.size();
        int n50 = Math.min(50, count);
        int n10 = Math.min(10, count);

        double sum10 = 0.0;
        double sum50 = 0.0;

        for (int i = 0; i < n50; i++) {
            double p = sorted.get(i).getPlayRating();
            sum50 += p;
            if (i < n10) {
                sum10 += p;
            }
        }

        double b10 = (n10 > 0) ? (sum10 / 10.0) : 0.0;
        double b50 = (n50 > 0) ? (sum50 / 50.0) : 0.0;
        double maxPtt = (sum50 + sum10) / 60.0;

        List<PlayResult> b50List = new ArrayList<>(sorted.subList(0, n50));
        List<PlayResult> overflowList = (count > 50) ? new ArrayList<>(sorted.subList(50, count)) : new ArrayList<>();

        return new B50Summary(maxPtt, b50Ptt(b50), b10, b50List, overflowList);
    }

    private static double b50Ptt(double v) {
        return v;
    }

    /**
     * 根据 Max PTT 获取对应的段位框图片资源名
     */
    public static String getPotentialFrameImage(double potential) {
        double[] ranges = {3.49, 6.99, 9.99, 10.99, 11.99, 12.49, 12.99, 13.5};
        int[] frames = {0, 1, 2, 3, 4, 5, 6, 8};
        for (int i = 0; i < ranges.length; i++) {
            if (potential <= ranges[i]) {
                return "rating_" + frames[i] + ".png";
            }
        }
        return "rating_8.png";
    }
}
