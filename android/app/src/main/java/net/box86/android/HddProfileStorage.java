package net.box86.android;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Offline fixed-HDD management for one 86Box hardware profile. */
final class HddProfileStorage {
    /** Android UI currently exposes four editable HDD slots. */
    static final int SLOT_COUNT = 4;
    /** 86Box core HDD_NUM: desktop profiles may contain hdd_01 .. hdd_88. */
    private static final int CORE_HDD_SLOT_COUNT = 88;
    private static final int SCSI_BUS_COUNT = 9;
    private static final int SCSI_ID_COUNT = 16;
    private static final long HDD_IMPORT_FREE_RESERVE = 64L * 1024L * 1024L;

    static final class Result {
        final File file;
        final Geometry geometry;
        final String bus;
        final String channel;
        Result(File file, Geometry geometry, String bus, String channel) {
            this.file = file;
            this.geometry = geometry;
            this.bus = bus;
            this.channel = channel;
        }
    }

    static final class Geometry {
        final int spt;
        final int heads;
        final int cylinders;
        Geometry(int spt, int heads, int cylinders) {
            this.spt = Math.max(1, spt);
            this.heads = Math.max(1, heads);
            this.cylinders = Math.max(1, cylinders);
        }
        long bytes() {
            return (long) spt * (long) heads * (long) cylinders * 512L;
        }
    }

    private HddProfileStorage() { }

