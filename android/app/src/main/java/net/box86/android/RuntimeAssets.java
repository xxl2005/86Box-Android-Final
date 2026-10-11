package net.box86.android;

import android.content.Context;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Extract packaged 86Box ROMs/assets once per installed APK revision.
 *
 * ProfileManager calls this on a worker thread before launching the emulator.
 * Box86Activity calls it as a cheap fallback; normally the completion marker
 * makes that call return immediately.
 */
final class RuntimeAssets {
    private RuntimeAssets() { }

    static void prepare(Context context) throws IOException {
        File root = new File(context.getFilesDir(), "86box");
        File roms = new File(root, "roms");
        File assets = new File(root, "assets");
        File temp = new File(root, "tmp");
        File profiles = new File(root, "profiles");
        ensureDir(root);
        ensureDir(roms);
        ensureDir(assets);
        ensureDir(temp);
        ensureDir(profiles);

        File marker = new File(root, markerName(context));
        if (marker.isFile()) return;

        copyAssetTree(context, "86box/roms", roms);
        copyAssetTree(context, "86box/assets", assets);

        File markerTmp = new File(root, marker.getName() + ".tmp-" + Thread.currentThread().getId());
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(markerTmp, false))) {
            out.write("ok\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
        if (!markerTmp.renameTo(marker)) {
            if (marker.exists() || markerTmp.renameTo(marker)) {
                // done
            } else {
                markerTmp.delete();
                throw new IOException("Could not commit packaged-assets marker");
            }
        }
        cleanupOldMarkers(root, marker.getName());
    }

    private static String markerName(Context context) {
        long updated = 0;
        try {
            updated = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
        } catch (Throwable ignored) { }
        return ".packaged-assets-" + updated + ".complete";
    }

    private static void cleanupOldMarkers(File root, String keep) {
        File[] files = root.listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            if (n.startsWith(".packaged-assets-") && n.endsWith(".complete") && !n.equals(keep))
                //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
    }

    private static void ensureDir(File dir) throws IOException {
        if (dir.isDirectory()) return;
        if (dir.exists() || !dir.mkdirs()) throw new IOException("Could not create directory: " + dir);
    }

    private static void copyAssetTree(Context context, String assetPath, File destination) throws IOException {
        String[] children = context.getAssets().list(assetPath);
        if (children == null) return;
        if (children.length == 0) {
            copyAssetFile(context, assetPath, destination);
            return;
        }
        ensureDir(destination);
        for (String child : children) {
            String childAsset = assetPath + "/" + child;
            String[] grandChildren = context.getAssets().list(childAsset);
            if (grandChildren != null && grandChildren.length > 0)
                copyAssetTree(context, childAsset, new File(destination, child));
            else
                copyAssetFile(context, childAsset, new File(destination, child));
        }
    }

    private static void copyAssetFile(Context context, String assetPath, File destination) throws IOException {
        String name = destination.getName();
        if (name.startsWith("PLACE_") || name.equals(".keep")) return;
        if (destination.isFile()) return; // Preserve existing/user-updated ROMs and assets.

        File parent = destination.getParentFile();
        if (parent != null) ensureDir(parent);
        File tmp = new File(parent, destination.getName() + ".tmp-" + Thread.currentThread().getId());
        try {
            try (InputStream input = context.getAssets().open(assetPath);
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(tmp, false))) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (count > 0) output.write(buffer, 0, count);
                }
            }
            if (destination.exists()) return; // Another preparation won the race.
            if (!tmp.renameTo(destination))
                throw new IOException("Could not commit packaged asset: " + destination);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
