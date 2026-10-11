package net.box86.android;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Persistent diagnostics for the complete Android application and 86Box native runtime.
 *
 * v0.9.7 adds one process-wide app log which starts in Application.onCreate and stays
 * active while the user moves between Profile Manager, Hardware Configurator and the VM.
 * Profile/session logs remain available as before. Diagnostic exports contain logs and
 * configuration metadata only; HDD/floppy/CD images are deliberately excluded.
 */
public final class DiagnosticLogger {
    private static final Object LOCK = new Object();
    private static final int KEEP_SESSION_FILES = 8;
    private static final int KEEP_APP_FILES = 12;
    private static final long MAX_TAIL_BYTES = 512 * 1024;
    private static final String TAG = "86BoxDiag";

    private static volatile File currentProfile;
    private static volatile File currentAndroidLog;
    private static volatile File currentNativeLog;
    private static volatile File currentAppLog;
    private static volatile File appRoot;
    private static volatile String currentProfileName = "unknown";
    private static Thread.UncaughtExceptionHandler previousHandler;
    private static boolean crashHandlerInstalled;

    private DiagnosticLogger() { }

    public static void startAppSession(Context context) {
        synchronized (LOCK) {
            ensureAppSessionLocked(context.getApplicationContext());
        }
    }

    private static void ensureAppSessionLocked(Context context) {
        if (currentAppLog != null && currentAppLog.isFile()) return;
        try {
            appRoot = new File(context.getFilesDir(), "86box");
            File logs = new File(appRoot, "app-logs");
            if (!logs.mkdirs() && !logs.isDirectory())
                throw new IOException("Could not create " + logs);
            pruneByPrefix(logs, "app-", KEEP_APP_FILES - 1);
            currentAppLog = new File(logs, "app-" + timestampForFile() + ".log");
            appendFile(currentAppLog, "===== 86Box Android COMPLETE APP LOG =====\n");
            appendFile(currentAppLog, systemInfo(context, "Application process", appRoot));
            appendFile(currentAppLog, "This log follows the complete app process across all activities.\n");
            appendFile(currentAppLog, "================================================\n");
            installJavaCrashHandler(context);
            Log.i(TAG, "Complete app diagnostics started: " + currentAppLog);
        } catch (Throwable t) {
            Log.e(TAG, "Could not initialize complete app diagnostics", t);
        }
    }

    public static void startSession(Context context, File profile, String profileName) {
        synchronized (LOCK) {
            try {
                ensureAppSessionLocked(context.getApplicationContext());
                currentProfile = profile;
                currentProfileName = profileName == null || profileName.trim().isEmpty() ? profile.getName() : profileName;
                File logs = new File(profile, "logs");
                if (!logs.mkdirs() && !logs.isDirectory())
                    throw new IOException("Could not create " + logs);
                pruneByPrefix(logs, "android-", KEEP_SESSION_FILES - 1);
                pruneByPrefix(logs, "native-", KEEP_SESSION_FILES - 1);
                String stamp = timestampForFile();
                currentAndroidLog = new File(logs, "android-" + stamp + ".log");
                currentNativeLog = new File(logs, "native-" + stamp + ".log");
                // Create the native log path before SDL/86Box starts. Some Android
                // native logging backends open the file lazily; creating it here
                // guarantees COMPLETE APP LOG can show whether native output was
                // empty instead of silently omitting the expected file.
                try { if (!currentNativeLog.exists()) currentNativeLog.createNewFile(); } catch (IOException ignored) { }
                writeRaw("===== 86Box Android diagnostic session =====\n");
                writeRaw(systemInfo(context, currentProfileName, profile));
                writeRaw("Native log: " + currentNativeLog.getAbsolutePath() + "\n");
                writeRaw("============================================\n");
                installJavaCrashHandler(context.getApplicationContext());
                Log.i(TAG, "Diagnostics started: " + currentAndroidLog);
            } catch (Throwable t) {
                Log.e(TAG, "Could not initialize persistent diagnostics", t);
            }
        }
    }

