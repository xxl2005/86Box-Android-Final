package net.box86.android;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java desktop -> Android path portability helpers for 86box.cfg. */
public final class ProfilePortability {
    private static final long MAX_IMPORTED_CONFIG_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_IMPORTED_CUE_BYTES = 8L * 1024L * 1024L;

    private ProfilePortability() { }

    public static final class ImportStats {
        public int rewritten = 0;
        public final List<String> unresolved = new ArrayList<>();
    }

    public static ImportStats rewriteDesktopConfig(File cfg, File profile) throws IOException {
        List<File> allFiles = new ArrayList<>();
        collectFiles(profile, allFiles);
        Map<String, List<String>> byBase = new HashMap<>();
        for (File f : allFiles) {
            if (!f.isFile()) continue;
            String rel = relativePath(profile, f).replace('\\', '/');
            byBase.computeIfAbsent(f.getName().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(rel);
        }

        ImportStats stats = new ImportStats();
        if (cfg.length() > MAX_IMPORTED_CONFIG_BYTES)
            throw new IOException("Imported 86box.cfg is unexpectedly large (maximum 16 MB)");
        String configText = decodeImportedText(Files.readAllBytes(cfg.toPath()));
        List<String> lines = new ArrayList<>();
        java.util.Collections.addAll(lines, configText.split("\\r\\n|\\n|\\r", -1));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty() &&
                (configText.endsWith("\n") || configText.endsWith("\r")))
            lines.remove(lines.size() - 1);
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            String trim = line.trim();
            int eq = line.indexOf('=');
            if (eq <= 0 || trim.startsWith(";") || trim.startsWith("#")) {
                out.add(line);
                continue;
            }

            String left = line.substring(0, eq + 1);
            String value = line.substring(eq + 1).trim();
            if (value.isEmpty()) {
                out.add(line);
                continue;
            }

            boolean quoted = value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"");
            String raw = quoted ? value.substring(1, value.length() - 1) : value;
            String prefix = "";
            if (raw.startsWith("wp://")) {
                prefix = "wp://";
                raw = raw.substring(5);
            }

            if (raw.startsWith("ioctl://")) {
                stats.unresolved.add(line.substring(0, eq).trim() + " = " + raw +
                        " (physical host device is not portable to Android)");
                out.add(line);
                continue;
            }

            if (!looksLikePath(raw)) {
                out.add(line);
                continue;
            }

            String normalized = raw.replace('\\', '/');
            String replacement = null;
            if (!isAbsoluteDesktopPath(normalized)) {
                while (normalized.startsWith("./")) normalized = normalized.substring(2);
                String safeRelative = safeRelativePath(profile, normalized);
                if (safeRelative != null) {
                    File local = new File(profile, safeRelative);
                    if (local.exists()) {
                        replacement = safeRelative;
                    } else if (raw.indexOf('\\') >= 0 || !safeRelative.equals(raw)) {
                        // Normalize Windows separators and harmless ./ or a/../b segments,
                        // but never permit a relative path to escape the selected profile.
                        replacement = safeRelative;
                    }
                } else {
                    // A copied desktop image can still be recovered by matching its basename,
                    // but an unresolved ../ path must never point into a sibling Android profile.
                    replacement = findImportedMatch(normalized, byBase);
                    if (replacement == null)
                        stats.unresolved.add(line.substring(0, eq).trim() + " = " + raw +
                                " (relative path escapes imported profile)");
                }
            } else {
                replacement = findImportedMatch(normalized, byBase);
                if (replacement == null)
                    stats.unresolved.add(line.substring(0, eq).trim() + " = " + raw);
            }

