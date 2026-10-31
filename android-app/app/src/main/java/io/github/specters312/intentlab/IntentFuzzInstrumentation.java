package io.github.specters312.intentlab;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ADB-launched instrumentation runner. Resolution-only mode is the default.
 * Pass -e launch true to open every action, collect am/dumpsys output, and press Back between cases.
 */
public final class IntentFuzzInstrumentation extends Instrumentation {
    private Bundle arguments;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? new Bundle() : arguments;
        start();
        new Thread(this::runCorpus, "intent-lab-runner").start();
    }

    private void runCorpus() {
        Bundle result = new Bundle();
        try {
            boolean launch = "true".equalsIgnoreCase(arguments.getString("launch", "false"));
            int delayMs = boundedInt(arguments.getString("delayMs", "750"), 250, 10_000, 750);
            Context context = getTargetContext();
            PackageManager packageManager = context.getPackageManager();
            UiAutomation automation = launch ? getUiAutomation() : null;

            JSONObject report = new JSONObject();
            report.put("schemaVersion", 1);
            report.put("createdAt", isoNow());
            report.put("mode", launch ? "launch" : "resolve_only");
            report.put("delayMs", delayMs);
            report.put("targetPackage", context.getPackageName());

            JSONArray cases = new JSONArray();
            int index = 0;
            for (String action : IntentCorpus.COMMON_ACTIONS) {
                JSONObject item = new JSONObject();
                item.put("index", index++);
                item.put("action", action);

                Intent browserIntent = new Intent(action).addCategory(Intent.CATEGORY_BROWSABLE);
                List<ResolveInfo> handlers = packageManager.queryIntentActivities(
                        browserIntent,
                        PackageManager.MATCH_ALL | PackageManager.GET_RESOLVED_FILTER
                );
                JSONArray resolved = new JSONArray();
                for (ResolveInfo info : handlers) {
                    ActivityInfo activity = info.activityInfo;
                    if (activity == null) continue;
                    JSONObject handler = new JSONObject();
                    handler.put("package", activity.packageName);
                    handler.put("activity", activity.name);
                    handler.put("exported", activity.exported);
                    handler.put("enabled", activity.enabled);
                    handler.put("permission", activity.permission == null ? "" : activity.permission);
                    resolved.put(handler);
                }
                item.put("browserHandlers", resolved);

                if (launch) {
                    String startOutput = shell(automation, "am start -W -a " + action);
                    item.put("amStart", startOutput);
                    Thread.sleep(delayMs);
                    item.put("focusedWindow", shell(
                            automation,
                            "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' | head -4"
                    ));
                    item.put("backOutput", shell(automation, "input keyevent KEYCODE_BACK"));
                    Thread.sleep(Math.min(400, delayMs));
                }

                cases.put(item);
                Bundle progress = new Bundle();
                progress.putString("action", action);
                progress.putInt("index", index);
                sendStatus(1, progress);
            }
            report.put("cases", cases);

            File directory = context.getExternalFilesDir("intent-lab");
            if (directory == null) directory = new File(context.getFilesDir(), "intent-lab");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("Could not create report directory");
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File destination = new File(directory, "instrumentation-" + stamp + ".json");
            byte[] bytes = report.toString(2).getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream stream = new FileOutputStream(destination)) {
                stream.write(bytes);
            }

            result.putString("reportPath", destination.getAbsolutePath());
            result.putInt("caseCount", cases.length());
            result.putString("mode", launch ? "launch" : "resolve_only");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("error", error.getClass().getName() + ": " + error.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static String shell(UiAutomation automation, String command) throws Exception {
        try (ParcelFileDescriptor descriptor = automation.executeShellCommand(command);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > 256 * 1024) break;
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static int boundedInt(String value, int minimum, int maximum, int fallback) {
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String isoNow() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date());
    }
}
