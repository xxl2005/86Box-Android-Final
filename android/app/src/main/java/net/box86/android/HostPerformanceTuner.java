package net.box86.android;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.PerformanceHintManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Runtime host detection for one universal APK.  Keep compile-time ARM64 code
 * generic; select scheduler/display tuning at runtime so Qualcomm, MediaTek,
 * Rockchip and unknown ARM64 devices all remain compatible.
 */
final class HostPerformanceTuner {
    static final int MODE_AUTO = 0;
    static final int MODE_BALANCED = 1;
    static final int MODE_PERFORMANCE = 2;
    static final int MODE_MAXIMUM = 3;

    static final int NATIVE_BALANCED = 0;
    static final int NATIVE_PERFORMANCE = 1;
    static final int NATIVE_MAXIMUM = 2;

    private static final Object ADPF_LOCK = new Object();
    private static PerformanceHintManager.Session adpfSession;
    private static int adpfTid = -1;
    private static int adpfMode = MODE_BALANCED;
    private static long adpfTargetNs = 16_666_667L;
    private static String adpfStatus = "off";

    static final class HostProfile {
        final String family;
        final String detail;
        final int effectiveAutoMode;
        final long maxCpuKHz;
        final int cpuCount;

        HostProfile(String family, String detail, int effectiveAutoMode, long maxCpuKHz, int cpuCount) {
            this.family = family;
            this.detail = detail;
            this.effectiveAutoMode = effectiveAutoMode;
            this.maxCpuKHz = maxCpuKHz;
            this.cpuCount = cpuCount;
        }

        String shortName() {
            if (family.startsWith("Qualcomm Oryon")) return "Oryon";
            if (family.startsWith("Qualcomm")) return "Snapdragon";
            if (family.startsWith("MediaTek")) return "MediaTek";
            if (family.startsWith("Rockchip")) return "RK3588";
            if (family.startsWith("ARM64")) return "ARM64";
            if (family.startsWith("x86_64")) return "x86_64";
            return "Host";
        }
    }

    static final class FrequencySample {
        final long timestampMs;
        final int nativeTid;
        final int runningCpu;
        final long currentKHz;
        final long maxKHz;
        final String allCoreSummary;

        FrequencySample(long timestampMs, int nativeTid, int runningCpu, long currentKHz, long maxKHz, String allCoreSummary) {
            this.timestampMs = timestampMs;
            this.nativeTid = nativeTid;
            this.runningCpu = runningCpu;
            this.currentKHz = currentKHz;
            this.maxKHz = maxKHz;
            this.allCoreSummary = allCoreSummary == null ? "" : allCoreSummary;
        }

        boolean hasFrequency() { return currentKHz > 0 && maxKHz > 0; }

        double ratioPercent() {
            return hasFrequency() ? (currentKHz * 100.0 / maxKHz) : Double.NaN;
        }

        String activeSummary() {
            if (runningCpu < 0) return "cpu=n/a freq=n/a";
            if (!hasFrequency()) return "cpu=" + runningCpu + " freq=n/a";
            return "cpu=" + runningCpu + " freq=" + formatGHz(currentKHz) + "/" + formatGHz(maxKHz) +
                    String.format(Locale.ROOT, " (%.0f%%)", ratioPercent());
        }
    }

    private HostPerformanceTuner() { }

    static HostProfile detect() {
        String abi = String.join(",", Build.SUPPORTED_ABIS).toLowerCase(Locale.ROOT);
        String soc = Build.VERSION.SDK_INT >= 31 ? safe(Build.SOC_MODEL) : "";
        String raw = (safe(Build.MANUFACTURER) + " " + safe(Build.BRAND) + " " + safe(Build.DEVICE) + " " +
                safe(Build.MODEL) + " " + safe(Build.HARDWARE) + " " + safe(Build.BOARD) + " " + soc + " " +
                readCpuInfo()).toLowerCase(Locale.ROOT);
        long maxKHz = detectMaxCpuKHz();
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());