    public static void startManagerSession(Context context) {
        synchronized (LOCK) {
            try {
                ensureAppSessionLocked(context.getApplicationContext());
                File root = new File(context.getFilesDir(), "86box");
                File profile = new File(root, "manager-diagnostics");
                if (!profile.mkdirs() && !profile.isDirectory()) return;
                if (profile.equals(currentProfile) && currentAndroidLog != null && currentAndroidLog.isFile())
                    return;
                startSession(context, profile, "Profile Manager");
            } catch (Throwable t) {
                Log.e(TAG, "Could not initialize manager diagnostics", t);
            }
        }
    }

    public static File getNativeLogFile(File profile) {
        synchronized (LOCK) {
            if (currentNativeLog != null && currentProfile != null && currentProfile.equals(profile))
                return currentNativeLog;
            File logs = new File(profile, "logs");
            //noinspection ResultOfMethodCallIgnored
            logs.mkdirs();
            currentProfile = profile;
            currentNativeLog = new File(logs, "native-" + timestampForFile() + ".log");
            try { if (!currentNativeLog.exists()) currentNativeLog.createNewFile(); } catch (IOException ignored) { }
            return currentNativeLog;
        }
    }

    public static File getAndroidLogFile() {
        synchronized (LOCK) { return currentAndroidLog; }
    }

    public static File getAppLogFile() {
        synchronized (LOCK) { return currentAppLog; }
    }

    public static void i(String tag, String message) { log('I', tag, message, null); }
    public static void w(String tag, String message) { log('W', tag, message, null); }
    public static void w(String tag, String message, Throwable t) { log('W', tag, message, t); }
    public static void e(String tag, String message, Throwable t) { log('E', tag, message, t); }

    private static void log(char level, String tag, String message, Throwable t) {
        if (message == null) message = "";
        if (level == 'E') Log.e(tag, message, t);
        else if (level == 'W') Log.w(tag, message, t);
        else Log.i(tag, message);
        synchronized (LOCK) {
            StringBuilder line = new StringBuilder();
            line.append(timestamp()).append(' ').append(level).append('/').append(tag)
                    .append(" [").append(Thread.currentThread().getName()).append("]: ")
                    .append(message).append('\n');
            if (t != null) line.append(stackTrace(t));
            writeRaw(line.toString());
        }
    }

    public static String tailCurrentLogs(int maxLines) {
        synchronized (LOCK) {
            StringBuilder out = new StringBuilder();
            if (currentAppLog != null) {
                out.append("--- Complete app log ---\n");
                out.append(tailFile(currentAppLog, Math.max(20, maxLines / 3)));
            }
            if (currentAndroidLog != null) {
                out.append("\n--- Android/UI session log ---\n");
                out.append(tailFile(currentAndroidLog, Math.max(20, maxLines / 3)));
            }
            if (currentNativeLog != null) {
                out.append("\n--- 86Box native log ---\n");
                out.append(tailFile(currentNativeLog, Math.max(20, maxLines / 3)));
            }
            if (out.length() == 0) return "No diagnostic log has been created yet.";
            return out.toString();
        }
    }

    public static String tailAppLog(int maxLines) {
        synchronized (LOCK) {
            if (currentAppLog == null) return "No complete app log has been created yet.";
            return tailFile(currentAppLog, Math.max(20, maxLines));
        }
    }

    public static void exportDiagnostics(Context context, File profile, String profileName, Uri destination) throws IOException {
        try (OutputStream raw = openCreateOutputStream(context, destination)) {
            try (ZipOutputStream zip = new ZipOutputStream(raw)) {
                addText(zip, "diagnostics/system.txt", systemInfo(context, profileName, profile));
                addIfFile(zip, currentAppLog, "app/current-complete-app.log");
                addIfFile(zip, new File(profile, "86box.cfg"), "profile/86box.cfg");
                addIfFile(zip, new File(profile, "profile.properties"), "profile/profile.properties");
                addIfFile(zip, new File(profile, "IMPORT_REPORT.txt"), "profile/IMPORT_REPORT.txt");
                addIfFile(zip, new File(profile, "86box.desktop-original.cfg"), "profile/86box.desktop-original.cfg");
                addLogDirectory(zip, new File(profile, "logs"), "logs/");
            }
        }
    }

