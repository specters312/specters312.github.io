package io.github.specters312.intentlab;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class IntentLabBridge {
    private static final Pattern ACTION_PATTERN = Pattern.compile("^[A-Za-z0-9_$]+(?:\\.[A-Za-z0-9_$]+)+$");
    private static final int MAX_CASES = 2000;
    private static final int MAX_REPORT_BYTES = 20 * 1024 * 1024;

    private final Activity activity;
    private final PackageManager packageManager;

    IntentLabBridge(Activity activity) {
        this.activity = activity;
        this.packageManager = activity.getPackageManager();
    }

    @JavascriptInterface
    public String getEnvironment() {
        try {
            JSONObject out = new JSONObject();
            PackageInfo own = packageManager.getPackageInfo(activity.getPackageName(), 0);
            out.put("hostPackage", activity.getPackageName());
            out.put("hostVersionName", own.versionName);
            out.put("hostVersionCode", versionCode(own));
            out.put("sdkInt", Build.VERSION.SDK_INT);
            out.put("release", Build.VERSION.RELEASE);
            out.put("manufacturer", Build.MANUFACTURER);
            out.put("model", Build.MODEL);
            out.put("device", Build.DEVICE);
            out.put("fingerprint", Build.FINGERPRINT);

            PackageInfo webView = WebView.getCurrentWebViewPackage();
            if (webView != null) {
                JSONObject web = new JSONObject();
                web.put("package", webView.packageName);
                web.put("versionName", webView.versionName);
                web.put("versionCode", versionCode(webView));
                out.put("webView", web);
            } else {
                out.put("webView", JSONObject.NULL);
            }
            return out.toString();
        } catch (Exception e) {
            return error(e).toString();
        }
    }

    @JavascriptInterface
    public String enumerateHost() {
        try {
            int flags = PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_SERVICES
                    | PackageManager.GET_RECEIVERS
                    | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_PERMISSIONS
                    | PackageManager.GET_META_DATA;
            PackageInfo info = packageManager.getPackageInfo(activity.getPackageName(), flags);
            JSONObject out = new JSONObject();
            out.put("package", info.packageName);
            out.put("versionName", info.versionName);
            out.put("versionCode", versionCode(info));
            out.put("activities", components(info.activities));
            out.put("services", components(info.services));
            out.put("receivers", components(info.receivers));
            out.put("providers", providers(info.providers));
            out.put("requestedPermissions", stringArray(info.requestedPermissions));
            return out.toString();
        } catch (Exception e) {
            return error(e).toString();
        }
    }

    @JavascriptInterface
    public String enumerateInstalledPackages() {
        try {
            List<PackageInfo> packages = packageManager.getInstalledPackages(0);
            packages.sort(Comparator.comparing(item -> item.packageName));
            JSONArray rows = new JSONArray();
            for (PackageInfo info : packages) {
                JSONObject row = new JSONObject();
                ApplicationInfo app = info.applicationInfo;
                row.put("package", info.packageName);
                row.put("versionName", info.versionName);
                row.put("versionCode", versionCode(info));
                row.put("enabled", app != null && app.enabled);
                row.put("system", app != null && (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                row.put("updatedSystem", app != null && (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
                rows.put(row);
            }
            JSONObject out = new JSONObject();
            out.put("count", rows.length());
            out.put("packages", rows);
            return out.toString();
        } catch (Exception e) {
            return error(e).toString();
        }
    }

    @JavascriptInterface
    public String resolveCases(String casesJson) {
        try {
            JSONArray cases = new JSONArray(casesJson);
            if (cases.length() > MAX_CASES) {
                throw new IllegalArgumentException("Too many cases; maximum is " + MAX_CASES);
            }

            JSONArray results = new JSONArray();
            for (int i = 0; i < cases.length(); i++) {
                JSONObject input = cases.getJSONObject(i);
                String action = input.optString("action", "").trim();
                if (!ACTION_PATTERN.matcher(action).matches()) {
                    JSONObject rejected = new JSONObject();
                    rejected.put("index", i);
                    rejected.put("action", action);
                    rejected.put("error", "invalid_action");
                    results.put(rejected);
                    continue;
                }

                String uri = input.optString("uri", "").trim();
                String mimeType = input.optString("mimeType", "").trim();
                Intent base = new Intent(action);
                if (!uri.isEmpty()) {
                    base.setData(Uri.parse(uri));
                }
                if (!mimeType.isEmpty()) {
                    base.setType(mimeType);
                }

                Intent browser = new Intent(base);
                browser.addCategory(Intent.CATEGORY_BROWSABLE);

                JSONObject result = new JSONObject();
                result.put("index", i);
                result.put("id", input.optString("id", "case-" + i));
                result.put("label", input.optString("label", ""));
                result.put("action", action);
                result.put("uri", uri);
                result.put("mimeType", mimeType);
                result.put("source", input.opt("source"));
                result.put("nativeHandlers", handlers(query(base)));
                result.put("browserHandlers", handlers(query(browser)));
                results.put(result);
            }

            JSONObject out = new JSONObject();
            out.put("scannedAt", isoNow());
            out.put("caseCount", cases.length());
            out.put("results", results);
            return out.toString();
        } catch (Exception e) {
            return error(e).toString();
        }
    }

    @JavascriptInterface
    public String getDeviceCorpus() {
        try (InputStream input = activity.getAssets().open("device-browsable-scan.json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > MAX_REPORT_BYTES) {
                    throw new IllegalArgumentException("Device corpus exceeds 20 MiB");
                }
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return error(e).toString();
        }
    }

    @JavascriptInterface
    public String runLocalCommand(String command) {
        long started = System.currentTimeMillis();
        Process process = null;
        try {
            if (command == null) throw new IllegalArgumentException("Command is required");
            String normalized = command.trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException("Command is empty");
            if (normalized.length() > 4096) throw new IllegalArgumentException("Command exceeds 4096 characters");
            if (normalized.indexOf('\0') >= 0 || normalized.contains("\n") || normalized.contains("\r")) {
                throw new IllegalArgumentException("Only one command line is accepted");
            }

            process = new ProcessBuilder("/system/bin/sh", "-c", normalized)
                    .redirectErrorStream(true)
                    .start();
            Process runningProcess = process;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (InputStream stream = runningProcess.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = stream.read(buffer)) != -1) {
                        int remaining = (1024 * 1024) - output.size();
                        if (remaining > 0) output.write(buffer, 0, Math.min(read, remaining));
                    }
                } catch (Exception ignored) {
                    // The result still includes timeout/exit status and any partial output.
                }
            }, "intent-lab-shell-reader");
            reader.start();

            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            reader.join(2000);

            JSONObject result = new JSONObject();
            result.put("command", normalized);
            result.put("finished", finished);
            result.put("exitCode", finished ? process.exitValue() : JSONObject.NULL);
            result.put("stdout", output.toString(StandardCharsets.UTF_8.name()));
            result.put("truncated", output.size() >= 1024 * 1024);
            result.put("durationMs", System.currentTimeMillis() - started);
            return result.toString();
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            JSONObject result = error(e);
            try {
                result.put("durationMs", System.currentTimeMillis() - started);
            } catch (JSONException ignored) {
                // Fixed JSON key.
            }
            return result.toString();
        }
    }

    @JavascriptInterface
    public String saveReport(String reportJson) {
        try {
            byte[] bytes = reportJson.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_REPORT_BYTES) {
                throw new IllegalArgumentException("Report exceeds 20 MiB");
            }
            File base = activity.getExternalFilesDir("intent-lab");
            if (base == null) {
                base = new File(activity.getFilesDir(), "intent-lab");
            }
            if (!base.exists() && !base.mkdirs()) {
                throw new IllegalStateException("Could not create log directory");
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File destination = new File(base, "native-enumeration-" + stamp + ".json");
            try (FileOutputStream stream = new FileOutputStream(destination)) {
                stream.write(bytes);
            }
            JSONObject out = new JSONObject();
            out.put("saved", true);
            out.put("path", destination.getAbsolutePath());
            out.put("bytes", bytes.length);
            return out.toString();
        } catch (Exception e) {
            return error(e).toString();
        }
    }

    private List<ResolveInfo> query(Intent intent) {
        return packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_ALL | PackageManager.GET_RESOLVED_FILTER
        );
    }

    private JSONArray handlers(List<ResolveInfo> values) throws JSONException {
        List<ResolveInfo> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparing(item -> {
            ActivityInfo activityInfo = item.activityInfo;
            return activityInfo == null ? "" : activityInfo.packageName + "/" + activityInfo.name;
        }));

        JSONArray out = new JSONArray();
        for (ResolveInfo info : sorted) {
            ActivityInfo component = info.activityInfo;
            if (component == null) continue;
            JSONObject row = new JSONObject();
            row.put("package", component.packageName);
            row.put("activity", component.name);
            row.put("exported", component.exported);
            row.put("enabled", component.enabled);
            row.put("permission", component.permission == null ? "" : component.permission);
            row.put("system", component.applicationInfo != null
                    && (component.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            row.put("priority", info.priority);
            row.put("preferredOrder", info.preferredOrder);
            row.put("match", info.match);
            out.put(row);
        }
        return out;
    }

    private JSONArray components(ComponentInfo[] values) throws JSONException {
        JSONArray out = new JSONArray();
        if (values == null) return out;
        for (ComponentInfo component : values) {
            JSONObject row = new JSONObject();
            String permission = "";
            if (component instanceof ActivityInfo && ((ActivityInfo) component).permission != null) {
                permission = ((ActivityInfo) component).permission;
            } else if (component instanceof ServiceInfo && ((ServiceInfo) component).permission != null) {
                permission = ((ServiceInfo) component).permission;
            }
            row.put("name", component.name);
            row.put("exported", component.exported);
            row.put("enabled", component.enabled);
            row.put("permission", permission);
            out.put(row);
        }
        return out;
    }

    private JSONArray providers(ProviderInfo[] values) throws JSONException {
        JSONArray out = new JSONArray();
        if (values == null) return out;
        for (ProviderInfo provider : values) {
            JSONObject row = new JSONObject();
            row.put("name", provider.name);
            row.put("authority", provider.authority);
            row.put("exported", provider.exported);
            row.put("enabled", provider.enabled);
            row.put("readPermission", provider.readPermission == null ? "" : provider.readPermission);
            row.put("writePermission", provider.writePermission == null ? "" : provider.writePermission);
            out.put(row);
        }
        return out;
    }

    private JSONArray stringArray(String[] values) {
        JSONArray out = new JSONArray();
        if (values == null) return out;
        for (String value : values) out.put(value);
        return out;
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static JSONObject error(Exception exception) {
        JSONObject out = new JSONObject();
        try {
            out.put("error", exception.getClass().getSimpleName());
            out.put("message", exception.getMessage());
        } catch (JSONException ignored) {
            // JSONObject operations above use fixed keys and should not fail.
        }
        return out;
    }

    private static String isoNow() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date());
    }
}