        if (raw.contains("rk3588")) {
            return new HostProfile("Rockchip RK3588", "Cortex-A76/A55", MODE_PERFORMANCE, maxKHz, cores);
        }
        if (raw.contains("oryon") || raw.contains("snapdragon 8 elite") || raw.contains("sm8750") ||
                raw.contains("sm8850") || raw.contains("sm8850-ac")) {
            return new HostProfile("Qualcomm Oryon", nonEmpty(soc, "Snapdragon 8 Elite family"), MODE_PERFORMANCE, maxKHz, cores);
        }
        if (raw.contains("mediatek") || raw.contains("dimensity") || raw.matches("(?s).*\\bmt[0-9]{4,}.*")) {
            return new HostProfile("MediaTek ARM64", nonEmpty(soc, "Dimensity/Cortex family"), MODE_PERFORMANCE, maxKHz, cores);
        }
        if (raw.contains("qualcomm") || raw.contains("snapdragon") || raw.contains("qcom") ||
                raw.matches("(?s).*\\bsm[0-9]{4}.*")) {
            return new HostProfile("Qualcomm ARM64", nonEmpty(soc, "Snapdragon/Kryo family"), MODE_PERFORMANCE, maxKHz, cores);
        }
        if (abi.contains("arm64-v8a")) {
            int auto = maxKHz >= 2800000L ? MODE_PERFORMANCE : MODE_BALANCED;
            return new HostProfile("ARM64 generic", nonEmpty(soc, safe(Build.HARDWARE)), auto, maxKHz, cores);
        }
        if (abi.contains("x86_64")) {
            return new HostProfile("x86_64 Android", nonEmpty(soc, safe(Build.HARDWARE)), MODE_BALANCED, maxKHz, cores);
        }
        return new HostProfile("Unknown Android host", nonEmpty(soc, safe(Build.HARDWARE)), MODE_BALANCED, maxKHz, cores);
    }

    static int resolveMode(int selectedMode, HostProfile profile) {
        if (selectedMode == MODE_AUTO)
            return profile == null ? MODE_BALANCED : profile.effectiveAutoMode;
        return clamp(selectedMode, MODE_BALANCED, MODE_MAXIMUM);
    }

    static int toNativeMode(int effectiveMode) {
        if (effectiveMode >= MODE_MAXIMUM) return NATIVE_MAXIMUM;
        if (effectiveMode >= MODE_PERFORMANCE) return NATIVE_PERFORMANCE;
        return NATIVE_BALANCED;
    }

    static String modeName(int mode) {
        switch (mode) {
            case MODE_AUTO: return "Auto";
            case MODE_BALANCED: return "Balanced";
            case MODE_PERFORMANCE: return "Performance";
            case MODE_MAXIMUM: return "Maximum";
            default: return "Balanced";
        }
    }

    static String describe(HostProfile p) {
        if (p == null) return "unknown";
        String freq = p.maxCpuKHz > 0 ? String.format(Locale.ROOT, "%.2f GHz", p.maxCpuKHz / 1000000.0) : "freq n/a";
        return p.family + " / " + p.detail + " / " + p.cpuCount + " CPUs / " + freq;
    }

    static void applyActivityHints(Activity activity, int effectiveMode) {
        if (activity == null || activity.getWindow() == null)
            return;
        try {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            // 86Box guest timing does not need a 120/144/165 Hz Android panel.  In
            // performance modes prefer 60 Hz to leave thermal/power headroom for
            // the single hot emulation thread. Balanced leaves Android in charge.
            lp.preferredRefreshRate = effectiveMode >= MODE_PERFORMANCE ? 60.0f : 0.0f;
            activity.getWindow().setAttributes(lp);
        } catch (Throwable t) {
            DiagnosticLogger.w("HostPerf", "Could not apply display performance hint", t);
        }
    }


    static int recommendedVoodooRenderThreads(HostProfile p) {
        if (p == null) return 2;
        if (p.cpuCount >= 6 && p.maxCpuKHz >= 2500000L) return 4;
        if (p.cpuCount >= 4) return 2;
        return 1;
    }

    static void applyAdpf(Context context, int effectiveMode, int nativeTid) {
        synchronized (ADPF_LOCK) {
            if (Build.VERSION.SDK_INT < 31 || context == null || effectiveMode < MODE_PERFORMANCE || nativeTid <= 0) {
                closeAdpfLocked("inactive mode/tid");
                return;
            }
            if (adpfSession != null && adpfTid == nativeTid && adpfMode == effectiveMode)
                return;
            closeAdpfLocked("recreate");
            try {
                PerformanceHintManager manager = context.getSystemService(PerformanceHintManager.class);
                if (manager == null) {
                    adpfStatus = "unavailable";
                    return;
                }
                adpfTargetNs = effectiveMode >= MODE_MAXIMUM ? 16_666_667L : 20_000_000L;
                PerformanceHintManager.Session session = manager.createHintSession(new int[]{nativeTid}, adpfTargetNs);
                if (session == null) {
                    adpfStatus = "session-null";
                    return;
                }
                adpfSession = session;
                adpfTid = nativeTid;
                adpfMode = effectiveMode;
                long preferred = manager.getPreferredUpdateRateNanos();
                adpfStatus = "active tid=" + nativeTid + " target=" + (adpfTargetNs / 1_000_000.0) +
                        "ms update=" + (preferred / 1_000_000.0) + "ms";
                DiagnosticLogger.i("HostPerf", "ADPF " + adpfStatus);
            } catch (Throwable t) {
                adpfStatus = "error:" + t.getClass().getSimpleName();
                DiagnosticLogger.w("HostPerf", "Could not create ADPF performance hint session", t);
                closeAdpfLocked("error");
            }
        }
    }

    static void reportAdpfWork(int speedPercent) {
        synchronized (ADPF_LOCK) {
            if (adpfSession == null || speedPercent <= 0)
                return;
            try {
                long actualNs = Math.max(adpfTargetNs,
                        Math.min(100_000_000L, Math.round(adpfTargetNs * 100.0 / Math.max(1, speedPercent))));
                adpfSession.reportActualWorkDuration(actualNs);
            } catch (Throwable t) {
                DiagnosticLogger.w("HostPerf", "ADPF reportActualWorkDuration failed", t);
                closeAdpfLocked("report-error");
            }
        }
    }

    static void closeAdpf() {
        synchronized (ADPF_LOCK) {
            closeAdpfLocked("close");
        }
    }

    private static void closeAdpfLocked(String reason) {
        if (adpfSession != null) {
            try { adpfSession.close(); } catch (Throwable ignored) { }
        }
        adpfSession = null;
        adpfTid = -1;
        adpfMode = MODE_BALANCED;
        if (!"recreate".equals(reason)) adpfStatus = "off(" + reason + ")";
    }

    static String runtimeHealth(Context context) {
        String thermal = "thermal=n/a";
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && Build.VERSION.SDK_INT >= 29)
                thermal = "thermal=" + thermalName(pm.getCurrentThermalStatus());
        } catch (Throwable t) {
            thermal = "thermal=error";
        }
        String headroom = "cpuHeadroom=n/a";
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                Object manager = context.getSystemService(Context.SYSTEM_HEALTH_SERVICE);
                if (manager != null) {
                    Class<?> paramsClass = Class.forName("android.os.CpuHeadroomParams");
                    Method method = manager.getClass().getMethod("getCpuHeadroom", paramsClass);
                    Object value = method.invoke(manager, new Object[]{null});
                    if (value instanceof Number) {
                        float f = ((Number) value).floatValue();
                        headroom = Float.isNaN(f) ? "cpuHeadroom=NaN" :
                                String.format(Locale.ROOT, "cpuHeadroom=%.1f%%", f);
                    }
                }
            } catch (Throwable t) {
                headroom = "cpuHeadroom=unsupported";
            }
        }
        String adpf;
        synchronized (ADPF_LOCK) { adpf = "ADPF=" + adpfStatus; }
        return thermal + " / " + headroom + " / " + adpf;
    }

    private static String thermalName(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "none";
            case PowerManager.THERMAL_STATUS_LIGHT: return "light";
            case PowerManager.THERMAL_STATUS_MODERATE: return "moderate";
            case PowerManager.THERMAL_STATUS_SEVERE: return "severe";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "shutdown";
            default: return "unknown(" + status + ")";
        }
    }

    static FrequencySample sampleFrequencies(int nativeTid, boolean includeAllCores) {
        int runningCpu = readThreadProcessor(nativeTid);
        long currentKHz = runningCpu >= 0 ? readCoreCurrentKHz(runningCpu) : 0L;
        long maxKHz = runningCpu >= 0 ? readCoreMaxKHz(runningCpu) : 0L;
        String all = includeAllCores ? buildAllCoreFrequencySummary() : "";
        return new FrequencySample(SystemClock.elapsedRealtime(), nativeTid, runningCpu, currentKHz, maxKHz, all);
    }

    static String frequencyState(int nativeTid) {
        FrequencySample sample = sampleFrequencies(nativeTid, true);
        String state = sample.activeSummary();
        if (!sample.allCoreSummary.isEmpty()) state += " / all=" + sample.allCoreSummary;
        return state;
    }

    private static int readThreadProcessor(int tid) {
        if (tid <= 0) return -1;
        File stat = new File("/proc/self/task/" + tid + "/stat");
        try (BufferedReader br = new BufferedReader(new FileReader(stat))) {
            String line = br.readLine();
            if (line == null) return -1;
            int endComm = line.lastIndexOf(')');
            if (endComm < 0 || endComm + 2 >= line.length()) return -1;
            String[] fields = line.substring(endComm + 2).trim().split("\\s+");
            // /proc/[pid]/stat field 39 is the last CPU used. After stripping
            // pid+comm, array index 0 is field 3, therefore processor is 36.
            if (fields.length <= 36) return -1;
            return Integer.parseInt(fields[36]);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static long readCoreCurrentKHz(int cpu) {
        if (cpu < 0) return 0L;
        long v = readLong("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/scaling_cur_freq");
        if (v <= 0) v = readLong("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/cpuinfo_cur_freq");
        return v;
    }

    private static String buildAllCoreFrequencySummary() {
        int count = Math.min(64, Math.max(1, Runtime.getRuntime().availableProcessors()));
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int cpu = 0; cpu < count; ++cpu) {
            if (cpu > 0) sb.append(' ');
            long cur = readCoreCurrentKHz(cpu);
            long max = readCoreMaxKHz(cpu);
            sb.append(cpu).append(':');
            if (cur > 0) sb.append(formatGHz(cur)); else sb.append("n/a");
            sb.append('/');
            if (max > 0) sb.append(formatGHz(max)); else sb.append("n/a");
        }
        sb.append(']');
        return sb.toString();
    }

    private static String formatGHz(long kHz) {
        return String.format(Locale.ROOT, "%.2fGHz", kHz / 1000000.0);
    }

    static String[] buildCoreLabels(int count) {
        int n = clamp(count, 1, 64);
        long[] score = new long[n];
        long maxScore = 0;
        for (int cpu = 0; cpu < n; cpu++) {
            long capacity = readLong("/sys/devices/system/cpu/cpu" + cpu + "/cpu_capacity");
            long freq = readCoreMaxKHz(cpu);
            score[cpu] = capacity > 0 ? capacity * 10000000L + freq : freq;
            if (score[cpu] > maxScore) maxScore = score[cpu];
        }
        String[] labels = new String[n];
        for (int cpu = 0; cpu < n; cpu++) {
            long capacity = readLong("/sys/devices/system/cpu/cpu" + cpu + "/cpu_capacity");
            long freq = readCoreMaxKHz(cpu);
            String speed = freq > 0 ? String.format(Locale.ROOT, "%.2f GHz", freq / 1000000.0) : "freq n/a";
            boolean fast = maxScore > 0 && score[cpu] * 100 >= maxScore * 90;
            labels[cpu] = (fast ? "★ " : "") + "CPU " + cpu + " · " + speed +
                    (capacity > 0 ? " · cap " + capacity : "");
        }
        return labels;
    }

    static long recommendedCoreMask(int count, int wanted) {
        int n = clamp(count, 1, 64);
        int picks = clamp(wanted, 1, n);
        long[] score = new long[n];
        for (int cpu = 0; cpu < n; cpu++) {
            long capacity = readLong("/sys/devices/system/cpu/cpu" + cpu + "/cpu_capacity");
            long freq = readCoreMaxKHz(cpu);
            score[cpu] = capacity > 0 ? capacity * 10000000L + freq : freq;
        }
        long mask = 0L;
        for (int pick = 0; pick < picks; pick++) {
            int bestCpu = -1;
            long best = Long.MIN_VALUE;
            for (int cpu = 0; cpu < n; cpu++) {
                if ((mask & (1L << cpu)) != 0L) continue;
                if (score[cpu] > best) {
                    best = score[cpu];
                    bestCpu = cpu;
                }
            }
            if (bestCpu < 0) break;
            mask |= (1L << bestCpu);
        }
        return mask;
    }


    static long recommendedBigClusterMask(int count, int wanted) {
        int n = clamp(count, 1, 64);
        int picks = clamp(wanted, 1, n);
        long prime = recommendedCoreMask(n, Math.min(2, n));
        long[] score = new long[n];
        for (int cpu = 0; cpu < n; cpu++) {
            long capacity = readLong("/sys/devices/system/cpu/cpu" + cpu + "/cpu_capacity");
            long freq = readCoreMaxKHz(cpu);
            score[cpu] = capacity > 0 ? capacity * 10000000L + freq : freq;
        }
        long mask = 0L;
        for (int pick = 0; pick < picks; pick++) {
            int bestCpu = -1;
            long best = Long.MIN_VALUE;
            for (int cpu = 0; cpu < n; cpu++) {
                if ((prime & (1L << cpu)) != 0L || (mask & (1L << cpu)) != 0L) continue;
                if (score[cpu] > best) { best = score[cpu]; bestCpu = cpu; }
            }
            if (bestCpu < 0) break;
            mask |= 1L << bestCpu;
        }
        return mask != 0L ? mask : recommendedCoreMask(n, picks);
    }

    static long bestLiveCoreMask(int count, int wanted) {
        int n = clamp(count, 1, 64);
        int picks = clamp(wanted, 1, n);
        double[] score = new double[n];
        int readable = 0;
        for (int cpu = 0; cpu < n; cpu++) {
            long cur = readCoreCurrentKHz(cpu);
            long capacity = readLong("/sys/devices/system/cpu/cpu" + cpu + "/cpu_capacity");
            if (cur > 0) {
                readable++;
                score[cpu] = cur * (capacity > 0 ? Math.max(1.0, capacity / 512.0) : 1.0);
            }
        }
        if (readable == 0) return 0L;
        long mask = 0L;
        for (int pick = 0; pick < picks; pick++) {
            int bestCpu = -1;
            double best = -1.0;
            for (int cpu = 0; cpu < n; cpu++) {
                if ((mask & (1L << cpu)) != 0L) continue;
                if (score[cpu] > best) { best = score[cpu]; bestCpu = cpu; }
            }
            if (bestCpu < 0 || best <= 0.0) break;
            mask |= 1L << bestCpu;
        }
        return mask;
    }

    static double averageCurrentKHz(long mask) {
        if (mask == 0L) return 0.0;
        long sum = 0L;
        int count = 0;
        for (int cpu = 0; cpu < 64; cpu++) {
            if ((mask & (1L << cpu)) == 0L) continue;
            long cur = readCoreCurrentKHz(cpu);
            if (cur > 0) { sum += cur; count++; }
        }
        return count > 0 ? sum / (double) count : 0.0;
    }

    static String coreMaskFrequencySummary(long mask) {
        if (mask == 0L) return "Auto";
        StringBuilder sb = new StringBuilder();
        for (int cpu = 0; cpu < 64; cpu++) {
            if ((mask & (1L << cpu)) == 0L) continue;
            if (sb.length() > 0) sb.append(' ');
            long cur = readCoreCurrentKHz(cpu);
            sb.append("CPU").append(cpu).append('=');
            sb.append(cur > 0 ? formatGHz(cur) : "n/a");
        }
        return sb.toString();
    }

    private static long readCoreMaxKHz(int cpu) {
        long v = readLong("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/cpuinfo_max_freq");
        if (v <= 0) v = readLong("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/scaling_max_freq");
        return v;
    }

    private static long detectMaxCpuKHz() {
        long best = 0;
        int count = Math.max(1, Runtime.getRuntime().availableProcessors());
        for (int cpu = 0; cpu < Math.max(count, 16); cpu++) {
            long v = readLong("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/cpuinfo_max_freq");
            if (v <= 0) v = readLong("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/scaling_max_freq");
            if (v > best) best = v;
        }
        return best;
    }

    private static long readLong(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(new File(path)))) {
            String line = br.readLine();
            return line == null ? 0 : Long.parseLong(line.trim());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String readCpuInfo() {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            int lines = 0;
            while ((line = br.readLine()) != null && lines++ < 256)
                sb.append(line).append('\n');
        } catch (Throwable ignored) { }
        return sb.toString();
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
    private static String nonEmpty(String a, String b) { return safe(a).isEmpty() ? safe(b) : safe(a); }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