    /** Export one ZIP which covers the whole app, all profile/session logs and configs, but never media. */
    public static void exportAppDiagnostics(Context context, Uri destination) throws IOException {
        synchronized (LOCK) {
            ensureAppSessionLocked(context.getApplicationContext());
            i("AppDiagnostics", "Complete app diagnostics export requested");
        }
        File root = new File(context.getFilesDir(), "86box");
        try (OutputStream raw = openCreateOutputStream(context, destination);
             ZipOutputStream zip = new ZipOutputStream(raw)) {
            addText(zip, "diagnostics/system.txt", systemInfo(context, "Complete application", root));
            addText(zip, "diagnostics/README.txt",
                    "86Box Android complete diagnostics. Contains application/session logs and configuration metadata only.\n" +
                    "HDD, floppy, CD/DVD images and ROM binaries are NOT included.\n");

            addLogDirectory(zip, new File(root, "app-logs"), "app-logs/");
            addLogDirectory(zip, new File(root, "manager-diagnostics/logs"), "manager-diagnostics/logs/");

            File profiles = new File(root, "profiles");
            File[] dirs = profiles.listFiles(File::isDirectory);
            if (dirs != null) {
                Arrays.sort(dirs, Comparator.comparing(File::getName));
                for (File profile : dirs) {
                    String base = "profiles/" + safeZipSegment(profile.getName()) + "/";
                    addIfFile(zip, new File(profile, "86box.cfg"), base + "86box.cfg");
                    addIfFile(zip, new File(profile, "86box.desktop-original.cfg"), base + "86box.desktop-original.cfg");
                    addIfFile(zip, new File(profile, "profile.properties"), base + "profile.properties");
                    addIfFile(zip, new File(profile, "IMPORT_REPORT.txt"), base + "IMPORT_REPORT.txt");
                    addLogDirectory(zip, new File(profile, "logs"), base + "logs/");
                }
            }
        }
    }