    static Result importImage(Context context, File profileDir, File cfgFile, Uri uri, int slot) throws IOException {
        validateSlot(slot);
        File mediaDir = new File(profileDir, "media/hdd");
        if (!mediaDir.isDirectory() && !mediaDir.mkdirs())
            throw new IOException("Could not create profile HDD directory");

        String requested = sanitizeFileName(queryDisplayName(context, uri));
        if (requested.isEmpty()) requested = "hdd" + (slot + 1) + ".img";
        validateExtensionBeforeCopy(requested);

        long declaredSize = querySize(context, uri);
        long usable = mediaDir.getUsableSpace();
        long maxCopyBytes = usable > HDD_IMPORT_FREE_RESERVE ? usable - HDD_IMPORT_FREE_RESERVE : 0L;
        if (usable > 0 && usable <= HDD_IMPORT_FREE_RESERVE)
            throw new IOException("Not enough free app storage to import an HDD image");
        if (declaredSize > 0 && maxCopyBytes > 0 && declaredSize > maxCopyBytes)
            throw new IOException("HDD image is larger than the available app storage (64 MB reserve kept free)");

        File out = uniqueFile(mediaDir, requested);
        copyUri(context, uri, out, maxCopyBytes);

        boolean complete = false;
        try {
            validateImportedImage(out);
            Geometry geometry = detectGeometry(out);
            IniConfig cfg = IniConfig.load(cfgFile);
            BusPlacement placement = choosePlacement(cfg, slot);
            configureSlot(cfg, profileDir, slot, out, geometry, placement);
            cfg.save(cfgFile);
            complete = true;
            return new Result(out, geometry, placement.bus, placement.channel);
        } finally {
            if (!complete && out.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
        }
    }

    static Result createRawImage(File profileDir, File cfgFile, int slot, String requestedName, long sizeMb) throws IOException {
        validateSlot(slot);
        if (sizeMb < 1 || sizeMb > 130000)
            throw new IOException("HDD size must be between 1 and 130000 MB");

        File mediaDir = new File(profileDir, "media/hdd");
        if (!mediaDir.isDirectory() && !mediaDir.mkdirs())
            throw new IOException("Could not create profile HDD directory");

        String name = sanitizeFileName(requestedName == null ? "" : requestedName.trim());
        if (name.isEmpty()) name = "disk" + (slot + 1) + ".img";
        name = normalizeRawFileName(name);
        File out = uniqueFile(mediaDir, name);

        Geometry geometry = calcChs(sizeMb);
        long bytes = geometry.bytes();
        boolean complete = false;
        try (RandomAccessFile raf = new RandomAccessFile(out, "rw")) {
            // Android/Linux creates this as a sparse file on normal app storage,
            // so a large virtual disk does not need to be physically zero-filled.
            raf.setLength(bytes);
            complete = true;
        } finally {
            if (!complete && out.exists()) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
        }

        try {
            IniConfig cfg = IniConfig.load(cfgFile);
            BusPlacement placement = choosePlacement(cfg, slot);
            configureSlot(cfg, profileDir, slot, out, geometry, placement);
            cfg.save(cfgFile);
            return new Result(out, geometry, placement.bus, placement.channel);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            throw e;
        }
    }

    static String slotSummary(File profileDir, IniConfig cfg, int slot) {
        validateSlot(slot);
        String key = key(slot);
        String fn = cfg.get("Hard disks", key + "_fn", "").trim();
        if (fn.isEmpty()) return "HDD " + (slot + 1) + ": empty";

        ParsedParameters p = parseParameters(cfg.get("Hard disks", key + "_parameters", "0, 0, 0, 0, none"));
        String bus = p.bus.isEmpty() ? "none" : p.bus;
        String channel = channelFor(cfg, slot, bus);
        long virtualBytes = (long) p.spt * p.heads * p.cylinders * 512L;

        String normalized = fn.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        StringBuilder s = new StringBuilder("HDD ").append(slot + 1).append(": ").append(base);
        s.append(" • ").append(bus.toUpperCase(Locale.ROOT));
        if (!channel.isEmpty()) s.append(' ').append(channel);
        if (virtualBytes > 0) s.append(" • ").append(humanSize(virtualBytes)).append(" virtual");

        File local = safeProfileFile(profileDir, fn);
        if (local != null && local.isFile()) {
            long actual = local.length();
            if (actual > 0 && Math.abs(actual - virtualBytes) > 1024 * 1024)
                s.append(" • ").append(humanSize(actual)).append(" file");
        } else if (local != null) {
            s.append(" • file missing");
        } else {
            s.append(" • external path");
        }
        return s.toString();
    }

    static boolean slotOccupied(IniConfig cfg, int slot) {
        validateSlot(slot);
        return !cfg.get("Hard disks", key(slot) + "_fn", "").trim().isEmpty();
    }

    static Geometry calcChs(long sizeMb) {
        long ts = sizeMb << 11; // MB -> 512-byte sectors
        long maxTs = 65535L * 16L * 255L;
        if (ts > maxTs) ts = maxTs;
        int spt;
        int heads;
        long cth;
        if (ts >= 65535L * 16L * 63L) {
            spt = 255;
            heads = 16;
            cth = ts / spt;
        } else {
            spt = 17;
            cth = ts / spt;
            heads = (int) ((cth + 1023L) / 1024L);
            if (heads < 4) heads = 4;
            if (cth >= (long) heads * 1024L || heads > 16) {
                spt = 31;
                heads = 16;
                cth = ts / spt;
            }
            if (cth >= (long) heads * 1024L) {
                spt = 63;
                heads = 16;
                cth = ts / spt;
            }
        }
        int cylinders = (int) Math.max(1L, cth / heads);
        return new Geometry(spt, heads, cylinders);
    }

    private static void configureSlot(IniConfig cfg, File profileDir, int slot, File image,
                                      Geometry geometry, BusPlacement placement) {
        String k = key(slot);
        cfg.set("Hard disks", k + "_parameters",
                geometry.spt + ", " + geometry.heads + ", " + geometry.cylinders + ", 0, " + placement.bus);
        cfg.set("Hard disks", k + "_fn", relativePath(profileDir, image));
        cfg.set("Hard disks", k + "_speed", cfg.get("Hard disks", k + "_speed", "1997_5400rpm"));

        // Remove stale addressing left behind when a slot changes bus type.
        cfg.remove("Hard disks", k + "_mfm_channel");
        cfg.remove("Hard disks", k + "_xta_channel");
        cfg.remove("Hard disks", k + "_esdi_channel");
        cfg.remove("Hard disks", k + "_ide_channel");
        cfg.remove("Hard disks", k + "_scsi_location");
        cfg.remove("Hard disks", k + "_scsi_id");

        if ("ide".equals(placement.bus) || "atapi".equals(placement.bus))
            cfg.set("Hard disks", k + "_ide_channel", placement.channel);
        else if ("scsi".equals(placement.bus))
            cfg.set("Hard disks", k + "_scsi_location", placement.channel);
        else if ("mfm".equals(placement.bus))
            cfg.set("Hard disks", k + "_mfm_channel", placement.channel);
        else if ("xta".equals(placement.bus))
            cfg.set("Hard disks", k + "_xta_channel", placement.channel);
        else if ("esdi".equals(placement.bus))
            cfg.set("Hard disks", k + "_esdi_channel", placement.channel);

        // Imported VHD/HDI/HDX files carry their own format metadata. A RAW image
        // has no VHD creation settings either, so stale VHD options must not leak.
        cfg.remove("Hard disks", k + "_vhd_blocksize");
        cfg.remove("Hard disks", k + "_vhd_parent");
        cfg.remove("Hard disks", k + "_raw_device");
    }

    private static BusPlacement choosePlacement(IniConfig cfg, int slot) throws IOException {
        String k = key(slot);
        ParsedParameters old = parseParameters(cfg.get("Hard disks", k + "_parameters", "0, 0, 0, 0, none"));
        String bus = old.bus.toLowerCase(Locale.ROOT);
        if (!(bus.equals("ide") || bus.equals("atapi") || bus.equals("scsi") ||
                bus.equals("mfm") || bus.equals("xta") || bus.equals("esdi")))
            bus = "ide";

        String channel = channelFor(cfg, slot, bus);

        // A desktop-imported profile can already contain a duplicated or invalid
        // bus address. Do not blindly preserve it when replacing the image:
        // validate the old address against every other configured device first.
        if (bus.equals("ide") || bus.equals("atapi")) {
            String normalized = normalizeIdeChannel(channel);
            if (normalized == null || collectUsedIdeChannels(cfg, slot).contains(normalized))
                channel = findFreeIdeChannel(cfg, slot);
            else
                channel = normalized;
            if (channel == null)
                throw new IOException("No free IDE channel is available. Remove or reassign another IDE/ATAPI device first.");
        } else if (bus.equals("scsi")) {
            String normalized = normalizeScsiLocation(channel);
            if (normalized == null || collectUsedScsiLocations(cfg, slot).contains(normalized))
                channel = findFreeScsiLocation(cfg, slot);
            else
                channel = normalized;
            if (channel == null)
                throw new IOException("No free SCSI location is available. Remove or reassign another SCSI device first.");
        } else {
            if (!("0".equals(channel) || "1".equals(channel)) ||
                    collectUsedLegacyChannels(cfg, slot, bus).contains(channel))
                channel = findFreeLegacyChannel(cfg, slot, bus);
            if (channel == null)
                throw new IOException("No free " + bus.toUpperCase(Locale.ROOT) +
                        " HDD channel is available. Remove or reassign another disk first.");
        }
        return new BusPlacement(bus, channel);
    }

    private static boolean hddConfigured(IniConfig cfg, int slot) {
        return !cfg.get("Hard disks", key(slot) + "_fn", "").trim().isEmpty();
    }

    private static Set<String> collectUsedIdeChannels(IniConfig cfg, int targetSlot) {
        Set<String> used = new HashSet<>();

        // 86Box supports HDD_NUM=88. The Android editor currently exposes four
        // slots, but a desktop-imported profile can contain any of the 88 and
        // every one of them must participate in bus-collision detection.
        for (int i = 0; i < CORE_HDD_SLOT_COUNT; i++) {
            if (i == targetSlot) continue;
            String k = key(i);
            ParsedParameters p = parseParameters(cfg.get("Hard disks", k + "_parameters", "0, 0, 0, 0, none"));
            if ((p.bus.equals("ide") || p.bus.equals("atapi")) && hddConfigured(cfg, i))
                addNormalizedIdeChannel(used, cfg.get("Hard disks", k + "_ide_channel", (i >> 1) + ":" + (i & 1)));
        }

        // CD-ROM drives.
        addAtapiChannels(cfg, used, "Floppy and CD-ROM drives", "cdrom_", 8, 0);

        // Legacy ZIP entries and the newer generic removable-disk entries are
        // mutually exclusive in the 86Box loader. Mirror that behaviour here.
        boolean legacyZip = hasLegacyZipEntries(cfg);
        if (legacyZip)
            addAtapiChannels(cfg, used, "Other removable devices", "zip_", 4, 2);
        else
            addAtapiChannels(cfg, used, "Other removable devices", "rdisk_", 4, 2);

        // Magneto-optical and tape devices can also consume ATAPI channels.
        addAtapiChannels(cfg, used, "Other removable devices", "mo_", 4, 2);
        addAtapiChannels(cfg, used, "Other removable devices", "tape_", 4, 2);
        return used;
    }

    private static String findFreeIdeChannel(IniConfig cfg, int targetSlot) {
        Set<String> used = collectUsedIdeChannels(cfg, targetSlot);
        for (int board = 0; board < 4; board++) {
            for (int dev = 0; dev < 2; dev++) {
                String candidate = board + ":" + dev;
                if (!used.contains(candidate)) return candidate;
            }
        }
        return null;
    }

    private static Set<String> collectUsedLegacyChannels(IniConfig cfg, int targetSlot, String bus) {
        Set<String> used = new HashSet<>();
        String suffix = "_" + bus + "_channel";
        for (int i = 0; i < CORE_HDD_SLOT_COUNT; i++) {
            if (i == targetSlot || !hddConfigured(cfg, i)) continue;
            ParsedParameters p = parseParameters(cfg.get("Hard disks", key(i) + "_parameters", "0, 0, 0, 0, none"));
            if (!bus.equals(p.bus)) continue;
            String channel = cfg.get("Hard disks", key(i) + suffix, Integer.toString(i & 1)).trim();
            if ("0".equals(channel) || "1".equals(channel)) used.add(channel);
        }
        return used;
    }

    private static String findFreeLegacyChannel(IniConfig cfg, int targetSlot, String bus) {
        Set<String> used = collectUsedLegacyChannels(cfg, targetSlot, bus);
        if (!used.contains("0")) return "0";
        if (!used.contains("1")) return "1";
        return null;
    }

    private static Set<String> collectUsedScsiLocations(IniConfig cfg, int targetSlot) {
        Set<String> used = new HashSet<>();

        for (int i = 0; i < CORE_HDD_SLOT_COUNT; i++) {
            if (i == targetSlot || !hddConfigured(cfg, i)) continue;
            ParsedParameters p = parseParameters(cfg.get("Hard disks", key(i) + "_parameters", "0, 0, 0, 0, none"));
            if (!"scsi".equals(p.bus)) continue;
            addScsiLocation(used,
                    cfg.get("Hard disks", key(i) + "_scsi_location", ""),
                    cfg.get("Hard disks", key(i) + "_scsi_id", ""),
                    Math.min(15, i + 2));
        }

        addScsiLocations(cfg, used, "Floppy and CD-ROM drives", "cdrom_", 8, 0);
        if (hasLegacyZipEntries(cfg))
            addScsiLocations(cfg, used, "Other removable devices", "zip_", 4, 2);
        else
            addScsiLocations(cfg, used, "Other removable devices", "rdisk_", 4, 2);
        addScsiLocations(cfg, used, "Other removable devices", "mo_", 4, 2);
        addScsiLocations(cfg, used, "Other removable devices", "tape_", 4, 4);
        return used;
    }

    private static String findFreeScsiLocation(IniConfig cfg, int targetSlot) {
        Set<String> used = collectUsedScsiLocations(cfg, targetSlot);
        // Match desktop bus tracking: choose the first unoccupied SCSI bus/ID.
        for (int board = 0; board < SCSI_BUS_COUNT; board++) {
            for (int id = 0; id < SCSI_ID_COUNT; id++) {
                String candidate = board + ":" + String.format(Locale.ROOT, "%02d", id);
                if (!used.contains(candidate)) return candidate;
            }
        }
        return null;
    }

    private static boolean hasLegacyZipEntries(IniConfig cfg) {
        for (int i = 0; i < 4; i++) {
            String prefix = String.format(Locale.ROOT, "zip_%02d_", i + 1);
            if (!cfg.get("Other removable devices", prefix + "parameters", "").trim().isEmpty())
                return true;
        }
        return false;
    }

    private static void addScsiLocations(IniConfig cfg, Set<String> used, String section,
                                         String prefixBase, int count, int defaultOffset) {
        for (int i = 0; i < count; i++) {
            String prefix = String.format(Locale.ROOT, prefixBase + "%02d_", i + 1);
            String params = cfg.get(section, prefix + "parameters", "").trim();
            if (!"scsi".equals(parameterBus(params))) continue;
            addScsiLocation(used,
                    cfg.get(section, prefix + "scsi_location", ""),
                    cfg.get(section, prefix + "scsi_id", ""),
                    "cdrom_".equals(prefixBase) ? (i & 3) : Math.min(15, i + defaultOffset));
        }
    }

    private static void addScsiLocation(Set<String> used, String location, String legacyId, int defaultId) {
        if (location != null && !location.trim().isEmpty()) {
            String[] parts = location.trim().split(":");
            if (parts.length == 2) {
                try {
                    int board = Integer.parseInt(parts[0].trim());
                    int id = Integer.parseInt(parts[1].trim());
                    if (board >= 0 && board < SCSI_BUS_COUNT && id >= 0 && id < SCSI_ID_COUNT) {
                        used.add(board + ":" + String.format(Locale.ROOT, "%02d", id));
                        return;
                    }
                } catch (NumberFormatException ignored) { }
            }
        }

        int id = defaultId;
        if (legacyId != null && !legacyId.trim().isEmpty()) {
            try { id = Integer.parseInt(legacyId.trim()); }
            catch (NumberFormatException ignored) { }
        }
        id = Math.max(0, Math.min(15, id));
        used.add("0:" + String.format(Locale.ROOT, "%02d", id));
    }

    private static void addAtapiChannels(IniConfig cfg, Set<String> used, String section,
                                         String prefixBase, int count, int defaultOffset) {
        for (int i = 0; i < count; i++) {
            String prefix = String.format(Locale.ROOT, prefixBase + "%02d_", i + 1);
            String params = cfg.get(section, prefix + "parameters", "").trim();
            if (!"atapi".equals(parameterBus(params)))
                continue;
            int defaultIndex = "cdrom_".equals(prefixBase) ? (i & 3) : (i + defaultOffset);
            addNormalizedIdeChannel(used,
                    cfg.get(section, prefix + "ide_channel",
                            (defaultIndex >> 1) + ":" + (defaultIndex & 1)));
        }
    }

    private static String parameterBus(String value) {
        if (value == null) return "";
        int comma = value.lastIndexOf(',');
        String bus = comma >= 0 ? value.substring(comma + 1) : value;
        return bus.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeIdeChannel(String value) {
        if (value == null) return null;
        String[] parts = value.trim().split(":");
        if (parts.length != 2) return null;
        try {
            int board = Integer.parseInt(parts[0].trim());
            int dev = Integer.parseInt(parts[1].trim());
            if (board < 0 || board >= 4 || dev < 0 || dev >= 2)
                return null;
            return board + ":" + dev;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeScsiLocation(String value) {
        if (value == null) return null;
        String[] parts = value.trim().split(":");
        if (parts.length != 2) return null;
        try {
            int board = Integer.parseInt(parts[0].trim());
            int id = Integer.parseInt(parts[1].trim());
            if (board < 0 || board >= SCSI_BUS_COUNT || id < 0 || id >= SCSI_ID_COUNT)
                return null;
            return board + ":" + String.format(Locale.ROOT, "%02d", id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void addNormalizedIdeChannel(Set<String> used, String value) {
        String normalized = normalizeIdeChannel(value);
        if (normalized != null) used.add(normalized);
    }

    private static String channelFor(IniConfig cfg, int slot, String bus) {
        String k = key(slot);
        if (bus.equals("ide") || bus.equals("atapi"))
            return cfg.get("Hard disks", k + "_ide_channel", "").trim();
        if (bus.equals("scsi"))
            return cfg.get("Hard disks", k + "_scsi_location", "").trim();
        if (bus.equals("mfm"))
            return cfg.get("Hard disks", k + "_mfm_channel", "").trim();
        if (bus.equals("xta"))
            return cfg.get("Hard disks", k + "_xta_channel", "").trim();
        if (bus.equals("esdi"))
            return cfg.get("Hard disks", k + "_esdi_channel", "").trim();
        return "";
    }

    private static void validateExtensionBeforeCopy(String fileName) throws IOException {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".qcow") || name.endsWith(".qcow2") || name.endsWith(".vdi") ||
                name.endsWith(".vmdk") || name.endsWith(".vhdx")) {
            throw new IOException("Unsupported HDD image format. Use RAW/IMG, HDI, HDX or VHD.");
        }
    }

    private static void validateImportedImage(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Selected HDD image could not be copied");
        if (file.length() < 512) throw new IOException("HDD image is too small (minimum 512 bytes)");

        String name = file.getName().toLowerCase(Locale.ROOT);
        validateExtensionBeforeCopy(name);

        if (name.endsWith(".hdi")) {
            validateHdiHeader(file);
        } else if (name.endsWith(".hdx")) {
            validateHdxHeader(file);
        } else if (name.endsWith(".vhd")) {
            int diskType = validateVhdIntegrity(file);
            Geometry geometry = readVhdGeometry(file);
            if (geometry == null)
                throw new IOException("Invalid VHD geometry. The selected .vhd is not a valid 86Box-compatible VHD image.");
            if (diskType == 4)
                throw new IOException("Differencing VHD images require their parent VHD and cannot be imported as a single HDD file. Use a fixed or dynamic VHD.");
            if (diskType != 2 && diskType != 3)
                throw new IOException("Unsupported VHD disk type " + diskType + ". Use fixed or dynamic VHD.");
        }
    }

    private static void validateHdiHeader(File file) throws IOException {
        if (file.length() < 32) throw new IOException("Invalid HDI image: header is too small");
        byte[] header = new byte[32];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.readFully(header);
        }
        long base = readLe32Unsigned(header, 8);
        long fullSize = readLe32Unsigned(header, 12);
        long sectorSize = readLe32Unsigned(header, 16);
        long spt = readLe32Unsigned(header, 20);
        long heads = readLe32Unsigned(header, 24);
        long cyl = readLe32Unsigned(header, 28);
        long required = base + fullSize;
        if (sectorSize != 512 || base < 32 || base > file.length() || fullSize < 512 ||
                required < base || required > file.length() ||
                spt < 1 || spt > 255 || heads < 1 || heads > 255 || cyl < 1)
            throw new IOException("Invalid or truncated HDI image (header/geometry/data size)");
    }

    private static void validateHdxHeader(File file) throws IOException {
        if (file.length() < 40) throw new IOException("Invalid HDX image: header is too small");
        byte[] header = new byte[40];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.readFully(header);
        }
        long signature = readLe64(header, 0);
        long expected = 0xD778A82044445459L;
        long fullSize = readLe64(header, 8);
        long sectorSize = readLe32Unsigned(header, 16);
        long spt = readLe32Unsigned(header, 20);
        long heads = readLe32Unsigned(header, 24);
        long cyl = readLe32Unsigned(header, 28);
        long required = fullSize > 0 && fullSize <= Long.MAX_VALUE - 40L ? 40L + fullSize : -1L;
        if (signature != expected || sectorSize != 512 || fullSize < 512 ||
                required < 0 || required > file.length() ||
                spt < 1 || spt > 255 || heads < 1 || heads > 255 || cyl < 1)
            throw new IOException("Invalid or truncated HDX image (header/signature/data size)");
    }

    private static Geometry detectGeometry(File file) throws IOException {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".vhd")) {
            Geometry g = readVhdGeometry(file);
            if (g != null) return g;
        } else if (name.endsWith(".hdi") || name.endsWith(".hdx")) {
            Geometry g = readHdiHdxGeometry(file);
            if (g != null) return g;
        }
        long sizeMb = Math.max(1L, (file.length() + 1048575L) / 1048576L);
        return calcChs(sizeMb);
    }

    private static int validateVhdIntegrity(File file) throws IOException {
        if (file.length() < 512) throw new IOException("Invalid VHD: footer is missing or truncated");

        byte[] footer = new byte[512];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(file.length() - 512L);
            raf.readFully(footer);

            if (!hasVhdCookie(footer))
                throw new IOException("Invalid VHD footer cookie");
            if (!validVhdChecksum(footer, 64))
                throw new IOException("Invalid VHD footer checksum");

            int diskType = readBe32(footer, 60);
            if (diskType == 3 || diskType == 4) {
                long sparseOffset = readBe64(footer, 16);
                if (sparseOffset < 0 || sparseOffset > file.length() - 1024L)
                    throw new IOException("Invalid VHD sparse-header offset");

                byte[] sparse = new byte[1024];
                raf.seek(sparseOffset);
                raf.readFully(sparse);
                if (!hasAsciiCookie(sparse, new byte[] {'c','x','s','p','a','r','s','e'}))
                    throw new IOException("Invalid VHD sparse-header cookie");
                if (!validVhdChecksum(sparse, 36))
                    throw new IOException("Invalid VHD sparse-header checksum");
            }
            return diskType;
        }
    }

