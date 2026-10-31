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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class IntentLabBridge {
    private static final Pattern ACTION_PATTERN = Pattern.compile("^[A-Za-z0-9_$]+(?:\\.[A-Za-z0-9_$]+)+$");
    private static final int MAX_CASES = 2000;
    private static final int MAX_REPORT_BYTES = 20 * 1024 * 1024;
    private static final int MAX_DIRECTORY_ENTRIES = 1000;
    private static final int MAX_PREVIEW_BYTES = 256 * 1024;
    private static final int MAX_HEX_BYTES = 8 * 1024;

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
    public String getFileSystemRoots() {
        try {
            Map<String, String> roots = new LinkedHashMap<>();
            addRoot(roots, "App data", activity.getDataDir());
            addRoot(roots, "App files", activity.getFilesDir());
            addRoot(roots, "App cache", activity.getCacheDir());
            addRoot(roots, "App code cache", activity.getCodeCacheDir());
            addRoot(roots, "App no-backup files", activity.getNoBackupFilesDir());

            File externalFiles = activity.getExternalFilesDir(null);
            if (externalFiles != null) {
                addRoot(roots, "App external data", externalFiles.getParentFile());
                addRoot(roots, "App external files", externalFiles);
            }
            addRoot(roots, "App external cache", activity.getExternalCacheDir());

            addRoot(roots, "Process view", new File("/proc/self"));
            addRoot(roots, "Mounted filesystems", new File("/proc/mounts"));
            addRoot(roots, "Shared storage", new File("/storage/emulated/0"));
            addRoot(roots, "Filesystem root", new File("/"));

            JSONArray values = new JSONArray();
            for (Map.Entry<String, String> root : roots.entrySet()) {
                File file = new File(root.getValue());
                JSONObject row = fileMetadata(file);
                row.put("label", root.getKey());
                values.put(row);
            }
            JSONObject out = new JSONObject();
            out.put("readOnly", true);
            out.put("roots", values);
            return out.toString();
        } catch (Exception e) {
            return error(e).toString();
        }
    }

    @JavascriptInterface
    public String listPath(String path) {
        try {
            File directory = checkedPath(path);
            JSONObject out = new JSONObject();
            JSONObject metadata = fileMetadata(directory);
            out.put("readOnly", true);
            out.put("path", directory.getAbsolutePath());
            out.put("canonicalPath", canonicalPath(directory));
            out.put("parent", directory.getParentFile() == null
                    ? JSONObject.NULL
                    : directory.getParentFile().getAbsolutePath());
            out.put("metadata", metadata);
            out.put("entries", new JSONArray());

            if (!directory.exists() && !Files.isSymbolicLink(directory.toPath())) {
                out.put("error", "not_found");
                out.put("message", "Path does not exist or is not visible to this app UID");
                return out.toString();
            }
            if (!directory.isDirectory()) {
                out.put("error", "not_directory");
                out.put("message", "Path is not a directory; select it to preview the file");
                return out.toString();
            }

            File[] listed;
            try {
                listed = directory.listFiles();
            } catch (SecurityException denied) {
                out.put("error", "permission_denied");
                out.put("message", denied.getMessage() == null ? "Directory access denied" : denied.getMessage());
                return out.toString();
            }
            if (listed == null) {
                out.put("error", "permission_denied_or_unreadable");
                out.put("message", "The app UID could not enumerate this directory");
                return out.toString();
            }

            List<File> children = new ArrayList<>();
            Collections.addAll(children, listed);
            children.sort((left, right) -> {
                int directoryOrder = Boolean.compare(!left.isDirectory(), !right.isDirectory());
                if (directoryOrder != 0) return directoryOrder;
                int nameOrder = left.getName().compareToIgnoreCase(right.getName());
                return nameOrder != 0 ? nameOrder : left.getName().compareTo(right.getName());
            });

            JSONArray entries = new JSONArray();
            int limit = Math.min(children.size(), MAX_DIRECTORY_ENTRIES);
            for (int i = 0; i < limit; i++) {
                entries.put(fileMetadata(children.get(i)));
            }
            out.put("entries", entries);
            out.put("entryCount", children.size());
            out.put("returnedCount", limit);
            out.put("truncated", children.size() > limit);
            return out.toString();
        } catch (Exception e) {
            return error(e).toString();
        }
    }

    @JavascriptInterface
    public String readFilePreview(String path, int requestedBytes) {
        try {
            File file = checkedPath(path);
            JSONObject out = new JSONObject();
            JSONObject metadata = fileMetadata(file);
            out.put("readOnly", true);
            out.put("path", file.getAbsolutePath());
            out.put("canonicalPath", canonicalPath(file));
            out.put("metadata", metadata);

            if (!file.exists()) {
                out.put("error", "not_found");
                out.put("message", "File does not exist or is not visible to this app UID");
                return out.toString();
            }
            if (!file.isFile()) {
                out.put("error", "not_file");
                out.put("message", "Path is not a regular file");
                return out.toString();
            }

            int limit = requestedBytes <= 0
                    ? 64 * 1024
                    : Math.min(requestedBytes, MAX_PREVIEW_BYTES);
            ByteArrayOutputStream preview = new ByteArrayOutputStream(Math.min(limit, 16 * 1024));
            boolean truncated;
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                while (preview.size() < limit) {
                    int remaining = limit - preview.size();
                    int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (read == -1) break;
                    preview.write(buffer, 0, read);
                }
                truncated = input.read() != -1;
            }

            byte[] bytes = preview.toByteArray();
            boolean text = isProbablyText(bytes);
            out.put("previewBytes", bytes.length);
            out.put("limitBytes", limit);
            out.put("truncated", truncated);
            out.put("format", text ? "utf-8-text" : "binary");
            out.put("text", text ? new String(bytes, StandardCharsets.UTF_8) : JSONObject.NULL);
            int hexBytes = Math.min(bytes.length, MAX_HEX_BYTES);
            out.put("hexBytes", hexBytes);
            out.put("hex", hexDump(bytes, hexBytes));
            return out.toString();
        } catch (Exception e) {
            return error(e).toString();
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

    private static File checkedPath(String path) {
        if (path == null) throw new IllegalArgumentException("Path is required");
        String normalized = path.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Path is empty");
        if (normalized.length() > 4096) throw new IllegalArgumentException("Path exceeds 4096 characters");
        if (normalized.indexOf('\0') >= 0) throw new IllegalArgumentException("Path contains a null byte");
        return new File(normalized).getAbsoluteFile();
    }

    private static void addRoot(Map<String, String> roots, String label, File file) {
        if (file == null) return;
        String path = file.getAbsolutePath();
        if (!roots.containsValue(path)) roots.put(label, path);
    }

    private static JSONObject fileMetadata(File file) throws JSONException {
        JSONObject out = new JSONObject();
        boolean symbolicLink = false;
        String linkTarget = null;
        try {
            symbolicLink = Files.isSymbolicLink(file.toPath());
            if (symbolicLink) linkTarget = Files.readSymbolicLink(file.toPath()).toString();
        } catch (Exception ignored) {
            // Some virtual filesystems do not expose link metadata to this app UID.
        }

        out.put("name", file.getName().isEmpty() ? file.getAbsolutePath() : file.getName());
        out.put("path", file.getAbsolutePath());
        out.put("canonicalPath", canonicalPath(file));
        out.put("exists", file.exists());
        out.put("directory", file.isDirectory());
        out.put("file", file.isFile());
        out.put("symbolicLink", symbolicLink);
        out.put("linkTarget", linkTarget == null ? JSONObject.NULL : linkTarget);
        out.put("type", symbolicLink ? "symlink" : file.isDirectory() ? "directory" : file.isFile() ? "file" : "other");
        out.put("size", file.isFile() ? file.length() : JSONObject.NULL);
        out.put("lastModified", file.lastModified() > 0 ? file.lastModified() : JSONObject.NULL);
        out.put("lastModifiedIso", file.lastModified() > 0
                ? new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date(file.lastModified()))
                : JSONObject.NULL);
        out.put("readable", file.canRead());
        out.put("writable", file.canWrite());
        out.put("executable", file.canExecute());
        out.put("hidden", file.isHidden());
        return out;
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception ignored) {
            return file.getAbsolutePath();
        }
    }

    private static boolean isProbablyText(byte[] bytes) {
        if (bytes.length == 0) return true;
        int controls = 0;
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            if (unsigned == 0) return false;
            if (unsigned < 0x20 && unsigned != '\n' && unsigned != '\r' && unsigned != '\t' && unsigned != '\f') {
                controls++;
            }
        }
        return controls * 20 <= bytes.length;
    }

    private static String hexDump(byte[] bytes, int length) {
        StringBuilder out = new StringBuilder(length * 4);
        for (int offset = 0; offset < length; offset += 16) {
            out.append(String.format(Locale.US, "%08x  ", offset));
            int rowEnd = Math.min(offset + 16, length);
            for (int index = offset; index < offset + 16; index++) {
                if (index < rowEnd) out.append(String.format(Locale.US, "%02x ", bytes[index] & 0xff));
                else out.append("   ");
                if (index == offset + 7) out.append(' ');
            }
            out.append(" | ");
            for (int index = offset; index < rowEnd; index++) {
                int value = bytes[index] & 0xff;
                out.append(value >= 0x20 && value <= 0x7e ? (char) value : '.');
            }
            out.append('\n');
        }
        return out.toString();
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
