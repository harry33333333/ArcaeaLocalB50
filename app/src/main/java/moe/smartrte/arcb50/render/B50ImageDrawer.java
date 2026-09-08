package moe.smartrte.arcb50.render;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import moe.smartrte.arcb50.logic.RatingCalculator;
import moe.smartrte.arcb50.model.B50Summary;
import moe.smartrte.arcb50.model.PlayResult;
import moe.smartrte.arcb50.model.UserSettings;

/**
 * 纯 Java 原生 Canvas 高性能长图绘制引擎
 * 严丝合缝 1:1 对齐 SmartRTE 原版 5 列排版设计（Best 50 + Overflow、Top 10 橙光框、段位框/隐藏、精准坐标）
 */
public class B50ImageDrawer {
    private static final String TAG = "B50ImageDrawer";
    private static final int CANVAS_WIDTH = 1700;

    private final Context context;
    private final AssetManager assets;

    private Typeface fontExo;
    private Typeface fontExoLight;
    private Typeface fontGeosans;
    private Typeface fontGeosansMod;

    // 内存位图缓存
    private final Map<String, Bitmap> bitmapCache = new HashMap<>();

    public B50ImageDrawer(Context context) {
        this.context = context.getApplicationContext();
        this.assets = this.context.getAssets();
        loadFonts();
    }

    private void loadFonts() {
        try {
            fontExo = Typeface.createFromAsset(assets, "Fonts/Exo-SemiBold.ttf");
        } catch (Exception e) {
            fontExo = Typeface.DEFAULT_BOLD;
        }
        try {
            fontExoLight = Typeface.createFromAsset(assets, "Fonts/Exo-Light.ttf");
        } catch (Exception e) {
            fontExoLight = Typeface.DEFAULT;
        }
        try {
            fontGeosans = Typeface.createFromAsset(assets, "Fonts/GeosansLight.ttf");
        } catch (Exception e) {
            fontGeosans = Typeface.DEFAULT;
        }
        try {
            fontGeosansMod = Typeface.createFromAsset(assets, "Fonts/GeosansLight-Modified.otf");
        } catch (Exception e) {
            fontGeosansMod = Typeface.DEFAULT_BOLD;
        }
    }