    private static boolean validVhdChecksum(byte[] block, int checksumOffset) {
        if (block == null || checksumOffset < 0 || checksumOffset + 4 > block.length)
            return false;
        long stored = readBe32Unsigned(block, checksumOffset);
        long sum = 0L;
        for (int i = 0; i < block.length; i++) {
            if (i >= checksumOffset && i < checksumOffset + 4) continue;
            sum = (sum + (block[i] & 0xffL)) & 0xffffffffL;
        }
        long generated = (~sum) & 0xffffffffL;
        return stored == generated;
    }

    private static boolean hasAsciiCookie(byte[] block, byte[] cookie) {
        if (block == null || cookie == null || block.length < cookie.length) return false;
        for (int i = 0; i < cookie.length; i++)
            if (block[i] != cookie[i]) return false;
        return true;
    }

    private static long readBe32Unsigned(byte[] b, int off) {
        return (((long) b[off] & 0xffL) << 24) |
                (((long) b[off + 1] & 0xffL) << 16) |
                (((long) b[off + 2] & 0xffL) << 8) |
                ((long) b[off + 3] & 0xffL);
    }

    private static long readBe64(byte[] b, int off) {
        return (readBe32Unsigned(b, off) << 32) | readBe32Unsigned(b, off + 4);
    }

