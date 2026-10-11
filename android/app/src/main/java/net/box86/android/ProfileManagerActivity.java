package net.box86.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * v0.8.0 VM / hardware profile manager with visual hardware configurator and ROM ZIP management.
 *
 * Each profile is a complete 86Box VM directory (86box.cfg, NVR/CMOS state,
 * media, etc.). Desktop 86Box VM folders can be imported with Android SAF.
 */
public final class ProfileManagerActivity extends Activity {
    public static final String EXTRA_PROFILE_PATH = "net.box86.android.PROFILE_PATH";
    public static final String EXTRA_PROFILE_NAME = "net.box86.android.PROFILE_NAME";
    public static final String EXTRA_OPEN_HW_AFTER_VM = "net.box86.android.OPEN_HW_AFTER_VM";

    private static final int REQ_IMPORT_DESKTOP_FOLDER = 2001;
    private static final int REQ_IMPORT_ZIP = 2002;
    private static final int REQ_EXPORT_ZIP = 2003;
    private static final int REQ_EXPORT_DIAGNOSTICS = 2004;
    private static final int REQ_IMPORT_ROM_ZIP = 2005;
    private static final int REQ_EXPORT_ROMS = 2006;
    private static final int REQ_EXPORT_APP_DIAGNOSTICS = 2007;
    private static final String STATE_EXPORT_PROFILE = "state.exportProfile";
    private static final String STATE_DIAGNOSTICS_PROFILE = "state.diagnosticsProfile";
    private static final int MAX_PROFILE_ZIP_ENTRIES = 100000;
    private static final int MAX_DESKTOP_IMPORT_ENTRIES = 100000;
    private static final int MAX_DESKTOP_IMPORT_DEPTH = 128;
    private static final long PROFILE_IMPORT_FREE_RESERVE = 64L * 1024L * 1024L;

