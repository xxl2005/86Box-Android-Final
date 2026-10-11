package net.box86.android;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Import/export helper for the shared 86Box ROM library.
 *
 * The Android port stores ROMs in files/86box/roms.  Imports are merged into
 * that directory so a newer official 86Box/roms archive can update existing
 * files without deleting unrelated user ROMs.  ZIP extraction is protected
 * against path traversal and bounded to avoid accidental archive bombs.
 */
final class RomLibraryManager {
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_ZIP_PATH_LENGTH = 4096;
    private static final int MAX_ZIP_DEPTH = 128;
    private static final long MAX_UNCOMPRESSED_BYTES = 4L * 1024L * 1024L * 1024L;
    private static final long ROM_IMPORT_FREE_RESERVE = 64L * 1024L * 1024L;

    static final class Result {
        final int files;
        final long bytes;

        Result(int files, long bytes) {
            this.files = files;
            this.bytes = bytes;
        }
    }

    private RomLibraryManager() { }

    static Result importZip(InputStream rawInput, File romDir) throws IOException {
        if (rawInput == null) throw new IOException("Could not open ROM ZIP");
        File root = romDir.getParentFile();
        if (root == null) throw new IOException("Invalid ROM directory");
        ensureDir(root);
        ensureDir(romDir);

        long initialUsable = root.getUsableSpace();
        if (initialUsable > 0 && initialUsable <= ROM_IMPORT_FREE_RESERVE)
            throw new IOException("Not enough free app storage to import ROM ZIP");

        File staging = new File(root, ".rom-import-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId());
        if (!staging.mkdirs() && !staging.isDirectory()) throw new IOException("Could not create ROM import directory");

        try {
            Result extracted = unzipBounded(rawInput, staging);
            if (extracted.files == 0) throw new IOException("ROM ZIP contains no files");

            File payload = detectPayloadRoot(staging);
            CopyCounter copied = new CopyCounter();
            copyMerge(payload, romDir, copied, true);
            if (copied.files == 0) throw new IOException("ROM ZIP did not contain importable files");
            return new Result(copied.files, copied.bytes);
        } finally {
            deleteRecursive(staging);
        }
    }

    static Result exportZip(File romDir, OutputStream rawOutput) throws IOException {
        if (rawOutput == null) throw new IOException("Could not open ROM ZIP destination");
        ensureDir(romDir);
        Counter counter = new Counter();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(rawOutput))) {
            zip.putNextEntry(new ZipEntry("roms/"));
            zip.closeEntry();
            zipRecursive(romDir, romDir, zip, counter);
        }
        return new Result(counter.files, counter.bytes);
    }

    static Result scan(File romDir) {
        Counter c = new Counter();
        scanRecursive(romDir, c);
        return new Result(c.files, c.bytes);
    }

    private static Result unzipBounded(InputStream rawInput, File dest) throws IOException {
        final String canonicalRoot = dest.getCanonicalPath() + File.separator;
        int entries = 0;
        int files = 0;
        long total = 0;
        byte[] buffer = new byte[1024 * 1024];

        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(rawInput))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("ROM ZIP has too many entries");
                String name = normalizeZipName(entry.getName());
                if (name.isEmpty()) {
                    zip.closeEntry();
                    continue;
                }
                if (name.length() > MAX_ZIP_PATH_LENGTH)
                    throw new IOException("ROM ZIP contains an excessively long path");
                if (zipPathDepth(name) > MAX_ZIP_DEPTH)
                    throw new IOException("ROM ZIP folder nesting is too deep");
                File out = new File(dest, name);
                String canonical = out.getCanonicalPath();
                if (!canonical.startsWith(canonicalRoot)) throw new IOException("Unsafe ZIP path: " + name);

                if (entry.isDirectory()) {
                    ensureDir(out);
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) ensureDir(parent);
                    try (OutputStream output = new BufferedOutputStream(new FileOutputStream(out, false))) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            if (count <= 0) continue;
                            total += count;
                            if (total > MAX_UNCOMPRESSED_BYTES)
                                throw new IOException("ROM ZIP is too large after extraction");
                            long usableNow = dest.getUsableSpace();
                            if (usableNow > 0 && usableNow <= ROM_IMPORT_FREE_RESERVE + count)
                                throw new IOException("ROM ZIP import would leave less than 64 MB free app storage");
                            output.write(buffer, 0, count);
                        }
                    }
                    files++;
                }
                zip.closeEntry();
            }
        }
        return new Result(files, total);
    }

    private static int zipPathDepth(String path) {
        if (path == null || path.isEmpty()) return 0;
        int depth = 1;
        for (int i = 0; i < path.length(); i++)
            if (path.charAt(i) == '/') depth++;
        return depth;
    }

    private static String normalizeZipName(String name) {
        if (name == null) return "";
        String s = name.replace('\\', '/');
        while (s.startsWith("/")) s = s.substring(1);
        if (s.indexOf('\0') >= 0) return "";
        return s;
    }

    /**
     * Accept all common shapes:
     *   roms/...                         (our exports)
     *   roms-main/... / roms-master/... (GitHub Download ZIP)
     *   <single-wrapper>/roms/...
     *   machines/... + video/...        (already flattened archive)
     */
    private static File detectPayloadRoot(File staging) {
        File directRoms = new File(staging, "roms");
        if (directRoms.isDirectory()) return directRoms;

        File[] top = staging.listFiles(file -> !isIgnoredTopLevel(file));
        if (top != null && top.length == 1 && top[0].isDirectory()) {
            File wrapper = top[0];
            File nestedRoms = new File(wrapper, "roms");
            if (nestedRoms.isDirectory()) return nestedRoms;
            return wrapper; // GitHub repo ZIP: roms-main/... or roms-master/...
        }
        return staging;
    }

    private static boolean isIgnoredTopLevel(File file) {
        String n = file.getName();
        return n.equals("__MACOSX") || n.equals(".DS_Store");
    }

    private static void copyMerge(File sourceRoot, File romDir, CopyCounter counter, boolean topLevel) throws IOException {
        File[] children = sourceRoot.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName();
            if (name.equals("__MACOSX") || name.equals(".DS_Store") || name.equals(".git")) continue;
            if (topLevel && isRepositoryMetadata(name)) continue;

            File destination = new File(romDir, name);
            if (child.isDirectory()) {
                ensureDir(destination);
                copyMerge(child, destination, counter, false);
            } else {
                File parent = destination.getParentFile();
                if (parent != null) ensureDir(parent);
                long size = child.length();
                long usable = romDir.getUsableSpace();
                if (usable > 0 && usable <= ROM_IMPORT_FREE_RESERVE + size)
                    throw new IOException("ROM import would leave less than 64 MB free app storage");
                File temp = new File(parent, destination.getName() + ".tmp-rom-import-" + Thread.currentThread().getId());
                try {
                    Files.copy(child.toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    try {
                        Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException atomicUnavailable) {
                        Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    //noinspection ResultOfMethodCallIgnored
                    temp.delete();
                }
                counter.files++;
                counter.bytes += size;
            }
        }
    }

    private static boolean isRepositoryMetadata(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals("readme.md") || n.equals("readme.txt") || n.equals("license") ||
                n.equals("license.txt") || n.equals("copying") || n.equals(".gitignore") ||
                n.equals(".gitattributes") || n.equals(".github");
    }

    private static void zipRecursive(File root, File current, ZipOutputStream zip, Counter counter) throws IOException {
        File[] children = current.listFiles();
        if (children == null) return;
        byte[] buffer = new byte[1024 * 1024];
        for (File child : children) {
            if (child.getName().contains(".tmp-rom-import-")) continue;
            String rel = root.toPath().relativize(child.toPath()).toString().replace('\\', '/');
            String entryName = "roms/" + rel;
            if (child.isDirectory()) {
                zip.putNextEntry(new ZipEntry(entryName + "/"));
                zip.closeEntry();
                zipRecursive(root, child, zip, counter);
            } else {
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream input = new BufferedInputStream(new FileInputStream(child))) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (count > 0) {
                            zip.write(buffer, 0, count);
                            counter.bytes += count;
                        }
                    }
                }
                zip.closeEntry();
                counter.files++;
            }
        }
    }

    private static void scanRecursive(File current, Counter counter) {
        if (current == null || !current.exists()) return;
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) scanRecursive(child, counter);
            else {
                counter.files++;
                counter.bytes += child.length();
            }
        }
    }

    private static void ensureDir(File dir) throws IOException {
        if (dir.isDirectory()) return;
        if (dir.exists() || !dir.mkdirs()) throw new IOException("Could not create directory: " + dir);
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static final class Counter {
        int files;
        long bytes;
    }

    private static final class CopyCounter {
        int files;
        long bytes;
    }
}