    private static int readVhdDiskType(File file) {
        if (file.length() < 512) return -1;
        byte[] footer = new byte[512];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(file.length() - 512);
            raf.readFully(footer);
            if (!hasVhdCookie(footer)) return -1;
            return readBe32(footer, 60);
        } catch (Throwable ignored) { }
        return -1;
    }

    private static boolean hasVhdCookie(byte[] footer) {
        if (footer == null || footer.length < 64) return false;
        byte[] cookie = new byte[] {'c','o','n','e','c','t','i','x'};
        for (int i = 0; i < cookie.length; i++)
            if (footer[i] != cookie[i]) return false;
        return true;
    }

    private static int readBe32(byte[] b, int off) {
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16) |
                ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    private static Geometry readVhdGeometry(File file) {
        if (file.length() < 512) return null;
        byte[] footer = new byte[512];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(file.length() - 512);
            raf.readFully(footer);
            if (!hasVhdCookie(footer)) return null;
            int cyl = ((footer[56] & 0xff) << 8) | (footer[57] & 0xff);
            int heads = footer[58] & 0xff;
            int spt = footer[59] & 0xff;
            if (cyl > 0 && heads > 0 && spt > 0) return new Geometry(spt, heads, cyl);
        } catch (Throwable ignored) { }
        return null;
    }

    private static Geometry readHdiHdxGeometry(File file) {
        if (file.length() < 32) return null;
        byte[] header = new byte[32];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.readFully(header);
            int spt = readLe32(header, 20);
            int heads = readLe32(header, 24);
            int cyl = readLe32(header, 28);
            if (spt > 0 && spt <= 255 && heads > 0 && heads <= 255 && cyl > 0)
                return new Geometry(spt, heads, cyl);
        } catch (Throwable ignored) { }
        return null;
    }

    private static int readLe32(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8) |
                ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    private static long readLe32Unsigned(byte[] b, int off) {
        return ((long) b[off] & 0xffL) | (((long) b[off + 1] & 0xffL) << 8) |
                (((long) b[off + 2] & 0xffL) << 16) | (((long) b[off + 3] & 0xffL) << 24);
    }

    private static long readLe64(byte[] b, int off) {
        return readLe32Unsigned(b, off) | (readLe32Unsigned(b, off + 4) << 32);
    }

    private static void copyUri(Context context, Uri uri, File out, long maxBytes) throws IOException {
        boolean complete = false;
        long copied = 0L;
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out, false)) {
            if (in == null) throw new IOException("Could not open selected HDD image");
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n <= 0) continue;
                copied += n;
                if (maxBytes > 0 && copied > maxBytes)
                    throw new IOException("HDD image would leave less than 64 MB free app storage");
                os.write(buffer, 0, n);
            }
            complete = true;
        } finally {
            if (!complete && out.exists()) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
        }
    }

    private static long querySize(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[] { OpenableColumns.SIZE }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx);
            }
        } catch (Throwable ignored) { }
        return -1L;
    }

    private static String queryDisplayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String value = cursor.getString(idx);
                    if (value != null) return value;
                }
            }
        } catch (Throwable ignored) { }
        String last = uri.getLastPathSegment();
        return last == null ? "" : last;
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    }

    private static String normalizeRawFileName(String name) {
        String clean = name == null ? "" : name.trim();
        while (clean.startsWith(".")) clean = clean.substring(1);
        if (clean.isEmpty()) clean = "new_disk";

        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".img") || lower.endsWith(".raw"))
            return clean;

        int dot = clean.lastIndexOf('.');
        String stem = dot > 0 ? clean.substring(0, dot) : clean;
        while (stem.startsWith(".")) stem = stem.substring(1);
        if (stem.isEmpty()) stem = "new_disk";
        return stem + ".img";
    }

    private static File uniqueFile(File dir, String requested) {
        File f = new File(dir, requested);
        if (!f.exists()) return f;
        String stem = requested;
        String ext = "";
        int dot = requested.lastIndexOf('.');
        if (dot > 0) {
            stem = requested.substring(0, dot);
            ext = requested.substring(dot);
        }
        for (int i = 2; ; i++) {
            f = new File(dir, stem + "_" + i + ext);
            if (!f.exists()) return f;
        }
    }

    private static File safeProfileFile(File profileDir, String configured) {
        try {
            File f = new File(configured);
            if (f.isAbsolute()) return null;
            File root = profileDir.getCanonicalFile();
            File candidate = new File(root, configured).getCanonicalFile();
            if (!candidate.getPath().startsWith(root.getPath() + File.separator)) return null;
            return candidate;
        } catch (IOException e) {
            return null;
        }
    }

    private static String relativePath(File root, File file) {
        try {
            return root.toPath().relativize(file.toPath()).toString().replace('\\', '/');
        } catch (Throwable t) {
            return "media/hdd/" + file.getName();
        }
    }

    private static ParsedParameters parseParameters(String value) {
        String[] p = value == null ? new String[0] : value.split(",");
        int spt = p.length > 0 ? parseInt(p[0], 0) : 0;
        int heads = p.length > 1 ? parseInt(p[1], 0) : 0;
        int cylinders = p.length > 2 ? parseInt(p[2], 0) : 0;
        String bus = p.length > 4 ? p[4].trim().toLowerCase(Locale.ROOT) : "none";
        return new ParsedParameters(spt, heads, cylinders, bus);
    }

    private static int parseInt(String value, int def) {
        try { return Integer.parseInt(value.trim()); }
        catch (Throwable t) { return def; }
    }

    private static String key(int slot) {
        return String.format(Locale.ROOT, "hdd_%02d", slot + 1);
    }

    private static void validateSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) throw new IllegalArgumentException("Invalid HDD slot " + slot);
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024.0) return String.format(Locale.ROOT, "%.1f MB", mb);
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    private static final class ParsedParameters {
        final int spt, heads, cylinders;
        final String bus;
        ParsedParameters(int spt, int heads, int cylinders, String bus) {
            this.spt = spt;
            this.heads = heads;
            this.cylinders = cylinders;
            this.bus = bus == null ? "none" : bus;
        }
    }

    private static final class BusPlacement {
        final String bus, channel;
        BusPlacement(String bus, String channel) {
            this.bus = bus;
            this.channel = channel;
        }
    }
}