    private static void addLogDirectory(ZipOutputStream zip, File dir, String zipPrefix) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            if (f.isFile()) addIfFile(zip, f, zipPrefix + safeZipSegment(f.getName()));
        }
    }

    private static OutputStream openCreateOutputStream(Context context, Uri destination) throws IOException {
        IOException first = null;
        try {
            OutputStream out = context.getContentResolver().openOutputStream(destination, "rwt");
            if (out != null) return out;
        } catch (IOException | IllegalArgumentException e) {
            first = e instanceof IOException ? (IOException) e : new IOException(e);
        }
        try {
            OutputStream out = context.getContentResolver().openOutputStream(destination, "w");
            if (out != null) return out;
            throw new IOException("Could not open diagnostics destination");
        } catch (IOException | IllegalArgumentException e) {
            IOException second = e instanceof IOException ? (IOException) e : new IOException(e);
            if (first != null) second.addSuppressed(first);
            throw second;
        }
    }

    private static void installJavaCrashHandler(Context appContext) {
        if (crashHandlerInstalled) return;
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                File root = appRoot != null ? appRoot : new File(appContext.getFilesDir(), "86box");
                File appLogs = new File(root, "app-logs");
                //noinspection ResultOfMethodCallIgnored
                appLogs.mkdirs();
                try (FileWriter fw = new FileWriter(new File(appLogs, "app-crash-last.txt"), false)) {
                    fw.write(timestamp() + " E/UNCAUGHT: thread=" + thread.getName() + "\n");
                    fw.write(stackTrace(throwable));
                }

                File profile = currentProfile != null ? currentProfile : new File(root, "manager-diagnostics");
                File logs = new File(profile, "logs");
                //noinspection ResultOfMethodCallIgnored
                logs.mkdirs();
                try (FileWriter fw = new FileWriter(new File(logs, "java-crash-last.txt"), false)) {
                    fw.write(timestamp() + " E/UNCAUGHT: thread=" + thread.getName() + "\n");
                    fw.write(stackTrace(throwable));
                }
                writeRaw(timestamp() + " E/UNCAUGHT: thread=" + thread.getName() + "\n" + stackTrace(throwable));
            } catch (Throwable ignored) { }
            if (previousHandler != null) previousHandler.uncaughtException(thread, throwable);
        });
        crashHandlerInstalled = true;
    }

    private static String systemInfo(Context context, String profileName, File profile) {
        StringBuilder s = new StringBuilder();
        s.append("Time: ").append(timestamp()).append('\n');
        s.append("App: 86Box Android ").append(BuildConfig.VERSION_NAME).append(" (code ").append(BuildConfig.VERSION_CODE).append(")\n");
        s.append("Build type: ").append(BuildConfig.BUILD_TYPE).append('\n');
        s.append("Android: ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append('\n');
        s.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" [").append(Build.DEVICE).append("]\n");
        s.append("ABIs: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');
        s.append("Locale: ").append(Locale.getDefault()).append('\n');
        s.append("Profile/context: ").append(profileName == null ? "unknown" : profileName).append('\n');
        s.append("Path: ").append(profile == null ? "unknown" : profile.getAbsolutePath()).append('\n');
        if (profile != null) s.append("Free space: ").append(profile.getUsableSpace()).append(" bytes\n");
        Runtime rt = Runtime.getRuntime();
        s.append("Java memory: max=").append(rt.maxMemory()).append(" total=").append(rt.totalMemory()).append(" free=").append(rt.freeMemory()).append(" bytes\n");
        return s.toString();
    }

    /** Write to both the current profile/session log and the process-wide app log. */
    private static void writeRaw(String text) {
        if (text == null) return;
        File session = currentAndroidLog;
        File app = currentAppLog;
        if (session != null) appendFile(session, text);
        if (app != null && (session == null || !sameFile(session, app))) appendFile(app, text);
    }

    private static boolean sameFile(File a, File b) {
        try { return a.getCanonicalFile().equals(b.getCanonicalFile()); }
        catch (IOException e) { return a.equals(b); }
    }

    private static void appendFile(File file, String text) {
        if (file == null || text == null) return;
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write(text);
            fw.flush();
        } catch (IOException e) {
            Log.e(TAG, "Could not write diagnostic log " + file, e);
        }
    }

    private static String tailFile(File file, int maxLines) {
        if (file == null || !file.isFile()) return "(not created yet)\n";
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            long bytes = 0;
            while ((line = br.readLine()) != null) {
                lines.add(line);
                bytes += line.length() + 1L;
                if (lines.size() > maxLines * 2) lines.remove(0);
                if (bytes > MAX_TAIL_BYTES * 2 && lines.size() > maxLines) {
                    while (lines.size() > maxLines) lines.remove(0);
                    bytes = MAX_TAIL_BYTES;
                }
            }
        } catch (IOException e) {
            return "(could not read " + file.getName() + ": " + e.getMessage() + ")\n";
        }
        int start = Math.max(0, lines.size() - maxLines);
        StringBuilder out = new StringBuilder();
        for (int i = start; i < lines.size(); i++) out.append(lines.get(i)).append('\n');
        return out.toString();
    }

    private static void pruneByPrefix(File dir, String prefix, int keep) {
        File[] files = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(".log"));
        if (files == null || files.length <= keep) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = keep; i < files.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    private static void addIfFile(ZipOutputStream zip, File file, String entryName) throws IOException {
        if (file == null || !file.isFile()) return;
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) zip.write(buffer, 0, n);
        }
        zip.closeEntry();
    }

    private static void addText(ZipOutputStream zip, String entryName, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String safeZipSegment(String s) {
        if (s == null || s.isEmpty()) return "item";
        return s.replace('/', '_').replace('\\', '_').replace((char) 0, '_');
    }

    private static String stackTrace(Throwable t) {
        if (t == null) return "";
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String timestamp() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US);
        return f.format(new Date());
    }

    private static String timestampForFile() {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US);
        return f.format(new Date());
    }
}
