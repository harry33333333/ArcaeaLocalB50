package moe.smartrte.arcb50.logic;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通过 Magisk / KernelSU / APatch 等 Root 提权直接读取 Arcaea 的 st3 数据库
 * 采用 stdout 数据流管道 + 多路径智能探测 + mount-master 适配，彻底避免 SELinux 写入拦截与退出码 1 错误
 */
public class RootExtractor {
    private static final String TAG = "RootExtractor";

    public interface ExtractCallback {
        void onSuccess(File st3File);
        void onError(String message);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void extractSt3(final Context context, final ExtractCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                File targetFile = new File(context.getCacheDir(), "extracted_st3.db");
                try {
                    if (targetFile.exists()) {
                        targetFile.delete();
                    }

                    // 方案 1: 标准 su -c，通过 cat 管道输出字节流，由 Java 写入应用缓存
                    String resultMsg = tryCatExtraction(targetFile, false);
                    if (targetFile.exists() && targetFile.length() > 0) {
                        Log.i(TAG, "Root extraction succeeded (standard su), file size=" + targetFile.length());
                        postSuccess(callback, targetFile);
                        return;
                    }

                    // 方案 2: su --mount-master 适配 Magisk 隔离挂载命名空间
                    Log.w(TAG, "Standard extraction failed (" + resultMsg + "), trying --mount-master...");
                    resultMsg = tryCatExtraction(targetFile, true);
                    if (targetFile.exists() && targetFile.length() > 0) {
                        Log.i(TAG, "Root extraction succeeded (--mount-master), file size=" + targetFile.length());
                        postSuccess(callback, targetFile);
                        return;
                    }

                    // 方案 3: 尝试复制到 /data/local/tmp 中转
                    Log.w(TAG, "Pipe extraction failed, trying /data/local/tmp relay...");
                    resultMsg = tryLocalTmpRelay(targetFile);
                    if (targetFile.exists() && targetFile.length() > 0) {
                        Log.i(TAG, "Root extraction succeeded (tmp relay), file size=" + targetFile.length());
                        postSuccess(callback, targetFile);
                        return;
                    }

                    // 全部失败，收集诊断信息反馈给用户
                    String diagnostic = collectDiagnostics();
                    String fullError = "Root 提取 st3 失败。\n" +
                            "诊断详情: " + resultMsg + "\n" +
                            "设备环境: " + diagnostic + "\n\n" +
                            "提示: 请确认已在 Magisk / KernelSU 中允许授予 Root 权限，并确认设备中已安装 Arcaea 且至少完成过一次歌曲结算。";
                    Log.e(TAG, fullError);
                    postError(callback, fullError);

                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error during root extraction", e);
                    postError(callback, "调用 Root 提权异常: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 方案 1 & 2: 执行 shell 脚本查找 st3 并通过 cat 标准输出输出到 Java FileOutputStream
     */
    private static String tryCatExtraction(File targetFile, boolean useMountMaster) {
        StringBuilder script = new StringBuilder();
        script.append("for p in /data/data/moe.low.arc/files/st3 ")
              .append("/data/user/0/moe.low.arc/files/st3 ")
              .append("/data/user_de/0/moe.low.arc/files/st3 ")
              .append("/data/media/0/Android/data/moe.low.arc/files/st3; do ")
              .append("if [ -f \"$p\" ]; then ")
              .append("cat \"$p\"; exit 0; ")
              .append("fi; ")
              .append("done; ")
              .append("ST=$(find /data/data/moe.low.arc /data/user/0/moe.low.arc -name \"st3\" 2>/dev/null | head -n 1); ")
              .append("if [ -n \"$ST\" ] && [ -f \"$ST\" ]; then ")
              .append("cat \"$ST\"; exit 0; ")
              .append("fi; ")
              .append("echo \"NOT_FOUND\" >&2; exit 2;");

        Process process = null;
        try {
            String[] cmd;
            if (useMountMaster) {
                cmd = new String[]{"su", "--mount-master", "-c", script.toString()};
            } else {
                cmd = new String[]{"su", "-c", script.toString()};
            }

            process = Runtime.getRuntime().exec(cmd);

            // 读取 stdout 写入目标文件
            try (InputStream stdout = process.getInputStream();
                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = stdout.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
                fos.flush();
            }

            // 读取 stderr
            String stderr = readStreamFully(process.getErrorStream());
            int exitCode = process.waitFor();

            if (exitCode == 0 && targetFile.exists() && targetFile.length() > 0) {
                return "OK";
            } else {
                if (targetFile.exists()) targetFile.delete();
                return "exitCode=" + exitCode + ", stderr=" + stderr.trim();
            }
        } catch (Exception e) {
            if (targetFile.exists()) targetFile.delete();
            return "Exception: " + e.getMessage();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * 方案 3: 通过 /data/local/tmp 中转文件
     */
    private static String tryLocalTmpRelay(File targetFile) {
        String tmpPath = "/data/local/tmp/arc_st3_export.db";
        StringBuilder script = new StringBuilder();
        script.append("SRC=\"\"; ")
              .append("for p in /data/data/moe.low.arc/files/st3 /data/user/0/moe.low.arc/files/st3; do ")
              .append("if [ -f \"$p\" ]; then SRC=\"$p\"; break; fi; done; ")
              .append("if [ -z \"$SRC\" ]; then SRC=$(find /data/data/moe.low.arc -name \"st3\" 2>/dev/null | head -n 1); fi; ")
              .append("if [ -n \"$SRC\" ]; then ")
              .append("cp -f \"$SRC\" ").append(tmpPath).append(" && chmod 777 ").append(tmpPath).append(" && exit 0; ")
              .append("else exit 3; fi;");

        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", script.toString()});
            String stderr = readStreamFully(process.getErrorStream());
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                File tmpFile = new File(tmpPath);
                if (tmpFile.exists() && tmpFile.length() > 0) {
                    // 复制 tmpFile 到 targetFile
                    try (InputStream fis = new java.io.FileInputStream(tmpFile);
                         FileOutputStream fos = new FileOutputStream(targetFile)) {
                        byte[] b = new byte[8192];
                        int n;
                        while ((n = fis.read(b)) > 0) {
                            fos.write(b, 0, n);
                        }
                    }
                    // 清理 tmpFile
                    try {
                        Runtime.getRuntime().exec(new String[]{"su", "-c", "rm -f " + tmpPath});
                    } catch (Exception ignored) {}
                    return "OK";
                }
            }
            return "TmpRelay failed: exitCode=" + exitCode + ", stderr=" + stderr.trim();
        } catch (Exception e) {
            return "TmpRelay exception: " + e.getMessage();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * 收集环境信息，便于排错
     */
    private static String collectDiagnostics() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    "ls -ld /data/data/moe.low.arc /data/data/moe.low.arc/files 2>&1; id"});
            return readStreamFully(p.getInputStream()).trim();
        } catch (Exception e) {
            return "无法执行诊断: " + e.getMessage();
        }
    }

    private static String readStreamFully(InputStream is) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static void postSuccess(final ExtractCallback callback, final File file) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) callback.onSuccess(file);
            }
        });
    }

    private static void postError(final ExtractCallback callback, final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) callback.onError(msg);
            }
        });
    }
}
