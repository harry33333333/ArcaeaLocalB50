package moe.smartrte.arcb50.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import moe.smartrte.arcb50.R;
import moe.smartrte.arcb50.model.PlayResult;

/**
 * 顺滑的原生单曲成绩列表适配器（采用 LruCache 与采样率下采样优化，彻底杜绝主线程卡顿与 ANR）
 */
public class SongListAdapter extends RecyclerView.Adapter<SongListAdapter.SongViewHolder> {
    private final Context context;
    private List<PlayResult> items = new ArrayList<>();

    // 内存 LruCache（限制最多缓存 120 张缩略图）
    private static final LruCache<String, Bitmap> imageCache = new LruCache<>(120);
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SongListAdapter(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setData(List<PlayResult> list) {
        this.items = (list != null) ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song_card, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final SongViewHolder holder, int position) {
        PlayResult item = items.get(position);
        int rankNum = position + 1;

        holder.tvRankNum.setText("#" + rankNum);
        if (rankNum == 1) {
            holder.tvRankNum.setBackgroundColor(Color.parseColor("#FFD700"));
            holder.tvRankNum.setTextColor(Color.parseColor("#151322"));
        } else if (rankNum == 2) {
            holder.tvRankNum.setBackgroundColor(Color.parseColor("#C0C0C8"));
            holder.tvRankNum.setTextColor(Color.parseColor("#151322"));
        } else if (rankNum == 3) {
            holder.tvRankNum.setBackgroundColor(Color.parseColor("#CD7F32"));
            holder.tvRankNum.setTextColor(Color.parseColor("#FFFFFF"));
        } else {
            holder.tvRankNum.setBackgroundColor(Color.parseColor("#4A4660"));
            holder.tvRankNum.setTextColor(Color.parseColor("#E0E0F0"));
        }

        // 前 10 名金色/橙色边框
        if (position < 10) {
            holder.cardRoot.setStrokeColor(Color.parseColor("#FFF58200"));
            holder.cardRoot.setStrokeWidth(4);
        } else {
            holder.cardRoot.setStrokeColor(Color.parseColor("#3A3654"));
            holder.cardRoot.setStrokeWidth(2);
        }

        holder.tvSongName.setText(item.getSongName());
        holder.tvPlayRating.setText(item.getFormattedPlayRating());
        holder.tvPlayRating.setBackgroundColor(getDiffLightColor(item.getDifficulty()));

        String diffStr = item.getDifficulty() + " [" + item.getFormattedConstant() + "]";
        holder.tvDifficultyConstant.setText(diffStr);
        holder.tvDifficultyConstant.setBackgroundColor(getDiffDarkColor(item.getDifficulty()));

        holder.tvScore.setText(item.getFormattedScore());
        if (item.getScore() >= 10000000 && item.getFar() == 0 && item.getLost() == 0) {
            holder.tvScore.setTextColor(Color.parseColor("#00E5FF"));
        } else {
            holder.tvScore.setTextColor(Color.parseColor("#FFFFFF"));
        }

        String notesStr = "P/" + item.getPerfect() + "(-" + item.getNormalPerfect() + ")   F/" + item.getFar() + "   L/" + item.getLost();
        holder.tvItems.setText(notesStr);

        // 异步加载曲绘（使用 inSampleSize = 4 极速解码，仅需 ~128x128）
        final String illKey = "Processed_Illustration/" + item.getIllustration();
        holder.ivJacket.setTag(illKey);
        loadAssetImageAsync(illKey, "Processed_Illustration/sayonarahatsukoi.jpg", holder.ivJacket, 4);

        // 异步加载评级
        final String rankKey = "img/rank/" + item.getRank() + ".png";
        holder.ivRankBadge.setTag(rankKey);
        loadAssetImageAsync(rankKey, null, holder.ivRankBadge, 1);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void loadAssetImageAsync(final String assetPath, final String fallbackAssetPath, final ImageView imageView, final int sampleSize) {
        Bitmap cached = imageCache.get(assetPath);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        imageView.setImageDrawable(null);
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = loadSampledBitmapFromAsset(assetPath, sampleSize);
                if (bmp == null && fallbackAssetPath != null) {
                    bmp = loadSampledBitmapFromAsset(fallbackAssetPath, sampleSize);
                }
                final Bitmap finalBmp = bmp;
                if (finalBmp != null) {
                    imageCache.put(assetPath, finalBmp);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (assetPath.equals(imageView.getTag())) {
                                imageView.setImageBitmap(finalBmp);
                            }
                        }
                    });
                }
            }
        });
    }

    private Bitmap loadSampledBitmapFromAsset(String path, int sampleSize) {
        try (InputStream is = context.getAssets().open(path)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565; // 内存减半
            return BitmapFactory.decodeStream(is, null, opts);
        } catch (Exception e) {
            return null;
        }
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

    static class SongViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot;
        ImageView ivJacket;
        TextView tvRankNum;
        TextView tvPlayRating;
        TextView tvDifficultyConstant;
        TextView tvSongName;
        TextView tvScore;
        TextView tvItems;
        ImageView ivRankBadge;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = (MaterialCardView) itemView;
            ivJacket = itemView.findViewById(R.id.iv_jacket);
            tvRankNum = itemView.findViewById(R.id.tv_rank_num);
            tvPlayRating = itemView.findViewById(R.id.tv_play_rating);
            tvDifficultyConstant = itemView.findViewById(R.id.tv_difficulty_constant);
            tvSongName = itemView.findViewById(R.id.tv_song_name);
            tvScore = itemView.findViewById(R.id.tv_score);
            tvItems = itemView.findViewById(R.id.tv_items);
            ivRankBadge = itemView.findViewById(R.id.iv_rank_badge);
        }
    }
}