    private File rootDir;
    private File profilesDir;
    private LinearLayout profileList;
    private File pendingExportProfile;
    private File pendingDiagnosticsProfile;
    private volatile boolean profileLaunchInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FullscreenUi.apply(this);
        rootDir = new File(getFilesDir(), "86box");
        profilesDir = new File(rootDir, "profiles");
        //noinspection ResultOfMethodCallIgnored
        profilesDir.mkdirs();
        if (savedInstanceState != null) {
            pendingExportProfile = restorePendingProfile(savedInstanceState.getString(STATE_EXPORT_PROFILE));
            pendingDiagnosticsProfile = restorePendingProfile(savedInstanceState.getString(STATE_DIAGNOSTICS_PROFILE));
        }
        // Migration must happen before manager diagnostics are opened; otherwise the
        // legacy mover could relocate the directory containing the active log file.
        migrateLegacyV04IfNeeded();
        DiagnosticLogger.startManagerSession(this);
        DiagnosticLogger.i("ProfileManager", "Profile manager started");
        if (listProfileDirs().isEmpty()) {
            try { createProfileInternal("Default PC"); } catch (IOException ignored) { }
        }
        buildUi();
        refreshProfiles();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingExportProfile != null) outState.putString(STATE_EXPORT_PROFILE, pendingExportProfile.getAbsolutePath());
        if (pendingDiagnosticsProfile != null) outState.putString(STATE_DIAGNOSTICS_PROFILE, pendingDiagnosticsProfile.getAbsolutePath());
    }

    private File restorePendingProfile(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            File candidate = new File(path).getCanonicalFile();
            String root = profilesDir.getCanonicalPath() + File.separator;
            if (candidate.isDirectory() && candidate.getCanonicalPath().startsWith(root)) return candidate;
        } catch (IOException ignored) { }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FullscreenUi.apply(this);
        DiagnosticLogger.startManagerSession(this);
        if (profileList != null) refreshProfiles();
        maybeOpenHardwareAfterVm();
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Long profile/ROM import-export operations run on worker threads tied to
        // this Activity. Handle tablet rotation/config changes in-place so a
        // rotation cannot orphan the progress dialog or deliver completion to a
        // destroyed Activity instance.
        FullscreenUi.apply(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) FullscreenUi.apply(this);
    }

    private void maybeOpenHardwareAfterVm() {
        Intent intent = getIntent();
        if (intent == null) return;
        String path = intent.getStringExtra(EXTRA_OPEN_HW_AFTER_VM);
        if (path == null || path.trim().isEmpty()) return;
        intent.removeExtra(EXTRA_OPEN_HW_AFTER_VM);

        File profile = new File(path);
        try {
            String base = profilesDir.getCanonicalPath() + File.separator;
            String candidate = profile.getCanonicalPath();
            if (!candidate.startsWith(base) || !profile.isDirectory()) return;
        } catch (IOException e) {
            return;
        }

        // Do not guess a fixed teardown delay. The native SDL/86Box loop can
        // take longer than 500 ms on slower tablets or while flushing guest state.
        // Poll the real native runtime flag and only open the editor after teardown.
        waitForNativeTeardownThenOpen(profile, 0);
    }

    private void waitForNativeTeardownThenOpen(File profile, int attempt) {
        if (isFinishing() || isDestroyed()) return;
        if (!NativeHardwareCatalog.isRuntimeActive()) {
            openHardwareConfigurator(profile);
            return;
        }
        if (attempt >= 100) {
            new AlertDialog.Builder(this)
                    .setTitle("86Box is still shutting down")
                    .setMessage("The emulator did not finish native shutdown within 10 seconds. The hardware editor was not opened to protect 86box.cfg and the native core state. Please try HW again in a moment.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> waitForNativeTeardownThenOpen(profile, attempt + 1), 100L);
    }

    private void buildUi() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("86Box Android – Hardware profiles");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, dp(8));
        outer.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText("Every profile has its own 86box.cfg, NVR/CMOS and media. Desktop 86Box VM folders can be imported directly.");
        hint.setTextSize(14);
        hint.setPadding(0, 0, 0, dp(12));
        outer.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(isCompactLayout() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button add = smallButton("NEW", v -> promptCreateProfile());
        Button importDesktop = smallButton("IMPORT DESKTOP PROFILE", v -> openDesktopFolderPicker());
        actions.addView(add, weighted());
        actions.addView(importDesktop, weighted());
        outer.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout profileZipActions = new LinearLayout(this);
        profileZipActions.setOrientation(isCompactLayout() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        profileZipActions.setGravity(Gravity.CENTER_VERTICAL);
        profileZipActions.setPadding(0, dp(6), 0, 0);
        Button importProfileZip = smallButton("IMPORT PROFILE ZIP", v -> openZipPicker());
        Button exportProfileZip = smallButton("EXPORT PROFILE ZIP", v -> chooseProfileForExport());
        profileZipActions.addView(importProfileZip, weighted());
        profileZipActions.addView(exportProfileZip, weighted());
        outer.addView(profileZipActions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout romActions = new LinearLayout(this);
        romActions.setOrientation(isCompactLayout() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        romActions.setGravity(Gravity.CENTER_VERTICAL);
        romActions.setPadding(0, dp(6), 0, 0);
        Button importRoms = smallButton("IMPORT ROM ZIP", v -> openRomZipPicker());
        Button exportRoms = smallButton("EXPORT ROMS", v -> exportRomsZip());
        romActions.addView(importRoms, weighted());
        romActions.addView(exportRoms, weighted());
        outer.addView(romActions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout diagnosticActions = new LinearLayout(this);
        diagnosticActions.setOrientation(isCompactLayout() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        diagnosticActions.setGravity(Gravity.CENTER_VERTICAL);
        diagnosticActions.setPadding(0, dp(6), 0, 0);
        Button exportAppLog = smallButton("EXPORT COMPLETE APP LOG", v -> exportCompleteAppDiagnostics());
        diagnosticActions.addView(exportAppLog, weighted());
        outer.addView(diagnosticActions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView logHint = new TextView(this);
        logHint.setText("Complete app log records Profile Manager, imports, Hardware Configurator, lifecycle, errors and native session logs. Media images are never exported.");
        logHint.setTextSize(11);
        logHint.setPadding(dp(2), dp(2), dp(2), dp(4));
        outer.addView(logHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView romHint = new TextView(this);
        romHint.setText("ROMs are shared by all profiles. Official 86Box/roms GitHub Download ZIP archives are accepted directly.");
        romHint.setTextSize(12);
        romHint.setPadding(dp(2), dp(4), dp(2), 0);
        outer.addView(romHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView sub = new TextView(this);
        sub.setText("Profiles");
        sub.setTextSize(18);
        sub.setPadding(0, dp(14), 0, dp(6));
        outer.addView(sub);

        profileList = new LinearLayout(this);
        profileList.setOrientation(LinearLayout.VERTICAL);
        outer.addView(profileList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // One page-level ScrollView keeps the complete manager usable on short phones,
        // tablets, desktop mode and unusual aspect ratios.  The previous layout only
        // scrolled the profile list, so the fixed action header could consume most of
        // a small landscape display.
        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setVerticalScrollBarEnabled(true);
        pageScroll.setScrollbarFadingEnabled(false);
        pageScroll.addView(outer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(pageScroll);
    }

    private Button smallButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }

    private LinearLayout.LayoutParams weighted() {
        if (isCompactLayout()) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            return lp;
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private void refreshProfiles() {
        profileList.removeAllViews();
        List<File> profiles = listProfileDirs();
        Collections.sort(profiles, (a, b) -> getProfileName(a).compareToIgnoreCase(getProfileName(b)));
        for (File dir : profiles) addProfileRow(dir);
    }

    private void addProfileRow(File dir) {
        LinearLayout row = new LinearLayout(this);
        boolean compact = isCompactLayout();
        row.setOrientation(compact ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setGravity(compact ? Gravity.START : Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(5), dp(4), dp(5));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(getProfileName(dir));
        name.setTextSize(compact ? 16 : 18);
        TextView details = new TextView(this);
        File cfg = new File(dir, "86box.cfg");
        String hw = cfg.isFile() ? profileHardwareSummary(cfg) : "new profile";
        details.setText(hw + "  •  " + humanSize(directorySize(dir)));
        details.setTextSize(12);
        labels.addView(name);
        labels.addView(details);
        row.addView(labels, new LinearLayout.LayoutParams(
                compact ? ViewGroup.LayoutParams.MATCH_PARENT : 0,
                ViewGroup.LayoutParams.WRAP_CONTENT, compact ? 0f : 1f));

        Button start = smallButton("START", v -> startProfile(dir));
        Button hardware = smallButton("HW", v -> openHardwareConfigurator(dir));
        Button more = smallButton("⋮", v -> showProfileActions(dir));
        if (compact) {
            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setGravity(Gravity.END);
            controls.setPadding(0, dp(4), 0, 0);
            controls.addView(start, new LinearLayout.LayoutParams(0, dp(44), 1f));
            controls.addView(hardware, new LinearLayout.LayoutParams(0, dp(44), 1f));
            controls.addView(more, new LinearLayout.LayoutParams(dp(52), dp(44)));
            row.addView(controls, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            row.addView(start, new LinearLayout.LayoutParams(dp(78), dp(46)));
            row.addView(hardware, new LinearLayout.LayoutParams(dp(58), dp(46)));
            row.addView(more, new LinearLayout.LayoutParams(dp(50), dp(46)));
        }
        profileList.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void startProfile(File dir) {
        if (profileLaunchInProgress) return;
        profileLaunchInProgress = true;
        runBackground("Preparing 86Box ROMs/assets…", () -> {
            try {
                RuntimeAssets.prepare(this);
                runOnUiThread(() -> {
                    try {
                        DiagnosticLogger.i("ProfileManager", "Starting profile: " + getProfileName(dir));
                        Intent i = new Intent(this, Box86Activity.class);
                        i.putExtra(EXTRA_PROFILE_PATH, dir.getAbsolutePath());
                        i.putExtra(EXTRA_PROFILE_NAME, getProfileName(dir));
                        startActivity(i);
                    } finally {
                        profileLaunchInProgress = false;
                    }
                });
            } catch (Exception e) {
                profileLaunchInProgress = false;
                throw e;
            }
        });
    }


    private void openHardwareConfigurator(File dir) {
        logCpuConfigSnapshot("open hardware configurator", new File(dir, "86box.cfg"));
        DiagnosticLogger.i("ProfileManager", "Opening HardwareConfig profile=" + getProfileName(dir) + " path=" + dir.getAbsolutePath());
        Intent i = new Intent(this, HardwareConfigActivity.class);
        i.putExtra(EXTRA_PROFILE_PATH, dir.getAbsolutePath());
        i.putExtra(EXTRA_PROFILE_NAME, getProfileName(dir));
        startActivity(i);
    }

    private void showProfileActions(File dir) {
        String[] actions = {"Start", "Hardware configurator", "Rename", "Duplicate", "Export ZIP", "Export diagnostics", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(getProfileName(dir))
                .setItems(actions, (d, which) -> {
                    if (which == 0) startProfile(dir);
                    else if (which == 1) openHardwareConfigurator(dir);
                    else if (which == 2) promptRenameProfile(dir);
                    else if (which == 3) promptDuplicateProfile(dir);
                    else if (which == 4) exportProfileZip(dir);
                    else if (which == 5) exportProfileDiagnostics(dir);
                    else if (which == 6) confirmDelete(dir);
                }).show();
    }

    private void promptCreateProfile() {
        promptForName("New hardware profile", "New PC", name -> runBackground("Creating profile…", () -> {
            File created = createProfileInternal(name);
            runOnUiThread(() -> {
                refreshProfiles();
                openHardwareConfigurator(created);
            });
        }));
    }

    private void promptRenameProfile(File dir) {
        promptForName("Rename profile", getProfileName(dir), name -> {
            try {
                writeProfileMetadata(dir, name, getProfileSource(dir));
                refreshProfiles();
            } catch (IOException e) { showError("Rename failed", e); }
        });
    }

    private void promptDuplicateProfile(File dir) {
        promptForName("Duplicate profile", getProfileName(dir) + " Copy", name -> runBackground("Duplicating profile…", () -> {
            File out = newProfileDir(name);
            try {
                ensureDuplicateFits(dir, profilesDir);
                copyDirectory(dir, out);
                writeProfileMetadata(out, name, "duplicate:" + getProfileName(dir));
            } catch (Exception e) {
                deleteRecursive(out);
                throw e;
            }
            runOnUiThread(this::refreshProfiles);
        }));
    }

    private interface NameCallback { void accept(String value); }

    private void promptForName(String title, String initial, NameCallback callback) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        int pad = dp(20);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(pad, 0, pad, 0);
        wrap.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle(title).setView(wrap).setNegativeButton("Cancel", null)
                .setPositiveButton("OK", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "PC";
                    callback.accept(name);
                }).show();
    }

    private void confirmDelete(File dir) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + getProfileName(dir) + "?")
                .setMessage("This deletes the profile configuration, NVR/CMOS and media copied into this profile.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> runBackground("Deleting profile…", () -> {
                    deleteRecursive(dir);
                    runOnUiThread(this::refreshProfiles);
                })).show();
    }

    private void openDesktopFolderPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_IMPORT_DESKTOP_FOLDER);
    }

    private void openZipPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQ_IMPORT_ZIP);
    }

    private void openRomZipPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQ_IMPORT_ROM_ZIP);
    }

    private void exportRomsZip() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, "86Box-ROMs-" + BuildConfig.VERSION_NAME + ".zip");
        startActivityForResult(i, REQ_EXPORT_ROMS);
    }

    private void chooseProfileForExport() {
        List<File> profiles = listProfileDirs();
        Collections.sort(profiles, (a, b) -> getProfileName(a).compareToIgnoreCase(getProfileName(b)));

        if (profiles.isEmpty()) {
            Toast.makeText(this, "No profiles available to export", Toast.LENGTH_LONG).show();
            return;
        }

        String[] names = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); ++i) {
            names[i] = getProfileName(profiles.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("Export profile ZIP")
                .setItems(names, (dialog, which) -> {
                    if (which >= 0 && which < profiles.size()) {
                        exportProfileZip(profiles.get(which));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void exportProfileZip(File profile) {
        pendingExportProfile = profile;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, safeFileName(getProfileName(profile)) + "-86Box-profile.zip");
        startActivityForResult(i, REQ_EXPORT_ZIP);
    }

    private void exportCompleteAppDiagnostics() {
        DiagnosticLogger.i("ProfileManager", "Opening complete app diagnostics export picker");
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, "86Box-Android-v" + BuildConfig.VERSION_NAME + "-COMPLETE-APP-LOG.zip");
        startActivityForResult(i, REQ_EXPORT_APP_DIAGNOSTICS);
    }

    private void exportProfileDiagnostics(File profile) {
        pendingDiagnosticsProfile = profile;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, safeFileName(getProfileName(profile)) + "-86Box-diagnostics.zip");
        startActivityForResult(i, REQ_EXPORT_DIAGNOSTICS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        FullscreenUi.apply(this);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            DiagnosticLogger.i("ProfileManager", "Activity result cancelled/empty request=" + requestCode + " result=" + resultCode);
            if (requestCode == REQ_EXPORT_ZIP) pendingExportProfile = null;
            if (requestCode == REQ_EXPORT_DIAGNOSTICS) pendingDiagnosticsProfile = null;
            return;
        }
        Uri uri = data.getData();
        DiagnosticLogger.i("ProfileManager", "Activity result request=" + requestCode + " uri=" + uri);
        if (requestCode == REQ_IMPORT_DESKTOP_FOLDER) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) { }
            runBackground("Importing desktop 86Box profile…", () -> importDesktopFolder(uri));
        } else if (requestCode == REQ_IMPORT_ZIP) {
            runBackground("Importing 86Box profile ZIP…", () -> importProfileZip(uri));
        } else if (requestCode == REQ_EXPORT_ZIP && pendingExportProfile != null) {
            File profile = pendingExportProfile;
            pendingExportProfile = null;
            runBackground("Exporting profile…", () -> {
                try (OutputStream out = openCreateOutputStream(uri)) {
                    if (out == null) throw new IOException("Could not open destination");
                    zipDirectory(profile, out);
                }
                DiagnosticLogger.i("ProfileManager", "Profile ZIP exported: " + getProfileName(profile));
                runOnUiThread(() -> Toast.makeText(this, "Profile exported", Toast.LENGTH_LONG).show());
            });
        } else if (requestCode == REQ_EXPORT_DIAGNOSTICS && pendingDiagnosticsProfile != null) {
            File profile = pendingDiagnosticsProfile;
            pendingDiagnosticsProfile = null;
            runBackground("Exporting diagnostics…", () -> {
                DiagnosticLogger.exportDiagnostics(this, profile, getProfileName(profile), uri);
                DiagnosticLogger.i("ProfileManager", "Diagnostics ZIP exported: " + getProfileName(profile));
                runOnUiThread(() -> Toast.makeText(this, "Diagnostics exported", Toast.LENGTH_LONG).show());
            });
        } else if (requestCode == REQ_IMPORT_ROM_ZIP) {
            runBackground("Importing ROM ZIP…", () -> {
                RuntimeAssets.prepare(this);
                File romDir = new File(rootDir, "roms");
                RomLibraryManager.Result result;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    result = RomLibraryManager.importZip(in, romDir);
                }
                DiagnosticLogger.i("ProfileManager", "ROM ZIP imported: " + result.files + " files / " + humanSize(result.bytes));
                RomLibraryManager.Result finalResult = result;
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("ROM ZIP imported")
                        .setMessage("Imported/updated: " + finalResult.files + " files\nSize: " + humanSize(finalResult.bytes) +
                                "\n\nROMs are now available to all hardware profiles.")
                        .setPositiveButton("OK", null).show());
            });
        } else if (requestCode == REQ_EXPORT_APP_DIAGNOSTICS) {
            runBackground("Exporting complete app log…", () -> {
                DiagnosticLogger.exportAppDiagnostics(this, uri);
                DiagnosticLogger.i("ProfileManager", "Complete app diagnostics ZIP exported");
                runOnUiThread(() -> Toast.makeText(this, "Complete app log exported", Toast.LENGTH_LONG).show());
            });
        } else if (requestCode == REQ_EXPORT_ROMS) {
            runBackground("Exporting ROM library…", () -> {
                RuntimeAssets.prepare(this);
                File romDir = new File(rootDir, "roms");
                RomLibraryManager.Result result;
                try (OutputStream out = openCreateOutputStream(uri)) {
                    if (out == null) throw new IOException("Could not open destination");
                    result = RomLibraryManager.exportZip(romDir, out);
                }
                DiagnosticLogger.i("ProfileManager", "ROM library exported: " + result.files + " files / " + humanSize(result.bytes));
                RomLibraryManager.Result finalResult = result;
                runOnUiThread(() -> Toast.makeText(this,
                        "ROMs exported: " + finalResult.files + " files (" + humanSize(finalResult.bytes) + ")",
                        Toast.LENGTH_LONG).show());
            });
        }
    }

    private void importDesktopFolder(Uri treeUri) throws Exception {
        String display = queryTreeName(treeUri);
        if (display == null || display.trim().isEmpty()) display = "Desktop PC";
        DiagnosticLogger.i("ProfileManager", "Desktop import BEGIN display=" + display + " uri=" + treeUri);
        File temp = new File(profilesDir, ".import-" + System.currentTimeMillis());
        if (!temp.mkdirs()) throw new IOException("Could not create import directory");
        try {
            copyDocumentTree(treeUri, temp);
            File cfg = normalizeImportedRoot(temp);
            logCpuConfigSnapshot("desktop import raw", cfg);
            File original = new File(temp, "86box.desktop-original.cfg");
            Files.copy(cfg.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ProfilePortability.ImportStats stats = ProfilePortability.rewriteDesktopConfig(cfg, temp);
            logCpuConfigSnapshot("desktop import rewritten", cfg);
            ProfilePortability.normalizeCueFiles(temp);
            String name = uniqueDisplayName(display);
            writeProfileMetadata(temp, name, "desktop-folder");
            ProfilePortability.writeImportReport(temp, stats, "Desktop folder: " + display);
            File finalDir = newProfileDir(name);
            moveDirectory(temp, finalDir);
            logCpuConfigSnapshot("desktop import final", new File(finalDir, "86box.cfg"));
            DiagnosticLogger.i("ProfileManager", "Desktop import END profile=" + name + " path=" + finalDir.getAbsolutePath() +
                    " rewrittenPaths=" + stats.rewritten + " unresolved=" + stats.unresolved.size());
            runOnUiThread(() -> {
                refreshProfiles();
                new AlertDialog.Builder(this).setTitle("Desktop profile imported")
                        .setMessage("Profile: " + name + "\nRewritten paths: " + stats.rewritten + "\nUnresolved external paths: " + stats.unresolved.size() +
                                (stats.unresolved.isEmpty() ? "" : "\n\nSee IMPORT_REPORT.txt inside the profile."))
                        .setPositiveButton("OK", null).show();
            });
        } catch (Exception e) {
            deleteRecursive(temp);
            throw e;
        }
    }

    private void importProfileZip(Uri uri) throws Exception {
        String display = queryDisplayName(uri);
        if (display == null || display.isEmpty()) display = "Imported Profile";
        display = display.replaceFirst("(?i)\\.zip$", "");
        File temp = new File(profilesDir, ".zip-" + System.currentTimeMillis());
        if (!temp.mkdirs()) throw new IOException("Could not create import directory");
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open ZIP");
            unzipToDirectory(in, temp);
            File cfg = normalizeImportedRoot(temp);
            logCpuConfigSnapshot("desktop import raw", cfg);
            File original = new File(temp, "86box.desktop-original.cfg");
            Files.copy(cfg.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ProfilePortability.ImportStats stats = ProfilePortability.rewriteDesktopConfig(cfg, temp);
            logCpuConfigSnapshot("desktop import rewritten", cfg);
            ProfilePortability.normalizeCueFiles(temp);
            String name = uniqueDisplayName(display);
            writeProfileMetadata(temp, name, "zip-import");
            ProfilePortability.writeImportReport(temp, stats, "ZIP: " + display);
            File finalDir = newProfileDir(name);
            moveDirectory(temp, finalDir);
            runOnUiThread(() -> {
                refreshProfiles();
                Toast.makeText(this, "Profile imported: " + name, Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            deleteRecursive(temp);
            throw e;
        }
    }

    private File normalizeImportedRoot(File root) throws IOException {
        File direct = new File(root, "86box.cfg");
        if (direct.isFile()) return direct;
        List<File> configs = new ArrayList<>();
        findFilesNamed(root, "86box.cfg", configs);
        if (configs.size() != 1)
            throw new IOException(configs.isEmpty() ? "No 86box.cfg found in selected profile" : "More than one 86box.cfg found; select one VM folder only");
        File cfg = configs.get(0);
        File parent = cfg.getParentFile();
        if (parent != null && !parent.equals(root)) {
            File staging = new File(root.getParentFile(), root.getName() + "-normalized");
            if (!staging.mkdirs()) throw new IOException("Could not normalize imported folder");
            boolean completed = false;
            try {
                File[] children = parent.listFiles();
                if (children != null) for (File child : children) moveDirectory(child, new File(staging, child.getName()));
                deleteRecursive(root);
                if (!staging.renameTo(root)) {
                    copyDirectory(staging, root);
                    deleteRecursive(staging);
                }
                completed = true;
            } finally {
                // On an interrupted/failed normalization, staging is a sibling of root
                // and would otherwise survive the outer temp-directory cleanup.
                if (!completed) deleteRecursive(staging);
            }
        }
        direct = new File(root, "86box.cfg");
        if (!direct.isFile()) throw new IOException("Could not normalize 86box.cfg");
        return direct;
    }

    private static final class ImportCopyBudget {
        final long maxBytes;
        long bytes;
        int entries;

        ImportCopyBudget(long maxBytes) {
            this.maxBytes = Math.max(0L, maxBytes);
        }

        void addEntry() throws IOException {
            if (++entries > MAX_DESKTOP_IMPORT_ENTRIES)
                throw new IOException("Desktop profile contains too many files/folders");
        }

        void preflight(long declaredBytes) throws IOException {
            if (declaredBytes > 0 && maxBytes > 0 && declaredBytes > maxBytes - bytes)
                throw new IOException("Desktop profile is larger than the available app storage");
        }

        void addBytes(int count) throws IOException {
            bytes += count;
            if (maxBytes > 0 && bytes > maxBytes)
                throw new IOException("Desktop profile import would leave less than 64 MB free app storage");
        }
    }

    private void copyDocumentTree(Uri treeUri, File destination) throws IOException {
        if (!destination.isDirectory() && !destination.mkdirs())
            throw new IOException("Could not create desktop import directory");
        long usable = destination.getUsableSpace();
        if (usable > 0 && usable <= PROFILE_IMPORT_FREE_RESERVE)
            throw new IOException("Not enough free app storage to import desktop profile");
        long maxBytes = usable > 0 ? usable - PROFILE_IMPORT_FREE_RESERVE : 0L;

        ContentResolver cr = getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri rootDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
        copyDocumentChildren(cr, treeUri, rootDoc, destination, new ImportCopyBudget(maxBytes), 0);
    }

    private void copyDocumentChildren(ContentResolver cr, Uri treeUri, Uri parentDoc, File dest,
                                      ImportCopyBudget budget, int depth) throws IOException {
        if (depth > MAX_DESKTOP_IMPORT_DEPTH)
            throw new IOException("Desktop profile folder nesting is too deep");
        if (!dest.isDirectory() && !dest.mkdirs())
            throw new IOException("Could not create import directory");

        String parentId = DocumentsContract.getDocumentId(parentDoc);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        };
        try (Cursor cursor = cr.query(childrenUri, projection, null, null, null)) {
            if (cursor == null) throw new IOException("Could not enumerate selected folder");
            while (cursor.moveToNext()) {
                budget.addEntry();
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                long declaredSize = cursor.isNull(3) ? -1L : cursor.getLong(3);
                if (name == null) continue;
                name = sanitizeImportedEntryName(name);
                Uri child = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                File out = uniqueFile(dest, name.isEmpty() ? "item" : name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    copyDocumentChildren(cr, treeUri, child, out, budget, depth + 1);
                } else {
                    budget.preflight(declaredSize);
                    boolean complete = false;
                    try (InputStream in = cr.openInputStream(child);
                         OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        if (in == null) throw new IOException("Could not open " + name);
                        copyStreamBounded(in, os, budget);
                        complete = true;
                    } finally {
                        if (!complete && out.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            out.delete();
                        }
                    }
                }
            }
        }
    }

    private static int zipPathDepth(String path) {
        if (path == null || path.isEmpty()) return 0;
        int depth = 1;
        for (int i = 0; i < path.length(); i++)
            if (path.charAt(i) == '/') depth++;
        return depth;
    }

    private static void unzipToDirectory(InputStream input, File dest) throws IOException {
        String canonicalRoot = dest.getCanonicalPath() + File.separator;
        long usable = dest.getUsableSpace();
        if (usable > 0 && usable <= PROFILE_IMPORT_FREE_RESERVE)
            throw new IOException("Not enough free app storage to import a profile ZIP");
        long maxExtracted = usable > 0 ? usable - PROFILE_IMPORT_FREE_RESERVE : 0L;
        long extracted = 0L;
        int entries = 0;

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry e;
            byte[] buf = new byte[1024 * 1024];
            while ((e = zis.getNextEntry()) != null) {
                if (++entries > MAX_PROFILE_ZIP_ENTRIES)
                    throw new IOException("Profile ZIP contains too many entries");

                String entryName = e.getName().replace('\\', '/');
                if (entryName.length() > 4096)
                    throw new IOException("Profile ZIP contains an excessively long path");
                if (zipPathDepth(entryName) > MAX_DESKTOP_IMPORT_DEPTH)
                    throw new IOException("Profile ZIP folder nesting is too deep");

                File out = new File(dest, entryName);
                String canonical = out.getCanonicalPath();
                if (!canonical.startsWith(canonicalRoot))
                    throw new IOException("Unsafe ZIP path: " + entryName);

                if (e.isDirectory()) {
                    if (!out.mkdirs() && !out.isDirectory())
                        throw new IOException("Could not create " + entryName);
                } else {
                    long declared = e.getSize();
                    if (maxExtracted > 0 && declared > 0 && declared > (maxExtracted - extracted))
                        throw new IOException("Profile ZIP is larger than the available app storage");

                    File parent = out.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory())
                        throw new IOException("Could not create " + parent);

                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        int n;
                        while ((n = zis.read(buf)) > 0) {
                            extracted += n;
                            if (maxExtracted > 0 && extracted > maxExtracted)
                                throw new IOException("Profile ZIP would leave less than 64 MB free app storage");
                            os.write(buf, 0, n);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void zipDirectory(File profile, OutputStream output) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(output))) {
            zipRecursive(profile, profile, zos);
        }
    }

    private static void zipRecursive(File root, File current, ZipOutputStream zos) throws IOException {
        File[] children = current.listFiles();
        if (children == null) return;
        byte[] buffer = new byte[1024 * 1024];
        for (File child : children) {
            String rel = relativePath(root, child).replace('\\', '/');
            if (child.isDirectory()) {
                zos.putNextEntry(new ZipEntry(rel + "/")); zos.closeEntry();
                zipRecursive(root, child, zos);
            } else {
                zos.putNextEntry(new ZipEntry(rel));
                try (InputStream in = new BufferedInputStream(new FileInputStream(child))) {
                    int n; while ((n = in.read(buffer)) > 0) zos.write(buffer, 0, n);
                }
                zos.closeEntry();
            }
        }
    }

    private File createProfileInternal(String name) throws IOException {
        File dir = newProfileDir(name);
        if (!dir.mkdirs() && !dir.isDirectory()) throw new IOException("Could not create profile");
        new File(dir, "media/floppy").mkdirs();
        new File(dir, "media/cdrom").mkdirs();
        new File(dir, "media/hdd").mkdirs();
        writeProfileMetadata(dir, name, "android");
        return dir;
    }

    private File newProfileDir(String name) {
        String id = safeFileName(name).replace(' ', '_');
        while (id.startsWith(".")) id = id.substring(1);
        if (id.isEmpty()) id = "PC";
        File dir = new File(profilesDir, id);
        int n = 2;
        while (dir.exists()) dir = new File(profilesDir, id + "_" + n++);
        return dir;
    }

    private String uniqueDisplayName(String base) {
        String name = base == null || base.trim().isEmpty() ? "Imported PC" : base.trim();
        List<String> names = new ArrayList<>();
        for (File f : listProfileDirs()) names.add(getProfileName(f).toLowerCase(Locale.ROOT));
        if (!names.contains(name.toLowerCase(Locale.ROOT))) return name;
        for (int i = 2; ; ++i) {
            String candidate = name + " " + i;
            if (!names.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        }
    }

    private List<File> listProfileDirs() {
        List<File> result = new ArrayList<>();
        File[] items = profilesDir.listFiles();
        if (items != null) for (File f : items) if (f.isDirectory() && !f.getName().startsWith(".")) result.add(f);
        return result;
    }

    private void writeProfileMetadata(File dir, String name, String source) throws IOException {
        Properties p = new Properties();
        p.setProperty("name", name);
        p.setProperty("source", source == null ? "" : source);
        p.setProperty("updated", Long.toString(System.currentTimeMillis()));
        File meta = new File(dir, "profile.properties");
        try (OutputStream out = new FileOutputStream(meta)) { p.store(out, "86Box Android hardware profile"); }
    }

    private String getProfileName(File dir) {
        Properties p = readProfileMetadata(dir);
        return p.getProperty("name", dir.getName().replace('_', ' '));
    }

    private String getProfileSource(File dir) { return readProfileMetadata(dir).getProperty("source", ""); }

    private Properties readProfileMetadata(File dir) {
        Properties p = new Properties();
        File meta = new File(dir, "profile.properties");
        if (meta.isFile()) try (InputStream in = new FileInputStream(meta)) { p.load(in); } catch (IOException ignored) { }
        return p;
    }

    private void migrateLegacyV04IfNeeded() {
        if (!listProfileDirs().isEmpty()) return;
        File legacyCfg = new File(rootDir, "86box.cfg");
        if (!legacyCfg.exists()) return;
        File target = new File(profilesDir, "Legacy_v0.4");
        //noinspection ResultOfMethodCallIgnored
        target.mkdirs();
        File[] items = rootDir.listFiles();
        if (items != null) {
            for (File f : items) {
                String n = f.getName();
                if (n.equals("roms") || n.equals("assets") || n.equals("profiles") || n.equals("tmp") || n.equals("manager-diagnostics")) continue;
                try { moveDirectory(f, new File(target, n)); } catch (IOException ignored) { }
            }
        }
        try { writeProfileMetadata(target, "Legacy v0.4", "automatic-migration"); } catch (IOException ignored) { }
    }

    private void logCpuConfigSnapshot(String stage, File cfg) {
        if (cfg == null) {
            DiagnosticLogger.w("CpuTrace", stage + ": cfg=null");
            return;
        }
        try {
            IniConfig c = IniConfig.load(cfg);
            DiagnosticLogger.i("CpuTrace", stage +
                    " cfg=" + cfg.getAbsolutePath() +
                    " machine=" + c.get("Machine", "machine", "<missing>") +
                    " cpu_family=" + c.get("Machine", "cpu_family", "<missing>") +
                    " cpu_speed=" + c.get("Machine", "cpu_speed", "<missing>") +
                    " cpu_multi=" + c.get("Machine", "cpu_multi", "<missing>") +
                    " cpu_engine=" + c.get("Machine", "android_cpu_engine", "native") +
                    " fpu_type=" + c.get("Machine", "fpu_type", "<missing>") +
                    " mem_size=" + c.get("Machine", "mem_size", "<missing>"));
        } catch (Throwable t) {
            DiagnosticLogger.w("CpuTrace", stage + ": could not read " + cfg, t);
        }
    }

    private String queryTreeName(Uri uri) {
        try {
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            try (Cursor c = getContentResolver().query(doc, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) return c.getString(0);
            }
        } catch (Throwable ignored) { }
        return "Desktop PC";
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Throwable ignored) { }
        return uri.getLastPathSegment();
    }

    private OutputStream openCreateOutputStream(Uri uri) throws IOException {
        IOException first = null;
        try {
            OutputStream out = getContentResolver().openOutputStream(uri, "rwt");
            if (out != null) return out;
        } catch (IOException | IllegalArgumentException e) {
            first = e instanceof IOException ? (IOException) e : new IOException(e);
        }
        try {
            OutputStream out = getContentResolver().openOutputStream(uri, "w");
            if (out != null) return out;
            throw new IOException("Could not open destination");
        } catch (IOException | IllegalArgumentException e) {
            IOException second = e instanceof IOException ? (IOException) e : new IOException(e);
            if (first != null) second.addSuppressed(first);
            throw second;
        }
    }

    private void runBackground(String message, ThrowingRunnable work) {
        ProgressBar bar = new ProgressBar(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(dp(24), dp(20), dp(24), dp(20));
        wrap.addView(bar);
        AlertDialog progress = new AlertDialog.Builder(this).setTitle(message).setView(wrap).setCancelable(false).create();
        progress.show();
        new Thread(() -> {
            long started = System.currentTimeMillis();
            DiagnosticLogger.i("ProfileManager", "Background BEGIN: " + message);
            try {
                work.run();
                DiagnosticLogger.i("ProfileManager", "Background END: " + message + " elapsedMs=" + (System.currentTimeMillis() - started));
            }
            catch (Throwable e) {
                DiagnosticLogger.e("ProfileManager", "Background FAILED: " + message + " elapsedMs=" + (System.currentTimeMillis() - started), e);
                runOnUiThread(() -> showError("Operation failed", e));
            }
            finally { runOnUiThread(progress::dismiss); }
        }, "86Box-profile-worker").start();
    }

    private interface ThrowingRunnable { void run() throws Exception; }

    private void showError(String title, Throwable e) {
        DiagnosticLogger.e("ProfileManager", title, e);
        new AlertDialog.Builder(this).setTitle(title).setMessage(e.getMessage() == null ? e.toString() : e.getMessage()).setPositiveButton("OK", null).show();
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1024 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static void copyStreamBounded(InputStream in, OutputStream out, ImportCopyBudget budget) throws IOException {
        byte[] buf = new byte[1024 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            budget.addBytes(n);
            out.write(buf, 0, n);
        }
    }

    private static void ensureDuplicateFits(File src, File storageRoot) throws IOException {
        long usable = storageRoot.getUsableSpace();
        if (usable > 0 && usable <= PROFILE_IMPORT_FREE_RESERVE)
            throw new IOException("Not enough free app storage to duplicate profile");
        if (usable <= 0) return;
        long allowed = usable - PROFILE_IMPORT_FREE_RESERVE;
        long required = directoryLogicalSizeBounded(src, allowed);
        if (required > allowed)
            throw new IOException("Profile copy would leave less than 64 MB free app storage");
    }

    private static long directoryLogicalSizeBounded(File file, long cap) throws IOException {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return Math.max(0L, file.length());
        File[] children = file.listFiles();
        if (children == null)
            throw new IOException("Could not read profile directory: " + file);
        long total = 0L;
        for (File child : children) {
            long remaining = cap >= 0 ? Math.max(0L, cap - total) : Long.MAX_VALUE;
            long size = directoryLogicalSizeBounded(child, remaining);
            if (Long.MAX_VALUE - total < size) return Long.MAX_VALUE;
            total += size;
            if (cap >= 0 && total > cap) return total;
        }
        return total;
    }

    private static void copyDirectory(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.mkdirs() && !dst.isDirectory()) throw new IOException("Could not create " + dst);
            File[] children = src.listFiles(); if (children != null) for (File c : children) copyDirectory(c, new File(dst, c.getName()));
        } else {
            File parent = dst.getParentFile(); if (parent != null) parent.mkdirs();
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void moveDirectory(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("Could not create " + parent);
        if (src.renameTo(dst)) return;

        boolean destinationWasAbsent = !dst.exists();
        try {
            copyDirectory(src, dst);
            deleteRecursive(src);
        } catch (IOException e) {
            // Never leave a half-copied profile visible after an import/move error.
            if (destinationWasAbsent) deleteRecursive(dst);
            throw e;
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) { File[] kids = f.listFiles(); if (kids != null) for (File k : kids) deleteRecursive(k); }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static void findFilesNamed(File root, String name, List<File> out) {
        File[] kids = root.listFiles(); if (kids == null) return;
        for (File f : kids) { if (f.isDirectory()) findFilesNamed(f, name, out); else if (f.getName().equalsIgnoreCase(name)) out.add(f); }
    }

    private static void collectFiles(File root, List<File> out) {
        File[] kids = root.listFiles(); if (kids == null) return;
        for (File f : kids) { if (f.isDirectory()) collectFiles(f, out); else out.add(f); }
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name); if (!f.exists()) return f;
        String stem = name, ext = ""; int dot = name.lastIndexOf('.'); if (dot > 0) { stem = name.substring(0, dot); ext = name.substring(dot); }
        for (int i = 2; ; ++i) { f = new File(dir, stem + "_" + i + ext); if (!f.exists()) return f; }
    }

    private static String safeFileName(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
    }

    private static String sanitizeImportedEntryName(String s) {
        if (s == null) return "item";
        return s.replace("/", "_").replace((char) 0, '_');
    }

    private static String relativePath(File root, File file) {
        try { return root.toPath().relativize(file.toPath()).toString(); }
        catch (Throwable t) { return file.getName(); }
    }

    private static String profileHardwareSummary(File cfg) {
        String section = "";
        String machine = "";
        String cpu = "";
        long speed = 0;
        long mem = 0;
        String video = "";
        String sound = "";
        try (BufferedReader r = new BufferedReader(new FileReader(cfg))) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("[") && t.endsWith("]")) {
                    section = t.substring(1, t.length() - 1).trim();
                    continue;
                }
                if (t.isEmpty() || t.startsWith(";") || t.startsWith("#")) continue;
                int eq = t.indexOf('=');
                if (eq <= 0) continue;
                String key = t.substring(0, eq).trim();
                String value = t.substring(eq + 1).trim();
                if (section.equalsIgnoreCase("Machine")) {
                    if (key.equalsIgnoreCase("machine")) machine = value;
                    else if (key.equalsIgnoreCase("cpu_family")) cpu = value;
                    else if (key.equalsIgnoreCase("cpu_speed")) try { speed = Long.parseLong(value); } catch (NumberFormatException ignored) { }
                    else if (key.equalsIgnoreCase("mem_size")) try { mem = Long.parseLong(value); } catch (NumberFormatException ignored) { }
                } else if (section.equalsIgnoreCase("Video") && key.equalsIgnoreCase("gfxcard")) {
                    video = value;
                } else if (section.equalsIgnoreCase("Sound") && key.equalsIgnoreCase("sndcard")) {
                    sound = value;
                }
            }
        } catch (IOException ignored) {
            return "86box.cfg";
        }
        List<String> parts = new ArrayList<>();
        if (!machine.isEmpty()) parts.add(machine);
        if (!cpu.isEmpty()) {
            String c = cpu;
            if (speed >= 1_000_000) c += " " + String.format(Locale.ROOT, "%.0f MHz", speed / 1_000_000.0);
            parts.add(c);
        }
        if (mem > 0) parts.add(mem >= 1024 ? String.format(Locale.ROOT, "%.0f MB RAM", mem / 1024.0) : mem + " KB RAM");
        if (!video.isEmpty()) parts.add("GPU " + video);
        if (!sound.isEmpty()) parts.add("Audio " + sound);
        return parts.isEmpty() ? "86box.cfg" : String.join(" • ", parts);
    }

    private static long directorySize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total = 0; File[] kids = f.listFiles(); if (kids != null) for (File k : kids) total += directorySize(k); return total;
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0; if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0; if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    private boolean isCompactLayout() {
        Configuration c = getResources().getConfiguration();
        return (c.screenWidthDp > 0 && c.screenWidthDp < 520) ||
                (c.screenHeightDp > 0 && c.screenHeightDp < 500);
    }

    private float uiScaleFactor() {
        Configuration c = getResources().getConfiguration();
        int w = c.screenWidthDp;
        int h = c.screenHeightDp;
        if ((w > 0 && w < 360) || (h > 0 && h < 420)) return 0.82f;
        if ((w > 0 && w < 480) || (h > 0 && h < 600)) return 0.90f;
        if (w >= 900 && h >= 600) return 1.08f;
        return 1.0f;
    }

    private int dp(int value) {
        return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density * uiScaleFactor()));
    }
}
