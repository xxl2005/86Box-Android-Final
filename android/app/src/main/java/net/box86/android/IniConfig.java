package net.box86.android;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Small line-preserving INI editor for 86box.cfg. */
final class IniConfig {
    private static final long MAX_CONFIG_BYTES = 16L * 1024L * 1024L;
    private final List<String> lines = new ArrayList<>();

    static IniConfig load(File file) throws IOException {
        IniConfig out = new IniConfig();
        if (!file.isFile()) return out;
        long len = file.length();
        if (len > MAX_CONFIG_BYTES)
            throw new IOException("86box.cfg is unexpectedly large (maximum 16 MB)");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.lines.add(line);
        }
        return out;
    }

    String get(String section, String key, String def) {
        boolean inSection = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("[") && t.endsWith("]")) {
                inSection = t.substring(1, t.length() - 1).trim().equalsIgnoreCase(section);
                continue;
            }
            if (!inSection || t.isEmpty() || t.startsWith(";") || t.startsWith("#")) continue;
            int eq = t.indexOf('=');
            if (eq <= 0) continue;
            if (t.substring(0, eq).trim().equalsIgnoreCase(key)) return t.substring(eq + 1).trim();
        }
        return def;
    }

    void set(String section, String key, String value) {
        if (value == null) { remove(section, key); return; }
        int sectionStart = -1;
        int sectionEnd = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("[") && t.endsWith("]")) {
                String s = t.substring(1, t.length() - 1).trim();
                if (sectionStart >= 0) { sectionEnd = i; break; }
                if (s.equalsIgnoreCase(section)) sectionStart = i;
            }
        }
        if (sectionStart < 0) {
            if (!lines.isEmpty() && !lines.get(lines.size()-1).trim().isEmpty()) lines.add("");
            lines.add("[" + section + "]");
            lines.add(key + " = " + value);
            return;
        }
        for (int i = sectionStart + 1; i < sectionEnd; i++) {
            String t = lines.get(i).trim();
            if (t.isEmpty() || t.startsWith(";") || t.startsWith("#")) continue;
            int eq = t.indexOf('=');
            if (eq > 0 && t.substring(0, eq).trim().equalsIgnoreCase(key)) {
                lines.set(i, key + " = " + value);
                return;
            }
        }
        lines.add(sectionEnd, key + " = " + value);
    }

    void remove(String section, String key) {
        boolean inSection = false;
        for (int i = 0; i < lines.size(); ) {
            String t = lines.get(i).trim();
            if (t.startsWith("[") && t.endsWith("]")) {
                inSection = t.substring(1, t.length() - 1).trim().equalsIgnoreCase(section);
                i++;
                continue;
            }
            if (inSection && !t.isEmpty() && !t.startsWith(";") && !t.startsWith("#")) {
                int eq = t.indexOf('=');
                if (eq > 0 && t.substring(0, eq).trim().equalsIgnoreCase(key)) {
                    lines.remove(i);
                    continue;
                }
            }
            i++;
        }
    }

    void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("Could not create " + parent);

        File tmp = new File(parent, file.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp, false);
             BufferedWriter w = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            for (String line : lines) { w.write(line); w.newLine(); }
            w.flush();
            try { fos.getFD().sync(); } catch (IOException ignored) { }
        }

        try {
            Files.move(tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }
}
