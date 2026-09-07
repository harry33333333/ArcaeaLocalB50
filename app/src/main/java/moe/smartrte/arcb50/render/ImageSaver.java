package moe.smartrte.arcb50.render;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 依照 Android 10+ (API 29-34) 规范，使用 MediaStore API 无缝保存长图至系统相册 (Pictures/ArcaeaB50)
 */
public class ImageSaver {
    private static final String TAG = "ImageSaver";

    public interface SaveCallback {
        void onSuccess(Uri uri, String path);
        void onError(String message);
    }

    public static Uri saveToGallery(Context context, Bitmap bitmap) throws Exception {
        if (bitmap == null) {
            throw new Exception("位图数据为空");
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String fileName = "Arcaea_B50_" + sdf.format(new Date()) + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ArcaeaB50");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new Exception("无法在 MediaStore 中创建图片记录");
        }

        try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
            if (out == null) {
                throw new Exception("无法打开 MediaStore 输出流");
            }
            boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
            if (!success) {
                throw new Exception("压缩保存 JPEG 图片失败");
            }
        }

        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        context.getContentResolver().update(uri, values, null, null);

        Log.i(TAG, "Image successfully saved to MediaStore: " + uri);
        return uri;
    }
}