    public Bitmap drawB50(B50Summary summary, UserSettings settings) {
        long startTime = System.currentTimeMillis();

        boolean isDark = isDarkTheme(settings);

        List<PlayResult> allResults = new ArrayList<>();
        if (summary.getB50List() != null) allResults.addAll(summary.getB50List());
        if (summary.getOverflowList() != null) allResults.addAll(summary.getOverflowList());

        int totalCount = Math.min(settings.getUnitQuantity(), allResults.size());
        int b50Count = Math.min(50, totalCount);
        int overflowCount = Math.max(0, totalCount - 50);

        // 5 列排版：每行 5 张卡片
        int b50Rows = (b50Count + 4) / 5;
        int overflowRows = (overflowCount + 4) / 5;

        int headerHeight = 250;
        int spliterHeight = 70;
        int cardRowHeight = 110; // 卡片高 96 + 间距 14
        int overflowHeaderHeight = (overflowCount > 0) ? 70 : 0;
        int footerHeight = 65;

        int totalHeight = headerHeight + spliterHeight + (b50Rows * cardRowHeight) + overflowHeaderHeight + (overflowRows * cardRowHeight) + footerHeight;

        Bitmap bitmap = Bitmap.createBitmap(CANVAS_WIDTH, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 1. 绘制背景图与半透明保护层
        drawBackground(canvas, totalHeight, settings, isDark);

        // 2. 绘制顶部玩家信息与统计
        drawHeader(canvas, summary, settings);

        // 3. 绘制 Best 50 分割标线
        int b50SpliterY = headerHeight;
        drawSpliter(canvas, b50SpliterY, "img/best50.png");

        // 4. 绘制前 50 张卡片 (5 列网格，每张 300px x 96px)
        int startY = b50SpliterY + spliterHeight;
        drawCards(canvas, allResults.subList(0, b50Count), startY, 0, isDark);

        // 5. 绘制 Overflow 卡片 (默认 50+10，共 60 首)
        if (overflowCount > 0) {
            int overflowY = startY + (b50Rows * cardRowHeight);
            drawSpliter(canvas, overflowY, "img/overflow.png");
            int overflowCardsY = overflowY + overflowHeaderHeight;
            drawCards(canvas, allResults.subList(50, totalCount), overflowCardsY, 50, isDark);
        }

        // 6. 绘制底部版权信息与时间戳
        drawFooter(canvas, totalHeight);

        Log.i(TAG, "5-column B50 image drawn in " + (System.currentTimeMillis() - startTime) + " ms, height=" + totalHeight + ", isDark=" + isDark);
        return bitmap;
    }

    public boolean isDarkTheme(UserSettings settings) {
        String it = settings.getImageTheme();
        if ("dark".equalsIgnoreCase(it)) {
            return true;
        }
        if ("light".equalsIgnoreCase(it)) {
            return false;
        }
        // "follow" -> 跟随应用主题
        String at = settings.getAppTheme();
        if ("dark".equalsIgnoreCase(at)) {
            return true;
        }
        if ("light".equalsIgnoreCase(at)) {
            return false;
        }
        // "system" -> 跟随系统
        int nightModeFlags = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void drawBackground(Canvas canvas, int height, UserSettings settings, boolean isDark) {
        Bitmap bgBmp = null;
        if (settings.getCustomBgPath() != null) {
            File f = new File(settings.getCustomBgPath());
            if (f.exists()) {
                bgBmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            }
        }
        if (bgBmp == null) {
            bgBmp = loadAssetBitmap("bgs/" + settings.getBackgroundName());
        }
        if (bgBmp == null) {
            bgBmp = loadAssetBitmap("bgs/s9.webp");
        }
        if (bgBmp == null) {
            bgBmp = loadAssetBitmap("bgs/1.webp");
        }

        if (bgBmp != null) {
            float scale = Math.max((float) CANVAS_WIDTH / bgBmp.getWidth(), (float) height / bgBmp.getHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate((CANVAS_WIDTH - bgBmp.getWidth() * scale) / 2f, (height - bgBmp.getHeight() * scale) / 2f);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(bgBmp, matrix, p);
        } else {
            canvas.drawColor(isDark ? Color.parseColor("#151324") : Color.parseColor("#3A374A"));
        }

        // 半透明暗色覆盖层，保证前景文字高可读性
        Paint dimPaint = new Paint();
        dimPaint.setColor(isDark ? Color.parseColor("#8A0D0C18") : Color.parseColor("#500D0C18"));
        canvas.drawRect(0, 0, CANVAS_WIDTH, height, dimPaint);
    }

    private void drawHeader(Canvas canvas, B50Summary summary, UserSettings settings) {
        // --- 1. 头像（菱形） ---
        String avatarName = settings.getAvatarName();
        if (avatarName == null || avatarName.isEmpty()) {
            avatarName = "34u_icon.webp";
        }
        if (!avatarName.contains("_icon")) {
            avatarName = avatarName.replace(".webp", "_icon.webp");
        }

        Bitmap avatarBmp = null;
        if (settings.getCustomAvatarPath() != null) {
            File f = new File(settings.getCustomAvatarPath());
            if (f.exists()) {
                avatarBmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            }
        }
        if (avatarBmp == null) {
            avatarBmp = loadAssetBitmap("img/avatar/" + avatarName);
        }
        if (avatarBmp == null) {
            avatarBmp = loadAssetBitmap("img/avatar/34u_icon.webp");
        }
        if (avatarBmp == null) {
            avatarBmp = loadAssetBitmap("img/avatar/0_icon.webp");
        }

        int avatarX = 65;
        int avatarY = 35;
        int avatarSize = 175;
        int cx = avatarX + avatarSize / 2;
        int cy = avatarY + avatarSize / 2;

        // 菱形裁剪路径
        Path diamondPath = new Path();
        diamondPath.moveTo(cx, avatarY);
        diamondPath.lineTo(avatarX + avatarSize, cy);
        diamondPath.lineTo(cx, avatarY + avatarSize);
        diamondPath.lineTo(avatarX, cy);
        diamondPath.close();

        // 绘制头像背后的柔光 blur
        Bitmap blurBmp = loadAssetBitmap("img/blur.png");
        if (blurBmp != null) {
            Matrix bm = new Matrix();
            float bScale = (float) (avatarSize * 1.35) / blurBmp.getWidth();
            bm.setScale(bScale, bScale);
            bm.postTranslate(cx - (blurBmp.getWidth() * bScale) / 2f, cy - (blurBmp.getHeight() * bScale) / 2f);
            Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG);
            bp.setAlpha(190);
            canvas.drawBitmap(blurBmp, bm, bp);
        }

        // 绘制菱形头像
        if (avatarBmp != null) {
            canvas.save();
            canvas.clipPath(diamondPath);
            Rect src = new Rect(0, 0, avatarBmp.getWidth(), avatarBmp.getHeight());
            Rect dst = new Rect(avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize);
            Paint ap = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            canvas.drawBitmap(avatarBmp, src, dst, ap);
            canvas.restore();
        }

        // 菱形边框（金属紫银光芒）
        Paint diamondBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        diamondBorder.setStyle(Paint.Style.STROKE);
        diamondBorder.setStrokeWidth(5f);
        diamondBorder.setColor(Color.parseColor("#E0C0E8"));
        diamondBorder.setShadowLayer(8f, 0, 0, Color.parseColor("#AA7A3090"));
        canvas.drawPath(diamondPath, diamondBorder);

        // --- 2. 潜力值徽章 (Hexagon Rating Badge) ---
        int pttX = avatarX + 100;
        int pttY = avatarY + 100;
        int pttSize = 90;

        String frameImgName = RatingCalculator.getPotentialFrameImage(summary.getMaxPtt());
        Bitmap frameBmp = loadAssetBitmap("img/rating/" + frameImgName);
        if (frameBmp != null) {
            Rect src = new Rect(0, 0, frameBmp.getWidth(), frameBmp.getHeight());
            Rect dst = new Rect(pttX, pttY, pttX + pttSize, pttY + pttSize);
            Paint fp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            canvas.drawBitmap(frameBmp, src, dst, fp);
        }

        // 潜力值数值文字（三位小数对齐原版）
        Paint pttTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pttTextPaint.setTypeface(fontExo);
        pttTextPaint.setTextSize(24f);
        pttTextPaint.setColor(Color.WHITE);
        pttTextPaint.setTextAlign(Paint.Align.CENTER);
        pttTextPaint.setShadowLayer(5f, 0, 0, Color.parseColor("#5A005A"));
        String pttStr = String.format(Locale.US, "%.3f", summary.getMaxPtt());
        canvas.drawText(pttStr, pttX + pttSize / 2f, pttY + pttSize / 2f + 8f, pttTextPaint);

        // --- 3. 玩家名称与段位框 (Course Dan 0~40) ---
        int danIndex = settings.getCourseDan();
        int bannerX = 270;
        int bannerY = 48;
        int bannerW = 460;
        int bannerH = 72;

        if (danIndex > 0 && danIndex <= 40) {
            Bitmap danBmp = loadAssetBitmap("img/course/" + danIndex + ".png");
            if (danBmp != null) {
                Rect src = new Rect(0, 0, danBmp.getWidth(), danBmp.getHeight());
                Rect dst = new Rect(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH);
                Paint dp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                dp.setAlpha(240);
                canvas.drawBitmap(danBmp, src, dst, dp);
            }
        }

        // 玩家名称
        Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setTypeface(fontGeosansMod);
        namePaint.setTextSize(46f);
        namePaint.setFakeBoldText(true);
        namePaint.setColor(Color.WHITE);
        namePaint.setShadowLayer(8f, 2, 2, Color.parseColor("#000000"));
        canvas.drawText(settings.getUserName(), bannerX + 25, bannerY + 52, namePaint);

        // 好友 ID
        if (settings.isShowUserId()) {
            Paint idPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            idPaint.setTypeface(fontGeosans);
            idPaint.setTextSize(32f);
            idPaint.setFakeBoldText(true);
            idPaint.setColor(Color.parseColor("#E0E0FF"));
            idPaint.setShadowLayer(6f, 2, 2, Color.parseColor("#000000"));
            canvas.drawText("ID : " + settings.getUserId(), bannerX + 25, bannerY + 115, idPaint);
        }

        // --- 4. 右上角 PTT 统计 (Max, B50, B10) ---
        int statRightX = 1635;
        Paint statLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        statLabelPaint.setTypeface(fontGeosans);
        statLabelPaint.setTextSize(44f);
        statLabelPaint.setFakeBoldText(true);
        statLabelPaint.setColor(Color.WHITE);
        statLabelPaint.setTextAlign(Paint.Align.RIGHT);
        statLabelPaint.setShadowLayer(8f, 2, 2, Color.parseColor("#000000"));

        canvas.drawText("Max : " + summary.getFormattedMaxPtt(), statRightX, 80, statLabelPaint);
        canvas.drawText("B50 : " + summary.getFormattedB50Ptt(), statRightX, 135, statLabelPaint);
        canvas.drawText("B10 : " + summary.getFormattedB10Ptt(), statRightX, 190, statLabelPaint);
    }

    private void drawSpliter(Canvas canvas, int y, String centerImagePath) {
        Bitmap dividerBmp = loadAssetBitmap("img/divider.png");
        if (dividerBmp != null) {
            Rect src = new Rect(0, 0, dividerBmp.getWidth(), dividerBmp.getHeight());
            Rect dst = new Rect(0, y + 16, CANVAS_WIDTH, y + 50);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setAlpha(220);
            canvas.drawBitmap(dividerBmp, src, dst, p);
        }

        Bitmap centerBmp = loadAssetBitmap(centerImagePath);
        if (centerBmp != null) {
            int w = 330;
            int h = (int) (centerBmp.getHeight() * ((float) w / centerBmp.getWidth()));
            int x = (CANVAS_WIDTH - w) / 2;
            Rect src = new Rect(0, 0, centerBmp.getWidth(), centerBmp.getHeight());
            Rect dst = new Rect(x, y + 2, x + w, y + 2 + h);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            canvas.drawBitmap(centerBmp, src, dst, p);
        }
    }

    /**
     * 绘制成绩卡片（5 列网格，每张 300px x 96px，间隙水平 24px，垂直 14px，左右边距 52px）
     */
    private void drawCards(Canvas canvas, List<PlayResult> results, int startY, int rankOffset, boolean isDark) {
        int cardWidth = 300;
        int cardHeight = 96;
        int gapX = 24;
        int gapY = 14;
        int marginX = 52;

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // SmartRTE 原版: light 为浅亮色 #F4F8FC，dark 为深紫暗黑 rgba(25, 22, 40, 0.92) -> #EA191628
        bgPaint.setColor(isDark ? Color.parseColor("#EA191628") : Color.parseColor("#F4F8FC"));

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);

        int normalBorderColor = isDark ? Color.parseColor("#507864A0") : Color.parseColor("#B0B4C0");
        int titleColor = isDark ? Color.parseColor("#F4F0FF") : Color.parseColor("#101018");
        int titleShadowColor = isDark ? Color.parseColor("#907864A0") : Color.parseColor("#80B0B0C0");
        int normalScoreColor = isDark ? Color.parseColor("#FFFFFF") : Color.parseColor("#151325");
        int normalScoreShadowColor = isDark ? Color.parseColor("#607864A0") : Color.parseColor("#9090A0");
        int pmScoreColor = isDark ? Color.parseColor("#00E5FF") : Color.parseColor("#00B0C8");
        int pmScoreShadowColor = isDark ? Color.parseColor("#00E5FF") : Color.parseColor("#00D2D2");

        int pureColor = isDark ? Color.rgb(210, 180, 240) : Color.parseColor("#643D64");
        int farColor = isDark ? Color.rgb(245, 225, 100) : Color.parseColor("#9A7B00");
        int lostColor = isDark ? Color.rgb(230, 120, 140) : Color.parseColor("#A63349");

        int defaultRankBg = isDark ? Color.parseColor("#342F4C") : Color.parseColor("#D4D8DF");
        int defaultRankText = isDark ? Color.parseColor("#EAE6F8") : Color.parseColor("#252332");

        for (int i = 0; i < results.size(); i++) {
            PlayResult pr = results.get(i);
            int row = i / 5;
            int col = i % 5;

            int cardX = marginX + col * (cardWidth + gapX);
            int cardY = startY + row * (cardHeight + gapY);
            int rankNum = rankOffset + i + 1;

            RectF cardRect = new RectF(cardX, cardY, cardX + cardWidth, cardY + cardHeight);

            // 1. 卡片背景
            canvas.drawRoundRect(cardRect, 5f, 5f, bgPaint);

            // 2. 卡片边框（Top 10: 2px solid rgba(255, 140, 0, 0.95)，外带发光光晕）
            if (rankNum <= 10) {
                borderPaint.setStrokeWidth(2.5f);
                borderPaint.setColor(Color.parseColor("#FFF58200"));
                borderPaint.setShadowLayer(8f, 0, 0, Color.parseColor("#99FF8C00"));
                canvas.drawRoundRect(cardRect, 5f, 5f, borderPaint);
                borderPaint.setShadowLayer(0, 0, 0, 0); // 清除阴影
            } else {
                borderPaint.setStrokeWidth(1f);
                borderPaint.setColor(normalBorderColor);
                canvas.drawRoundRect(cardRect, 5f, 5f, borderPaint);
            }

            // 3. 曲绘封图 (90px x 90px 圆角)
            int illSize = 90;
            int illX = cardX + 3;
            int illY = cardY + 3;
            Bitmap jacketBmp = loadAssetBitmap("Processed_Illustration/" + pr.getIllustration());
            if (jacketBmp == null) {
                jacketBmp = loadAssetBitmap("Processed_Illustration/sayonarahatsukoi.jpg");
            }

            if (jacketBmp != null) {
                canvas.save();
                Path illPath = new Path();
                illPath.addRoundRect(new RectF(illX, illY, illX + illSize, illY + illSize), 4f, 4f, Path.Direction.CW);
                canvas.clipPath(illPath);
                Rect src = new Rect(0, 0, jacketBmp.getWidth(), jacketBmp.getHeight());
                Rect dst = new Rect(illX, illY, illX + illSize, illY + illSize);
                Paint jp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                canvas.drawBitmap(jacketBmp, src, dst, jp);
                canvas.restore();
            }

            // 4. 顶部信息条 (Difficulty & Constant & Play Rating & Rank)
            int barX = cardX + 96;
            int barY = cardY + 3;
            int barH = 18;

            int ratingW = 54;
            int diffW = 107;
            int rankW = 40;

            // Rating Pill (左圆角)
            RectF ratingRect = new RectF(barX, barY, barX + ratingW, barY + barH);
            Paint ratingBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ratingBgPaint.setColor(getDiffLightColor(pr.getDifficulty()));
            canvas.drawRoundRect(ratingRect, 3f, 3f, ratingBgPaint);

            Paint ratingTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ratingTextPaint.setTypeface(fontExo);
            ratingTextPaint.setTextSize(10.5f);
            ratingTextPaint.setColor(Color.WHITE);
            ratingTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(pr.getFormattedPlayRating(), barX + ratingW / 2f, barY + 13f, ratingTextPaint);

            // Diff + Constant Pill (中间)
            int diffX = barX + ratingW;
            RectF diffRect = new RectF(diffX, barY, diffX + diffW, barY + barH);
            Paint diffBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            diffBgPaint.setColor(getDiffDarkColor(pr.getDifficulty()));
            canvas.drawRect(diffRect, diffBgPaint);

            Paint diffTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            diffTextPaint.setTypeface(fontExo);
            diffTextPaint.setTextSize(9.5f);
            diffTextPaint.setColor(Color.WHITE);
            diffTextPaint.setTextAlign(Paint.Align.CENTER);
            String diffConstStr = pr.getDifficulty() + " [" + pr.getFormattedConstant() + "]";
            canvas.drawText(diffConstStr, diffX + diffW / 2f, barY + 13f, diffTextPaint);

            // Rank # Pill (右圆角)
            int rkX = diffX + diffW;
            RectF rankRect = new RectF(rkX, barY, rkX + rankW, barY + barH);
            Paint rankBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            int rankTextColor = Color.WHITE;
            if (rankNum == 1) {
                rankBgPaint.setColor(Color.parseColor("#FFD700"));
                rankTextColor = Color.parseColor("#151322");
            } else if (rankNum == 2) {
                rankBgPaint.setColor(Color.parseColor("#708090"));
            } else if (rankNum == 3) {
                rankBgPaint.setColor(Color.parseColor("#8B4513"));
            } else {
                rankBgPaint.setColor(defaultRankBg);
                rankTextColor = defaultRankText;
            }
            canvas.drawRoundRect(rankRect, 3f, 3f, rankBgPaint);

            Paint rankNumTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            rankNumTextPaint.setTypeface(fontExo);
            rankNumTextPaint.setTextSize(11f);
            rankNumTextPaint.setColor(rankTextColor);
            rankNumTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("#" + rankNum, rkX + rankW / 2f, barY + 13f, rankNumTextPaint);

            // 5. 曲名 (单行自适应截断)
            Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setTypeface(fontExo);
            titlePaint.setTextSize(14f);
            titlePaint.setColor(titleColor);
            titlePaint.setShadowLayer(isDark ? 2.5f : 1.5f, 1, 1, titleShadowColor);

            String title = pr.getSongName();
            float maxTitleW = 138f;
            if (titlePaint.measureText(title) > maxTitleW) {
                while (title.length() > 0 && titlePaint.measureText(title + "...") > maxTitleW) {
                    title = title.substring(0, title.length() - 1);
                }
                title = title + "...";
            }
            canvas.drawText(title, cardX + 98, cardY + 36, titlePaint);

            // 6. 得分 (大数字 GeosansLightModified，8位对齐带前导0)
            Paint scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            scorePaint.setTypeface(fontGeosansMod);
            scorePaint.setTextSize(25f);
            scorePaint.setFakeBoldText(true);

            boolean isPM = (pr.getScore() >= 10000000 && pr.getFar() == 0 && pr.getLost() == 0);
            if (isPM) {
                scorePaint.setColor(pmScoreColor);
                scorePaint.setShadowLayer(4f, 0, 0, pmScoreShadowColor);
            } else {
                scorePaint.setColor(normalScoreColor);
                scorePaint.setShadowLayer(isDark ? 2f : 1.5f, 1, 1, normalScoreShadowColor);
            }
            canvas.drawText(pr.getFormattedScore(), cardX + 97, cardY + 65, scorePaint);

            // 7. 物量 (Pure / Far / Lost)
            Paint itemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            itemPaint.setTypeface(fontExo);
            itemPaint.setTextSize(10.5f);

            int itemY = cardY + 86;
            itemPaint.setColor(pureColor);
            String pStr = "P/" + pr.getPerfect() + "(-" + Math.abs(pr.getNormalPerfect()) + ")";
            canvas.drawText(pStr, cardX + 98, itemY, itemPaint);
            float pWidth = itemPaint.measureText(pStr);

            itemPaint.setColor(farColor);
            String fStr = "F/" + pr.getFar();
            float fX = cardX + 98 + pWidth + 8;
            canvas.drawText(fStr, fX, itemY, itemPaint);
            float fWidth = itemPaint.measureText(fStr);

            itemPaint.setColor(lostColor);
            String lStr = "L/" + pr.getLost();
            canvas.drawText(lStr, fX + fWidth + 8, itemY, itemPaint);

            // 8. 成绩徽章 (PM, EX+, EX, AA 等)
            Bitmap rankBmp = loadAssetBitmap("img/rank/" + pr.getRank() + ".png");
            if (rankBmp != null) {
                int badgeW = 60;
                int badgeH = 60;
                int badgeX = cardX + 237;
                int badgeY = cardY + 28;
                Rect src = new Rect(0, 0, rankBmp.getWidth(), rankBmp.getHeight());
                Rect dst = new Rect(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH);
                Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                canvas.drawBitmap(rankBmp, src, dst, bp);
            }
        }
    }

    private void drawFooter(Canvas canvas, int height) {
        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setTypeface(fontGeosans);
        footerPaint.setTextSize(26f);
        footerPaint.setColor(Color.parseColor("#D0D0E8"));
        footerPaint.setTextAlign(Paint.Align.CENTER);
        footerPaint.setShadowLayer(4f, 1, 1, Color.BLACK);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
        String copyright = "Generated At " + sdf.format(new Date());
        canvas.drawText(copyright, CANVAS_WIDTH / 2f, height - 25, footerPaint);
    }

    private int getDiffLightColor(String diff) {
        if ("Past".equalsIgnoreCase(diff)) return Color.parseColor("#0085C8");
        if ("Present".equalsIgnoreCase(diff)) return Color.parseColor("#008200");
        if ("Future".equalsIgnoreCase(diff)) return Color.parseColor("#8A4875");
        if ("Beyond".equalsIgnoreCase(diff)) return Color.parseColor("#BF2941");
        if ("Eternal".equalsIgnoreCase(diff)) return Color.parseColor("#D08BD0");
        if ("Inscribed".equalsIgnoreCase(diff)) return Color.parseColor("#6D28D9");
        return Color.parseColor("#8A4875");
    }

    private int getDiffDarkColor(String diff) {
        if ("Past".equalsIgnoreCase(diff)) return Color.parseColor("#0042C8");
        if ("Present".equalsIgnoreCase(diff)) return Color.parseColor("#005A00");
        if ("Future".equalsIgnoreCase(diff)) return Color.parseColor("#6E3A60");
        if ("Beyond".equalsIgnoreCase(diff)) return Color.parseColor("#962336");
        if ("Eternal".equalsIgnoreCase(diff)) return Color.parseColor("#A269A2");
        if ("Inscribed".equalsIgnoreCase(diff)) return Color.parseColor("#4C1D95");
        return Color.parseColor("#6E3A60");
    }

    private Bitmap loadAssetBitmap(String path) {
        if (bitmapCache.containsKey(path)) {
            return bitmapCache.get(path);
        }
        try (InputStream is = assets.open(path)) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp != null) {
                bitmapCache.put(path, bmp);
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }
}