            if (replacement != null && !replacement.equals(raw)) {
                String portable = prefix + replacement;
                if (quoted) portable = "\"" + portable + "\"";
                out.add(left + " " + portable);
                stats.rewritten++;
            } else {
                out.add(line);
            }
        }

        Files.write(cfg.toPath(), out, StandardCharsets.UTF_8);
        return stats;
    }

    /** Decode desktop text robustly and normalize it to a Java String. */
    public static String decodeImportedText(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) return "";
        int off = 0;
        Charset charset = null;
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf) {
            off = 3;
            charset = StandardCharsets.UTF_8;
        } else if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe) {
            off = 2;
            charset = StandardCharsets.UTF_16LE;
        } else if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff) {
            off = 2;
            charset = StandardCharsets.UTF_16BE;
        }
        if (charset != null)
            return new String(bytes, off, bytes.length - off, charset);
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (CharacterCodingException badUtf8) {
            // Older Windows 86Box/PCem-era profiles and CUE sheets are commonly ANSI.
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    public static void normalizeCueFiles(File profile) {
        List<File> files = new ArrayList<>();
        collectFiles(profile, files);
        Pattern fileLine = Pattern.compile("(?i)^(\\s*FILE\\s+)(?:\"([^\"]+)\"|(\\S+))(\\s+.*)$");
        for (File cue : files) {
            if (!cue.getName().toLowerCase(Locale.ROOT).endsWith(".cue")) continue;
            try {
                if (cue.length() > MAX_IMPORTED_CUE_BYTES) continue;
                byte[] bytes = Files.readAllBytes(cue.toPath());
                String text = decodeImportedText(bytes);
                String[] lines = text.split("\\r\\n|\\n|\\r", -1);
                StringBuilder rewritten = new StringBuilder(text.length() + 64);
                boolean changed = false;

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    Matcher m = fileLine.matcher(line);
                    if (m.matches()) {
                        String reference = m.group(2) != null ? m.group(2) : m.group(3);
                        File resolved = resolveCueReference(profile, cue.getParentFile(), reference);
                        if (resolved != null) {
                            String portable = relativePath(cue.getParentFile(), resolved).replace('\\', '/');
                            line = m.group(1) + "\"" + portable + "\"" + m.group(4);
                            changed = true;
                        } else if (reference.indexOf('\\') >= 0) {
                            // Android paths use '/'. Even if an external track is unresolved,
                            // normalize Windows separators without touching unrelated CUE text.
                            line = m.group(1) + "\"" + reference.replace('\\', '/') + "\"" + m.group(4);
                            changed = true;
                        }
                    }
                    rewritten.append(line);
                    if (i + 1 < lines.length) rewritten.append('\n');
                }

                // Normalize imported CUE text to UTF-8.  Case-insensitive resolution is
                // important because desktop Windows profiles can refer to TRACK01.BIN
                // while the copied Android file is named track01.bin.
                String normalized = rewritten.toString();
                if (changed || !text.equals(normalized) || !isStrictUtf8(bytes))
                    Files.write(cue.toPath(), normalized.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
                // Keep the original file if it cannot be read or rewritten safely.
            }
        }
    }

    private static boolean isStrictUtf8(byte[] bytes) {
        if (bytes == null) return true;
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static File resolveCueReference(File profile, File cueDir, String reference) {
        if (profile == null || cueDir == null || reference == null || reference.trim().isEmpty()) return null;
        String normalized = reference.trim().replace('\\', '/');
        try {
            java.nio.file.Path profileRoot = profile.toPath().toAbsolutePath().normalize();
            java.nio.file.Path wanted;
            if (isAbsoluteDesktopPath(normalized)) {
                wanted = null;
            } else {
                wanted = cueDir.toPath().toAbsolutePath().normalize().resolve(normalized).normalize();
            }

            if (wanted != null && wanted.startsWith(profileRoot) && !wanted.equals(profileRoot)) {
                File direct = wanted.toFile();
                if (direct.isFile()) return direct;

                // Android filesystems are case-sensitive. Walk each path component
                // case-insensitively to preserve CUEs imported from Windows.
                java.nio.file.Path relative = profileRoot.relativize(wanted);
                File current = profile;
                boolean ok = true;
                for (java.nio.file.Path part : relative) {
                    File[] children = current.listFiles();
                    if (children == null) { ok = false; break; }
                    File match = null;
                    String wantedName = part.toString();
                    for (File child : children) {
                        if (child.getName().equals(wantedName)) { match = child; break; }
                        if (match == null && child.getName().equalsIgnoreCase(wantedName)) match = child;
                    }
                    if (match == null) { ok = false; break; }
                    current = match;
                }
                if (ok && current.isFile()) return current;
            }

            // Last resort for absolute/out-of-tree desktop references: recover only
            // when the basename is unique inside the imported profile.
            String base = normalized.substring(normalized.lastIndexOf('/') + 1);
            if (base.isEmpty()) return null;
            List<File> matches = new ArrayList<>();
            List<File> all = new ArrayList<>();
            collectFiles(profile, all);
            for (File candidate : all)
                if (candidate.isFile() && candidate.getName().equalsIgnoreCase(base)) matches.add(candidate);
            return matches.size() == 1 ? matches.get(0) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void writeImportReport(File profile, ImportStats stats, String source) throws IOException {
        File report = new File(profile, "IMPORT_REPORT.txt");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(report))) {
            w.write("86Box Android desktop profile import\n");
            w.write("Source: " + source + "\n");
            w.write("Rewritten portable paths: " + stats.rewritten + "\n");
            w.write("Unresolved external paths: " + stats.unresolved.size() + "\n\n");
            if (!stats.unresolved.isEmpty()) {
                w.write("These files were outside the imported desktop VM folder or used host-only devices.\n");
                w.write("The original values remain in 86box.cfg so hardware geometry/settings are not silently lost.\n");
                w.write("Use the Android media controls to select replacement images if needed.\n\n");
                for (String s : stats.unresolved) w.write("- " + s + "\n");
            }
        }
    }

    private static boolean looksLikePath(String value) {
        if (value.startsWith("<exe_path>/")) return false;
        return value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.matches("^[A-Za-z]:.*");
    }

    private static boolean isAbsoluteDesktopPath(String value) {
        return value.startsWith("/") || value.startsWith("//") || value.matches("^[A-Za-z]:.*");
    }


    private static String safeRelativePath(File profile, String value) {
        try {
            java.nio.file.Path root = profile.toPath().toAbsolutePath().normalize();
            java.nio.file.Path candidate = root.resolve(value).normalize();
            if (!candidate.startsWith(root) || candidate.equals(root)) return null;
            return root.relativize(candidate).toString().replace('\\', '/');
        } catch (Throwable t) {
            return null;
        }
    }

    private static String findImportedMatch(String original, Map<String, List<String>> byBase) {
        String normalized = original.replace('\\', '/');
        String base = normalized.substring(normalized.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        List<String> candidates = byBase.get(base);
        if (candidates == null || candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        String[] parts = normalized.split("/");
        for (int depth = Math.min(4, parts.length); depth >= 2; --depth) {
            StringBuilder suffix = new StringBuilder();
            for (int i = parts.length - depth; i < parts.length; ++i) {
                if (suffix.length() > 0) suffix.append('/');
                suffix.append(parts[i]);
            }
            String wanted = suffix.toString().toLowerCase(Locale.ROOT);
            String found = null;
            boolean ambiguous = false;
            for (String candidate : candidates) {
                if (candidate.toLowerCase(Locale.ROOT).endsWith(wanted)) {
                    if (found != null) {
                        ambiguous = true;
                        break;
                    }
                    found = candidate;
                }
            }
            if (!ambiguous && found != null) return found;
        }
        return null;
    }

    private static void collectFiles(File root, List<File> out) {
        File[] children = root.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) collectFiles(f, out);
            else out.add(f);
        }
    }

    private static String relativePath(File root, File file) {
        try {
            return root.toPath().relativize(file.toPath()).toString();
        } catch (Throwable t) {
            return file.getName();
        }
    }
}
