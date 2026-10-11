package net.box86.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * v0.8.0 Android hardware editor.
 *
 * Hardware choices come from the compiled 86Box core instead of a small
 * duplicated Java list. This mirrors the desktop settings logic closely:
 * machine type -> machine -> eligible CPU families/speeds/FPU and compatible
 * bus devices.
 */
public final class HardwareConfigActivity extends Activity {
    private static final int REQ_IMPORT_HDD_IMAGE = 3101;
    private static final String STATE_PENDING_HDD_SLOT = "state.pendingHddImportSlot";

    private static final class LocalOption {
        final String label;
        final String value;
        LocalOption(String label, String value) { this.label = label; this.value = value; }
        @Override public String toString() { return label; }
    }

    private File profileDir, cfgFile;
    private IniConfig config;
    private boolean configWritable = true;
    private String configLoadError = "";
    private boolean catalogReady = false;

    private Spinner machineType, machine, cpuFamily, cpuSpeed, fpu;
    private EditText ramValue;
    private TextView ramHint, catalogStatus, configStatus;
    private Spinner waitStates, pitMode, timeSync, cpuExecutionMode;
    private CheckBox dynarec, softFloat;

    private Spinner video1, video2;
    private CheckBox voodoo, ibm8514, xga, da2;
    private Spinner mouse;

    private final Spinner[] sound = new Spinner[4];
    private Spinner midiOut;

    private final Spinner[] network = new Spinner[4];
    private final CheckBox[] slirp = new CheckBox[4];

    private Spinner fdc, cdromInterface;
    private final Spinner[] hdc = new Spinner[4];
    private final Spinner[] scsi = new Spinner[4];
    private TextView hddStatus;
    private int pendingHddImportSlot = -1;

    private CheckBox showFullCatalog;
    private final Spinner[] serial = new Spinner[4];
    private final CheckBox[] serialEnabled = new CheckBox[4];
    private final Spinner[] lpt = new Spinner[4];
    private final CheckBox[] lptEnabled = new CheckBox[4];

    private Spinner isaRtc;
    private final Spinner[] isaMem = new Spinner[4];
    private final Spinner[] mcaMem = new Spinner[4];
    private final Spinner[] isaRom = new Spinner[4];
    private CheckBox bugger, postcard, unittester, novellKeycard, softPower;

    private List<NativeHardwareCatalog.Item> allMachines = Collections.emptyList();
    private NativeHardwareCatalog.Item currentMachineItem;
    private Button saveButton;
    private Button saveStartButton;
    private Button importHddButton;
    private Button createHddButton;
    private boolean updating = true;
    private boolean initialSpinnerCallbacksPending = true;
    private String stableMachineTypeKey = "";
    private String stableMachineKey = "";
    private String stableCpuFamilyKey = "";
    private String stableCpuSpeedKey = "";

    private static final NativeHardwareCatalog.Item FALLBACK_MACHINE =
            new NativeHardwareCatalog.Item(new String[] {
                    "AMI 486 CS4031 (fallback)", "cs4031", "0", "0", "1024", "65536", "1024"
            });
    private static final int MACHINE_TYPE_ALL = -1000;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FullscreenUi.apply(this);
        String path = getIntent().getStringExtra(ProfileManagerActivity.EXTRA_PROFILE_PATH);
        if (path == null) { finish(); return; }

        profileDir = new File(path);
        cfgFile = new File(profileDir, "86box.cfg");
        String profileName = getIntent().getStringExtra(ProfileManagerActivity.EXTRA_PROFILE_NAME);
        DiagnosticLogger.startSession(this, profileDir, profileName);
        DiagnosticLogger.i("HardwareConfig", "onCreate BEGIN profile=" + profileName + " path=" + profileDir.getAbsolutePath() +
                " cfgExists=" + cfgFile.isFile() + " cfgBytes=" + (cfgFile.isFile() ? cfgFile.length() : 0));
        if (state != null) pendingHddImportSlot = state.getInt(STATE_PENDING_HDD_SLOT, -1);

        boolean existingConfig = cfgFile.isFile();
        try {
            config = IniConfig.load(cfgFile);
            traceRawConfig("after IniConfig.load");
        } catch (Exception e) {
            config = new IniConfig();
            if (existingConfig) {
                configWritable = false;
                configLoadError = readableError(e);
            }
            DiagnosticLogger.e("HardwareConfig", "IniConfig.load failed cfg=" + cfgFile.getAbsolutePath(), e);
        }

        buildUi();
        populateFromCore();
        updateMutationControls();
        if (!configWritable) showConfigLoadFailure(false);
        else refreshHddStatus();
        rememberCriticalSpinnerSelections();
        traceSelection("onCreate populated; waiting for queued Spinner callbacks");
        releaseInitialSpinnerCallbacksAfterLayout();
    }

    @Override
    protected void onDestroy() {
        traceSelection("onDestroy");
        DiagnosticLogger.i("HardwareConfig", "onDestroy finishing=" + isFinishing());
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PENDING_HDD_SLOT, pendingHddImportSlot);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FullscreenUi.apply(this);
        if (hddStatus != null && configWritable) refreshHddStatus();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Keep the in-memory hardware form intact across tablet rotation/config changes.
        FullscreenUi.apply(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) FullscreenUi.apply(this);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(12));

        TextView title = text("86Box hardware configurator v" + BuildConfig.VERSION_NAME, 23);
        root.addView(title);

        TextView subtitle = text("Profile: " + getIntent().getStringExtra(ProfileManagerActivity.EXTRA_PROFILE_NAME), 13);
        subtitle.setPadding(0, 0, 0, dp(5));
        root.addView(subtitle);

        configStatus = text("", 12);
        configStatus.setPadding(dp(6), dp(4), dp(6), dp(7));
        configStatus.setVisibility(View.GONE);
        root.addView(configStatus);

        catalogStatus = text("Loading hardware catalogue from 86Box core…", 12);
        catalogStatus.setPadding(0, 0, 0, dp(7));
        root.addView(catalogStatus);

        showFullCatalog = new CheckBox(this);
        showFullCatalog.setText("Full catalogue: also show hardware incompatible with the selected motherboard");
        showFullCatalog.setChecked(false);
        root.addView(showFullCatalog);
        TextView fullWarning = text("Normal mode matches the desktop compatibility filtering. Full catalogue is useful for browsing/testing; incompatible combinations may not boot.", 11);
        fullWarning.setPadding(dp(4), 0, dp(4), dp(6));
        root.addView(fullWarning);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        LinearLayout machineSection = addSection(form, "Machine / Processor / Performance", true);
        machineType = addNativeSpinner(machineSection, "Machine type");
        machine = addNativeSpinner(machineSection, "Machine / motherboard");
        cpuFamily = addNativeSpinner(machineSection, "CPU type");
        cpuSpeed = addNativeSpinner(machineSection, "Frequency");
        fpu = addNativeSpinner(machineSection, "FPU");

        label(machineSection, "Memory");
        LinearLayout ramRow = new LinearLayout(this);
        ramRow.setOrientation(LinearLayout.HORIZONTAL);
        ramValue = new EditText(this);
        ramValue.setSingleLine(true);
        ramValue.setInputType(InputType.TYPE_CLASS_NUMBER);
        ramRow.addView(ramValue, new LinearLayout.LayoutParams(0, dp(52), 1f));
        ramHint = text("", 12);
        ramHint.setPadding(dp(8), dp(15), 0, 0);
        ramRow.addView(ramHint, new LinearLayout.LayoutParams(0, dp(52), 1.4f));
        machineSection.addView(ramRow);

        waitStates = addLocalSpinner(machineSection, "Wait states",
                new LocalOption("Default", "0"),
                new LocalOption("0 wait states", "1"),
                new LocalOption("1 wait state", "2"),
                new LocalOption("2 wait states", "3"),
                new LocalOption("3 wait states", "4"),
                new LocalOption("4 wait states", "5"),
                new LocalOption("5 wait states", "6"),
                new LocalOption("6 wait states", "7"),
                new LocalOption("7 wait states", "8"));

        pitMode = addLocalSpinner(machineSection, "PIT mode",
                new LocalOption("Auto", "-1"),
                new LocalOption("Slow", "0"),
                new LocalOption("Fast", "1"));

        timeSync = addLocalSpinner(machineSection, "Time synchronization",
                new LocalOption("Disabled", "disabled"),
                new LocalOption("Enabled (local time)", "local"),
                new LocalOption("Enabled (UTC)", "utc"));

        dynarec = addCheck(machineSection, "Dynamic Recompiler", true);

        cpuExecutionMode = addLocalSpinner(machineSection, "CPU execution engine (restart required)",
                new LocalOption("86Box Native - exact selected CPU speed", "native"),
                new LocalOption("DOSBox Pure Adaptive CPU - Smooth v2 (experimental)", "dosbox_pure_adaptive"));
        TextView cpuExecutionHint = text(
                "Smooth v2 keeps the 86Box CPU/new-dynarec for hardware compatibility and treats the selected MHz " +
                "as a maximum. It now follows sustained 86Box real-time speed instead of reacting to short CPU " +
                "microbursts, and guest-MHz changes are slewed gradually to prevent visible stutter. Effective guest " +
                "MHz can still be lower than the configured value when the host cannot sustain real time.", 12);
        cpuExecutionHint.setPadding(dp(6), dp(4), dp(6), dp(10));
        machineSection.addView(cpuExecutionHint);

        softFloat = addCheck(machineSection, "Use FLOAT32 / Softfloat FPU", false);

        LinearLayout displaySection = addSection(form, "Display", false);
        video1 = addNativeSpinner(displaySection, "Video #1");
        video2 = addNativeSpinner(displaySection, "Video #2");
        voodoo = addCheck(displaySection, "Standalone 3dfx Voodoo", false);
        ibm8514 = addCheck(displaySection, "Standalone IBM 8514/A", false);
        xga = addCheck(displaySection, "Standalone IBM XGA", false);
        da2 = addCheck(displaySection, "Standalone DA2", false);

        LinearLayout inputSection = addSection(form, "Input devices", false);
        mouse = addNativeSpinner(inputSection, "Mouse");

        LinearLayout soundSection = addSection(form, "Sound / MIDI", false);
        for (int i = 0; i < sound.length; i++)
            sound[i] = addNativeSpinner(soundSection, "Sound card " + (i + 1));
        midiOut = addNativeSpinner(soundSection, "MIDI output device");

        LinearLayout networkSection = addSection(form, "Network", false);
        for (int i = 0; i < network.length; i++) {
            network[i] = addNativeSpinner(networkSection, "Network card " + (i + 1));
            slirp[i] = addCheck(networkSection, "NIC " + (i + 1) + ": SLiRP user-mode networking", i == 0);
        }

        LinearLayout storageSection = addSection(form, "Storage controllers", false);
        fdc = addNativeSpinner(storageSection, "Floppy controller");
        cdromInterface = addNativeSpinner(storageSection, "CD-ROM interface");
        for (int i = 0; i < hdc.length; i++)
            hdc[i] = addNativeSpinner(storageSection, "Hard-disk controller " + (i + 1));
        for (int i = 0; i < scsi.length; i++)
            scsi[i] = addNativeSpinner(storageSection, "SCSI controller " + (i + 1));

        TextView mediaNote = text(
                "This section configures storage controllers. Fixed HDD images are managed in the Hard disks section below.", 12);
        mediaNote.setPadding(dp(6), dp(8), dp(6), dp(12));
        storageSection.addView(mediaNote);

        LinearLayout hardDiskSection = addSection(form, "Hard disks", false);
        hddStatus = text("Loading fixed disks…", 13);
        hddStatus.setPadding(dp(6), dp(4), dp(6), dp(8));
        hardDiskSection.addView(hddStatus);

        LinearLayout hddButtons = new LinearLayout(this);
        hddButtons.setOrientation(LinearLayout.HORIZONTAL);
        importHddButton = button("IMPORT HDD IMAGE", v -> chooseHddSlotForImport());
        createHddButton = button("CREATE HDD", v -> showCreateHddDialog());
        hddButtons.addView(importHddButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        hddButtons.addView(createHddButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        hardDiskSection.addView(hddButtons);

        TextView hddNote = text(
                "Fixed HDD changes are offline-only. Importing here copies the image into this profile. " +
                "CREATE HDD makes a sparse RAW .img and assigns an available IDE channel automatically.", 12);
        hddNote.setPadding(dp(6), dp(7), dp(6), dp(10));
        hardDiskSection.addView(hddNote);

        LinearLayout portsSection = addSection(form, "Ports (COM & LPT)", false);
        for (int i = 0; i < serial.length; i++) {
            serialEnabled[i] = addCheck(portsSection, "COM" + (i + 1) + " enabled", i < 2);
            serial[i] = addNativeSpinner(portsSection, "COM" + (i + 1) + " device");
        }
        for (int i = 0; i < lpt.length; i++) {
            lptEnabled[i] = addCheck(portsSection, "LPT" + (i + 1) + " enabled", i == 0);
            lpt[i] = addNativeSpinner(portsSection, "LPT" + (i + 1) + " device");
        }

        LinearLayout otherSection = addSection(form, "Other peripherals", false);
        isaRtc = addNativeSpinner(otherSection, "ISA RTC / clock card");
        for (int i = 0; i < isaMem.length; i++)
            isaMem[i] = addNativeSpinner(otherSection, "ISA memory expansion " + (i + 1));
        for (int i = 0; i < mcaMem.length; i++)
            mcaMem[i] = addNativeSpinner(otherSection, "MCA memory expansion " + (i + 1));
        for (int i = 0; i < isaRom.length; i++)
            isaRom[i] = addNativeSpinner(otherSection, "ISA ROM card " + (i + 1));
        bugger = addCheck(otherSection, "Bugger diagnostic card", false);
        postcard = addCheck(otherSection, "POST card", false);
        unittester = addCheck(otherSection, "Unit tester", false);
        novellKeycard = addCheck(otherSection, "Novell key card", false);
        softPower = addCheck(otherSection, "Soft power device", false);

        machineType.setOnItemSelectedListener(listener(this::onMachineTypeChanged));
        machine.setOnItemSelectedListener(listener(this::onMachineChanged));
        cpuFamily.setOnItemSelectedListener(listener(this::onCpuFamilyChanged));
        cpuSpeed.setOnItemSelectedListener(listener(this::onCpuSpeedChanged));
        showFullCatalog.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updating) return;
            boolean prev = updating;
            updating = true;
            refreshMachineDependent(selectedValue(machine), false);
            updateCatalogStatus();
            updating = prev;
        });

        for (int i = 0; i < network.length; i++) {
            final int slot = i;
            network[i].setOnItemSelectedListener(listener(() -> updateNetworkEnabled(slot)));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        boolean compactButtons = isCompactLayout();
        buttons.setOrientation(compactButtons ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        buttons.setGravity(compactButtons ? Gravity.FILL_HORIZONTAL : Gravity.END);
        Button cancelButton = button("CANCEL", v -> finish());
        saveButton = button("SAVE", v -> save(false));
        saveStartButton = button("SAVE & START", v -> save(true));
        if (compactButtons) {
            buttons.addView(cancelButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            buttons.addView(saveButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            buttons.addView(saveStartButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        } else {
            buttons.addView(cancelButton);
            buttons.addView(saveButton);
            buttons.addView(saveStartButton);
        }
        root.addView(buttons);

        setContentView(root);
    }

    private android.widget.AdapterView.OnItemSelectedListener listener(final Runnable r) {
        return new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (updating || initialSpinnerCallbacksPending) {
                    if (isCriticalSpinner(parent)) {
                        DiagnosticLogger.i("CpuTrace", "suppressed queued Spinner callback control=" +
                                spinnerName(parent) + " position=" + position + " selected=" + parent.getSelectedItem());
                    }
                    return;
                }
                if (isCriticalSpinner(parent) && !criticalSpinnerSelectionChanged(parent)) {
                    DiagnosticLogger.i("CpuTrace", "ignored stale/unchanged Spinner callback control=" +
                            spinnerName(parent) + " position=" + position + " selected=" + parent.getSelectedItem());
                    return;
                }
                r.run();
                rememberCriticalSpinnerSelections();
            }
        };
    }

    /**
     * Android Spinner may dispatch setAdapter/setSelection callbacks many frames after onCreate.
     * Timing alone is therefore not reliable.  v0.9.8 combines a short startup gate with a
     * stable-selection guard: callbacks whose selected value did not actually change are ignored.
     * This prevents a late cpuFamily callback from rebuilding Frequency with no preferred value
     * and visually resetting an imported 200 MHz CPU to row 0 (66 MHz).
     */
    private void releaseInitialSpinnerCallbacksAfterLayout() {
        final View decor = getWindow().getDecorView();
        Runnable release = () -> {
            rememberCriticalSpinnerSelections();
            initialSpinnerCallbacksPending = false;
            updating = false;
            DiagnosticLogger.i("CpuTrace", "initial Spinner callback suppression RELEASED with stable-selection guard");
            traceSelection("initial Spinner callbacks drained");
        };
        decor.postOnAnimation(() -> decor.postOnAnimation(() ->
                decor.postOnAnimation(() -> decor.postOnAnimation(release))));
    }

    private boolean isCriticalSpinner(android.widget.AdapterView<?> parent) {
        return parent == machineType || parent == machine || parent == cpuFamily || parent == cpuSpeed;
    }

    private String criticalSpinnerKey(android.widget.AdapterView<?> parent) {
        Object selected = parent == null ? null : parent.getSelectedItem();
        if (selected instanceof NativeHardwareCatalog.Item) {
            NativeHardwareCatalog.Item it = (NativeHardwareCatalog.Item) selected;
            StringBuilder b = new StringBuilder();
            b.append(it.id).append('|').append(it.value == null ? "" : it.value);
            if (parent == cpuSpeed) b.append('|').append(it.extraDouble(0, Double.NaN));
            return b.toString();
        }
        return selected == null ? "<null>" : selected.toString();
    }

    private void rememberCriticalSpinnerSelections() {
        if (machineType != null) stableMachineTypeKey = criticalSpinnerKey(machineType);
        if (machine != null) stableMachineKey = criticalSpinnerKey(machine);
        if (cpuFamily != null) stableCpuFamilyKey = criticalSpinnerKey(cpuFamily);
        if (cpuSpeed != null) stableCpuSpeedKey = criticalSpinnerKey(cpuSpeed);
    }

    private boolean criticalSpinnerSelectionChanged(android.widget.AdapterView<?> parent) {
        String now = criticalSpinnerKey(parent);
        String old;
        if (parent == machineType) { old = stableMachineTypeKey; stableMachineTypeKey = now; }
        else if (parent == machine) { old = stableMachineKey; stableMachineKey = now; }
        else if (parent == cpuFamily) { old = stableCpuFamilyKey; stableCpuFamilyKey = now; }
        else if (parent == cpuSpeed) { old = stableCpuSpeedKey; stableCpuSpeedKey = now; }
        else return true;
        return old == null || !old.equals(now);
    }

    private String spinnerName(android.widget.AdapterView<?> parent) {
        if (parent == machineType) return "machineType";
        if (parent == machine) return "machine";
        if (parent == cpuFamily) return "cpuFamily";
        if (parent == cpuSpeed) return "cpuSpeed";
        return "other";
    }

    private LinearLayout addSection(LinearLayout parent, String name, boolean open) {
        Button header = new Button(this);
        header.setAllCaps(false);
        header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.setText((open ? "▼  " : "▶  ") + name);
        parent.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(8), 0, dp(4), dp(8));
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        parent.addView(body);

        header.setOnClickListener(v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            header.setText((show ? "▼  " : "▶  ") + name);
        });
        return body;
    }

    private Spinner addNativeSpinner(LinearLayout parent, String label) {
        label(parent, label);
        Spinner s = new Spinner(this);
        parent.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return s;
    }

    private Spinner addLocalSpinner(LinearLayout parent, String label, LocalOption... opts) {
        label(parent, label);
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new ArrayList<>(Arrays.asList(opts))));
        parent.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return s;
    }

    private void label(LinearLayout p, String s) {
        TextView t = text(s, 14);
        t.setPadding(0, dp(7), 0, 0);
        p.addView(t);
    }

    private CheckBox addCheck(LinearLayout p, String s, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setChecked(checked);
        p.addView(c);
        return c;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private TextView text(String s, float size) {
        TextView t = new TextView(this);
        t.setText(s == null ? "" : s);
        t.setTextSize(size);
        return t;
    }

    private void populateFromCore() {
        updating = true;
        DiagnosticLogger.i("CpuTrace", "populateFromCore BEGIN machine=" + config.get("Machine", "machine", "<missing>") +
                " family=" + config.get("Machine", "cpu_family", "<missing>") +
                " speed=" + config.get("Machine", "cpu_speed", "<missing>") +
                " multi=" + config.get("Machine", "cpu_multi", "<missing>"));
        if (!NativeHardwareCatalog.probe()) {
            catalogStatus.setText("NATIVE CORE CATALOGUE FAILED in v" + BuildConfig.VERSION_NAME + ":\n" +
                    NativeHardwareCatalog.getLoadError() + "\nUsing emergency minimal fallback.");
            setNativeAdapter(machineType, Collections.singletonList(
                    new NativeHardwareCatalog.Item(new String[]{"Fallback", "0", "0"})), "0");
            allMachines = Collections.singletonList(FALLBACK_MACHINE);
            setNativeAdapter(machine, allMachines, config.get("Machine", "machine", "cs4031"));
            currentMachineItem = (NativeHardwareCatalog.Item) machine.getSelectedItem();
            populateFallbackDevices();
            loadSimpleSettings();
            catalogReady = false;
            DiagnosticLogger.e("HardwareConfig", "Native hardware catalogue probe FAILED: " + NativeHardwareCatalog.getLoadError(), null);
            updateMutationControls();
            new AlertDialog.Builder(this)
                    .setTitle("86Box core hardware catalogue not active")
                    .setMessage("The Android UI could not read the compiled 86Box hardware tables.\n\n" +
                            NativeHardwareCatalog.getLoadError() +
                            "\n\nThis build should normally expose hundreds of machines. Do not save this profile while the core catalogue is unavailable. Rebuild the native ARM64/x86_64 libraries cleanly so libmain.so is relinked.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        catalogReady = true;
        updateMutationControls();

        List<NativeHardwareCatalog.Item> coreTypes =
                NativeHardwareCatalog.get(NativeHardwareCatalog.MACHINE_TYPES, -1, -1, 0);
        ArrayList<NativeHardwareCatalog.Item> types = new ArrayList<>();
        types.add(new NativeHardwareCatalog.Item(new String[]{"All machine types (full motherboard list)", "all", Integer.toString(MACHINE_TYPE_ALL)}));
        types.addAll(coreTypes);
        allMachines = NativeHardwareCatalog.get(NativeHardwareCatalog.MACHINES, -1, -1, 0);
        DiagnosticLogger.i("HardwareConfig", "Native catalogue OK machineTypes=" + coreTypes.size() + " machines=" + allMachines.size());

        String configuredMachine = config.get("Machine", "machine", "cs4031");
        NativeHardwareCatalog.Item found = NativeHardwareCatalog.findValue(allMachines, configuredMachine);
        int wantedType = found != null ? found.extraInt(0, 0) : (coreTypes.isEmpty() ? MACHINE_TYPE_ALL : coreTypes.get(0).id);
        DiagnosticLogger.i("CpuTrace", "configured machine lookup value=" + configuredMachine +
                " found=" + describeItem(found) + " wantedType=" + wantedType);

        setNativeAdapter(machineType, types, Integer.toString(wantedType));
        populateMachineSpinner(wantedType, configuredMachine);
        refreshMachineDependent(configuredMachine, true);
        loadSimpleSettings();
        updateCatalogStatus();
        traceSelection("populateFromCore END");
    }

    private List<NativeHardwareCatalog.Item> catalog(int kind, int machineId, int itemId, int slot) {
        boolean all = showFullCatalog != null && showFullCatalog.isChecked();
        return all ? NativeHardwareCatalog.getAll(kind, machineId, itemId, slot)
                : NativeHardwareCatalog.get(kind, machineId, itemId, slot);
    }

    private void updateCatalogStatus() {
        if (!NativeHardwareCatalog.isAvailable()) return;
        int machineClasses = NativeHardwareCatalog.get(NativeHardwareCatalog.MACHINE_TYPES, -1, -1, 0).size();
        int motherboards = allMachines == null ? 0 : allMachines.size();
        int allVideo = NativeHardwareCatalog.getAll(NativeHardwareCatalog.VIDEO, -1, -1, 0).size();
        int allSound = NativeHardwareCatalog.getAll(NativeHardwareCatalog.SOUND, -1, -1, 0).size();
        int allNet = NativeHardwareCatalog.getAll(NativeHardwareCatalog.NETWORK, -1, -1, 0).size();
        int allHdc = NativeHardwareCatalog.getAll(NativeHardwareCatalog.HDC, -1, -1, 0).size();
        int mid = currentMachineId();
        int compatVideo = NativeHardwareCatalog.get(NativeHardwareCatalog.VIDEO, mid, -1, 0).size();
        int compatSound = NativeHardwareCatalog.get(NativeHardwareCatalog.SOUND, mid, -1, 0).size();
        int compatNet = NativeHardwareCatalog.get(NativeHardwareCatalog.NETWORK, mid, -1, 0).size();

        catalogStatus.setText("86Box CORE CATALOG ACTIVE • " + machineClasses +
                " machine classes • " + motherboards + " motherboards\n" +
                "Full core: " + allVideo + " video • " + allSound + " sound • " + allNet +
                " network • " + allHdc + " HDD controllers\n" +
                "Selected motherboard compatible: " + compatVideo + " video • " + compatSound +
                " sound • " + compatNet + " network" +
                (showFullCatalog != null && showFullCatalog.isChecked() ?
                        "\nFULL CATALOG MODE is ON (incompatible devices are visible)." :
                        "\nCompatibility filtering is ON, matching the desktop configurator."));
    }

    private void populateFallbackDevices() {
        List<NativeHardwareCatalog.Item> none = Collections.singletonList(
                new NativeHardwareCatalog.Item(new String[]{"None", "none", "0"}));
        for (Spinner s : new Spinner[]{video1, video2, mouse, midiOut, fdc, cdromInterface, isaRtc})
            setNativeAdapter(s, none, "none");
        for (Spinner s : sound) setNativeAdapter(s, none, "none");
        for (Spinner s : network) setNativeAdapter(s, none, "none");
        for (Spinner s : hdc) setNativeAdapter(s, none, "none");
        for (Spinner s : scsi) setNativeAdapter(s, none, "none");
        for (Spinner s : serial) setNativeAdapter(s, none, "none");
        for (Spinner s : lpt) setNativeAdapter(s, none, "none");
        for (Spinner s : isaMem) setNativeAdapter(s, none, "none");
        for (Spinner s : mcaMem) setNativeAdapter(s, none, "none");
        for (Spinner s : isaRom) setNativeAdapter(s, none, "none");
        setNativeAdapter(cpuFamily, none, "none");
        setNativeAdapter(cpuSpeed, Collections.singletonList(
                new NativeHardwareCatalog.Item(new String[]{"Default", "0", "0", "1.0", "0", "0"})), "0");
        setNativeAdapter(fpu, none, "none");
        updateRamUi(FALLBACK_MACHINE, parseInt(config.get("Machine", "mem_size", "16384"), 16384));
    }

    private void loadSimpleSettings() {
        selectLocal(waitStates, config.get("Machine", "cpu_waitstates", "0"));
        selectLocal(pitMode, config.get("Machine", "pit_mode", "-1"));
        String ts = config.get("Machine", "time_sync", "local");
        if (ts.isEmpty()) ts = "local";
        selectLocal(timeSync, ts);
        dynarec.setChecked(!"0".equals(config.get("Machine", "cpu_use_dynarec", "1")));
        selectLocal(cpuExecutionMode, config.get("Machine", "android_cpu_engine", "native"));
        softFloat.setChecked(!"0".equals(config.get("Machine", "fpu_softfloat", "0")));

        voodoo.setChecked(!"0".equals(config.get("Video", "voodoo", "0")));
        ibm8514.setChecked(!"0".equals(config.get("Video", "8514a", "0")));
        xga.setChecked(!"0".equals(config.get("Video", "xga", "0")));
        da2.setChecked(!"0".equals(config.get("Video", "da2", "0")));
    }

    private void onMachineTypeChanged() {
        NativeHardwareCatalog.Item type = selectedNative(machineType);
        if (type == null) return;
        String previous = selectedValue(machine);
        String preferred = null;

        // Preserve the motherboard only when it really belongs to the newly
        // selected class (or when browsing the explicit All-machines view).
        // This prevents a stale board from a previous class appearing to survive
        // an intentional Machine type change.
        if (type.id == MACHINE_TYPE_ALL) {
            preferred = previous;
        } else {
            NativeHardwareCatalog.Item old = NativeHardwareCatalog.findValue(allMachines, previous);
            if (old != null && old.extraInt(0, -999) == type.id) preferred = previous;
        }

        boolean prev = updating;
        updating = true;
        populateMachineSpinner(type.id, preferred);
        refreshMachineDependent(selectedValue(machine), false);
        updateCatalogStatus();
        updating = prev;
    }

    private void onMachineChanged() {
        boolean prev = updating;
        updating = true;
        refreshMachineDependent(null, false);
        updateCatalogStatus();
        updating = prev;
    }

    private void onCpuFamilyChanged() {
        boolean prev = updating;
        updating = true;
        refreshCpuSpeed(null, null, null, false);
        updating = prev;
    }

    private void onCpuSpeedChanged() {
        boolean prev = updating;
        updating = true;
        refreshFpu(null);
        updateDynarecSupport();
        updating = prev;
    }

    private void populateMachineSpinner(int typeId, String preferred) {
        ArrayList<NativeHardwareCatalog.Item> filtered = new ArrayList<>();
        if (typeId == MACHINE_TYPE_ALL) {
            filtered.addAll(allMachines);
        } else {
            for (NativeHardwareCatalog.Item item : allMachines)
                if (item.extraInt(0, -999) == typeId) filtered.add(item);
        }
        if (filtered.isEmpty()) filtered.addAll(allMachines);
        setNativeAdapter(machine, filtered, preferred);
    }

    private void refreshMachineDependent(String preferredMachine, boolean initial) {
        NativeHardwareCatalog.Item m = selectedNative(machine);
        if (m == null && preferredMachine != null)
            m = NativeHardwareCatalog.findValue(allMachines, preferredMachine);
        if (m == null && !allMachines.isEmpty()) m = allMachines.get(0);
        currentMachineItem = m;
        int machineId = m == null ? -1 : m.id;

        String cfgCpuFamily = initial ? config.get("Machine", "cpu_family", "") : selectedValue(cpuFamily);
        String cfgCpuSpeed = initial ? config.get("Machine", "cpu_speed", "") : selectedValue(cpuSpeed);
        String cfgCpuMulti = initial ? config.get("Machine", "cpu_multi", "") : selectedCpuMultiplier();
        String cfgFpu = initial ? config.get("Machine", "fpu_type", "none") : selectedValue(fpu);

        List<NativeHardwareCatalog.Item> families =
                catalog(NativeHardwareCatalog.CPU_FAMILIES, machineId, -1, 0);
        List<NativeHardwareCatalog.Item> allFamiliesForTrace =
                NativeHardwareCatalog.getAll(NativeHardwareCatalog.CPU_FAMILIES, machineId, -1, 0);
        DiagnosticLogger.i("CpuTrace", "refreshMachineDependent initial=" + initial +
                " machine=" + describeItem(m) +
                " cfgFamily=" + cfgCpuFamily + " cfgSpeed=" + cfgCpuSpeed + " cfgMulti=" + cfgCpuMulti +
                " filteredFamilies=" + families.size() + " allFamilies=" + allFamiliesForTrace.size() +
                " filtered=" + describeItems(families, 40));

        // Imported desktop profiles may contain a CPU family that is hidden by
        // the Android compatibility filter.  Do not silently replace it with
        // the first visible family while merely opening the configurator.
        // Preserve the configured family on the initial load when it exists in
        // the compiled 86Box core; normal filtering resumes after user changes.
        if (initial && cfgCpuFamily != null && !cfgCpuFamily.isEmpty() &&
                NativeHardwareCatalog.findValue(families, cfgCpuFamily) == null) {
            NativeHardwareCatalog.Item configuredFamily = NativeHardwareCatalog.findValue(
                    allFamiliesForTrace, cfgCpuFamily);
            DiagnosticLogger.i("CpuTrace", "configured CPU family hidden by filter; lookup in full catalog -> " + describeItem(configuredFamily));
            if (configuredFamily != null) {
                ArrayList<NativeHardwareCatalog.Item> preservedFamilies = new ArrayList<>(families);
                preservedFamilies.add(configuredFamily);
                families = preservedFamilies;
            }
        }

        setNativeAdapter(cpuFamily, families, cfgCpuFamily);
        refreshCpuSpeed(cfgCpuSpeed, cfgCpuMulti, cfgFpu, initial);
        if (initial) {
            DiagnosticLogger.i("HardwareConfig",
                    "CPU profile load: family=" + cfgCpuFamily +
                    " speed=" + cfgCpuSpeed + " multi=" + cfgCpuMulti +
                    " -> selectedFamily=" + selectedValue(cpuFamily) +
                    " selectedSpeed=" + selectedValue(cpuSpeed) +
                    " selectedMulti=" + selectedCpuMultiplier());
        }

        int configuredRam = initial
                ? parseInt(config.get("Machine", "mem_size", "16384"), 16384)
                : normalizedRamKb();
        updateRamUi(m, configuredRam);

        setNativeAdapter(video1,
                catalog(NativeHardwareCatalog.VIDEO, machineId, -1, 0),
                initial ? config.get("Video", "gfxcard", "internal") : selectedValue(video1));
        setNativeAdapter(video2,
                catalog(NativeHardwareCatalog.VIDEO, machineId, -1, 1),
                initial ? config.get("Video", "gfxcard_2", "none") : selectedValue(video2));

        setNativeAdapter(mouse,
                catalog(NativeHardwareCatalog.MOUSE, machineId, -1, 0),
                initial ? config.get("Input devices", "mouse_type", "none") : selectedValue(mouse));

        for (int i = 0; i < sound.length; i++) {
            String key = i == 0 ? "sndcard" : "sndcard" + (i + 1);
            setNativeAdapter(sound[i],
                    catalog(NativeHardwareCatalog.SOUND, machineId, -1, i),
                    initial ? config.get("Sound", key, "none") : selectedValue(sound[i]));
        }

        setNativeAdapter(midiOut,
                catalog(NativeHardwareCatalog.MIDI_OUT, machineId, -1, 0),
                initial ? config.get("Sound", "midi_device", "none") : selectedValue(midiOut));

        for (int i = 0; i < network.length; i++) {
            String prefix = String.format(Locale.ROOT, "net_%02d_", i + 1);
            setNativeAdapter(network[i],
                    catalog(NativeHardwareCatalog.NETWORK, machineId, -1, i),
                    initial ? config.get("Network", prefix + "card", "none") : selectedValue(network[i]));
            if (initial)
                slirp[i].setChecked("slirp".equalsIgnoreCase(
                        config.get("Network", prefix + "net_type", i == 0 ? "slirp" : "none")));
            updateNetworkEnabled(i);
        }

        setNativeAdapter(fdc,
                catalog(NativeHardwareCatalog.FDC, machineId, -1, 0),
                initial ? config.get("Storage controllers", "fdc", "internal") : selectedValue(fdc));
        setNativeAdapter(cdromInterface,
                catalog(NativeHardwareCatalog.CDROM_INTERFACE, machineId, -1, 0),
                initial ? config.get("Storage controllers", "cdrom_interface", "none") : selectedValue(cdromInterface));

        for (int i = 0; i < hdc.length; i++) {
            String key = "hdc_" + (i + 1);
            String def = defaultHdcValue(i);
            setNativeAdapter(hdc[i],
                    catalog(NativeHardwareCatalog.HDC, machineId, -1, i),
                    initial ? config.get("Storage controllers", key, def) : selectedValue(hdc[i]));
        }
        for (int i = 0; i < scsi.length; i++) {
            String key = "scsicard_" + (i + 1);
            setNativeAdapter(scsi[i],
                    catalog(NativeHardwareCatalog.SCSI, machineId, -1, i),
                    initial ? config.get("Storage controllers", key, "none") : selectedValue(scsi[i]));
        }

        for (int i = 0; i < serial.length; i++) {
            String devKey = "serial" + (i + 1) + "_device";
            String enKey = "serial" + (i + 1) + "_enabled";
            setNativeAdapter(serial[i],
                    catalog(NativeHardwareCatalog.CHAR_COM, machineId, -1, i),
                    initial ? config.get("Ports (COM & LPT)", devKey, "none") : selectedValue(serial[i]));
            if (initial)
                serialEnabled[i].setChecked(!"0".equals(config.get("Ports (COM & LPT)", enKey, i < 2 ? "1" : "0")));
        }
        for (int i = 0; i < lpt.length; i++) {
            String devKey = "lpt" + (i + 1) + "_device";
            String enKey = "lpt" + (i + 1) + "_enabled";
            setNativeAdapter(lpt[i],
                    catalog(NativeHardwareCatalog.CHAR_LPT, machineId, -1, i),
                    initial ? config.get("Ports (COM & LPT)", devKey, "none") : selectedValue(lpt[i]));
            if (initial)
                lptEnabled[i].setChecked(!"0".equals(config.get("Ports (COM & LPT)", enKey, i == 0 ? "1" : "0")));
        }

        setNativeAdapter(isaRtc,
                catalog(NativeHardwareCatalog.ISA_RTC, machineId, -1, 0),
                initial ? config.get("Other peripherals", "isartc_type", "none") : selectedValue(isaRtc));
        for (int i = 0; i < isaMem.length; i++) {
            String key = "isamem" + i + "_type";
            setNativeAdapter(isaMem[i],
                    catalog(NativeHardwareCatalog.ISA_MEM, machineId, -1, i),
                    initial ? config.get("Other peripherals", key, "none") : selectedValue(isaMem[i]));
        }
        for (int i = 0; i < mcaMem.length; i++) {
            String key = "mcamem" + i + "_type";
            setNativeAdapter(mcaMem[i],
                    catalog(NativeHardwareCatalog.MCA_MEM, machineId, -1, i),
                    initial ? config.get("Other peripherals", key, "none") : selectedValue(mcaMem[i]));
        }
        for (int i = 0; i < isaRom.length; i++) {
            String key = "isarom" + i + "_type";
            setNativeAdapter(isaRom[i],
                    catalog(NativeHardwareCatalog.ISA_ROM, machineId, -1, i),
                    initial ? config.get("Other peripherals", key, "none") : selectedValue(isaRom[i]));
        }
        if (initial) {
            bugger.setChecked(!"0".equals(config.get("Other peripherals", "bugger_enabled", "0")));
            postcard.setChecked(!"0".equals(config.get("Other peripherals", "postcard_enabled", "0")));
            unittester.setChecked(!"0".equals(config.get("Other peripherals", "unittester_enabled", "0")));
            novellKeycard.setChecked(!"0".equals(config.get("Other peripherals", "novell_keycard_enabled", "0")));
            softPower.setChecked(!"0".equals(config.get("Other peripherals", "softpower_enabled", "0")));
        }
    }

    private void refreshCpuSpeed(String preferredSpeed, String preferredMulti, String preferredFpu,
                                 boolean preserveImportedProfile) {
        NativeHardwareCatalog.Item fam = selectedNative(cpuFamily);
        int machineId = currentMachineId();
        if (fam == null) {
            setNativeAdapter(cpuSpeed, Collections.emptyList(), null);
            setNativeAdapter(fpu, Collections.emptyList(), null);
            return;
        }

        List<NativeHardwareCatalog.Item> speeds =
                catalog(NativeHardwareCatalog.CPU_SPEEDS, machineId, fam.id, 0);
        List<NativeHardwareCatalog.Item> allSpeedsForTrace =
                NativeHardwareCatalog.getAll(NativeHardwareCatalog.CPU_SPEEDS, machineId, fam.id, 0);
        DiagnosticLogger.i("CpuTrace", "refreshCpuSpeed preserve=" + preserveImportedProfile +
                " machineId=" + machineId + " family=" + describeItem(fam) +
                " preferredSpeed=" + preferredSpeed + " preferredMulti=" + preferredMulti +
                " filteredCount=" + speeds.size() + " allCount=" + allSpeedsForTrace.size() +
                " filtered=" + describeItems(speeds, 80) +
                " all=" + describeItems(allSpeedsForTrace, 120));

        // Same rule as for the family above: while opening an imported desktop
        // profile, keep its exact CPU row even if compatibility filtering would
        // normally hide it. This prevents e.g. 200 MHz from being displayed as
        // the first compatible 66 MHz entry. Only the one configured row is
        // appended; the rest of the incompatible catalogue stays hidden.
        if (preserveImportedProfile && preferredSpeed != null && !preferredSpeed.isEmpty()) {
            NativeHardwareCatalog.Item configuredCpu = findCpuExact(
                    allSpeedsForTrace, preferredSpeed, preferredMulti);
            DiagnosticLogger.i("CpuTrace", "exact configured CPU lookup -> " + describeItem(configuredCpu));
            if (configuredCpu != null && !containsCpuRow(speeds, configuredCpu)) {
                ArrayList<NativeHardwareCatalog.Item> preservedSpeeds = new ArrayList<>(speeds);
                preservedSpeeds.add(configuredCpu);
                speeds = preservedSpeeds;
            }
        }

        setCpuSpeedAdapter(cpuSpeed, speeds, preferredSpeed, preferredMulti);
        DiagnosticLogger.i("CpuTrace", "after setCpuSpeedAdapter selected=" + describeItem(selectedNative(cpuSpeed)) +
                " selectedMulti=" + selectedCpuMultiplier());
        refreshFpu(preferredFpu);
        updateDynarecSupport();
    }

    private void refreshFpu(String preferred) {
        NativeHardwareCatalog.Item fam = selectedNative(cpuFamily);
        NativeHardwareCatalog.Item speed = selectedNative(cpuSpeed);
        if (fam == null || speed == null) {
            setNativeAdapter(fpu, Collections.emptyList(), null);
            return;
        }
        List<NativeHardwareCatalog.Item> fpus =
                NativeHardwareCatalog.get(NativeHardwareCatalog.FPUS, currentMachineId(), fam.id, speed.id);
        setNativeAdapter(fpu, fpus, preferred == null ? "none" : preferred);
    }

    private void updateDynarecSupport() {
        NativeHardwareCatalog.Item speed = selectedNative(cpuSpeed);
        if (speed == null) return;
        int flags = speed.extraInt(1, 0);
        if ((flags & 1) == 0) {
            dynarec.setChecked(false);
            dynarec.setEnabled(false);
        } else if ((flags & 2) != 0) {
            dynarec.setChecked(true);
            dynarec.setEnabled(false);
        } else {
            dynarec.setEnabled(true);
        }
        waitStates.setEnabled(true);
    }

    private void updateRamUi(NativeHardwareCatalog.Item m, int configuredKb) {
        if (m == null) {
            ramValue.setText(Integer.toString(configuredKb / 1024));
            ramHint.setText("MB");
            return;
        }
        int min = Math.max(1, m.extraInt(1, 64));
        int max = Math.max(min, m.extraInt(2, min));
        int gran = Math.max(1, m.extraInt(3, 1));

        boolean kb = gran < 1024;
        int divisor = kb ? 1 : 1024;
        int value = Math.max(min, Math.min(max, configuredKb));
        ramValue.setText(Integer.toString(Math.max(1, value / divisor)));
        ramHint.setText((kb ? "KB" : "MB") + "  •  min " + (min / divisor) +
                "  max " + (max / divisor) + "  step " + Math.max(1, gran / divisor));
    }

    private int normalizedRamKb() {
        NativeHardwareCatalog.Item m = currentMachineItem;
        int min = m == null ? 64 : Math.max(1, m.extraInt(1, 64));
        int max = m == null ? 524288 : Math.max(min, m.extraInt(2, 524288));
        int gran = m == null ? 1024 : Math.max(1, m.extraInt(3, 1024));
        boolean kb = gran < 1024;
        int divisor = kb ? 1 : 1024;
        long entered;
        try { entered = Long.parseLong(ramValue.getText().toString().trim()); }
        catch (Exception e) { entered = Math.max(1, min / divisor); }
        long kbValue = entered * divisor;
        kbValue = Math.max(min, Math.min(max, kbValue));
        kbValue = min + Math.round((kbValue - min) / (double) gran) * (long) gran;
        kbValue = Math.max(min, Math.min(max, kbValue));
        return (int) kbValue;
    }

    private String defaultHdcValue(int slot) {
        if (slot != 0) return "none";
        NativeHardwareCatalog.Item m = currentMachineItem;
        // Machine item extra columns: type, min RAM, max RAM, RAM granularity, has-internal-HDC.
        return m != null && m.extraInt(4, 0) != 0 ? "internal" : "none";
    }

    private void updateNetworkEnabled(int slot) {
        if (slot < 0 || slot >= network.length) return;
        String value = selectedValue(network[slot]);
        slirp[slot].setEnabled(!"none".equalsIgnoreCase(value));
        if ("none".equalsIgnoreCase(value)) slirp[slot].setChecked(false);
    }

    private int currentMachineId() {
        NativeHardwareCatalog.Item m = selectedNative(machine);
        return m == null ? -1 : m.id;
    }

    private void setNativeAdapter(Spinner spinner, List<NativeHardwareCatalog.Item> list, String preferredValue) {
        if (list == null) list = Collections.emptyList();
        ArrayAdapter<NativeHardwareCatalog.Item> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>(list));
        spinner.setAdapter(adapter);
        if (preferredValue != null) {
            for (int i = 0; i < adapter.getCount(); i++) {
                NativeHardwareCatalog.Item it = adapter.getItem(i);
                if (it != null && (it.value.equalsIgnoreCase(preferredValue) ||
                        Integer.toString(it.id).equals(preferredValue))) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }
        if (adapter.getCount() > 0) spinner.setSelection(0);
    }

    private NativeHardwareCatalog.Item findCpuExact(List<NativeHardwareCatalog.Item> list,
                                                      String preferredSpeed, String preferredMulti) {
        if (list == null || list.isEmpty()) return null;
        long requestedSpeed = parseLong(preferredSpeed, Long.MIN_VALUE);
        if (requestedSpeed == Long.MIN_VALUE) return null;
        double requestedMulti = parseDouble(preferredMulti, Double.NaN);

        NativeHardwareCatalog.Item speedOnly = null;
        for (NativeHardwareCatalog.Item it : list) {
            if (it == null || parseLong(it.value, Long.MIN_VALUE) != requestedSpeed) continue;
            if (speedOnly == null) speedOnly = it;
            if (!Double.isNaN(requestedMulti)) {
                double multi = it.extraDouble(0, Double.NaN);
                if (!Double.isNaN(multi) && Math.abs(multi - requestedMulti) < 0.0000001d)
                    return it;
            }
        }
        // Older profiles may omit cpu_multi. In that case preserving the exact
        // configured clock is better than falling back to another frequency.
        return Double.isNaN(requestedMulti) ? speedOnly : null;
    }

    private boolean containsCpuRow(List<NativeHardwareCatalog.Item> list, NativeHardwareCatalog.Item wanted) {
        if (list == null || wanted == null) return false;
        for (NativeHardwareCatalog.Item it : list) {
            if (it == null) continue;
            if (it.id == wanted.id && it.value.equals(wanted.value) &&
                    Math.abs(it.extraDouble(0, Double.NaN) - wanted.extraDouble(0, Double.NaN)) < 0.0000001d)
                return true;
        }
        return false;
    }

    /**
     * Select a CPU exactly like the desktop 86Box configuration loader:
     * first match cpu_speed + cpu_multi, otherwise choose the first eligible
     * CPU at or above the requested speed, or the fastest eligible CPU.
     *
     * v0.9.3 matched cpu_speed only and then silently fell back to row 0.
     * With imported desktop profiles that could make a configured 200 MHz CPU
     * appear as the first compatible entry (for example 66 MHz) and a later
     * SAVE would overwrite the original desktop CPU setting.
     */
    private void setCpuSpeedAdapter(Spinner spinner, List<NativeHardwareCatalog.Item> list,
                                    String preferredSpeed, String preferredMulti) {
        if (list == null) list = Collections.emptyList();
        ArrayAdapter<NativeHardwareCatalog.Item> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>(list));
        spinner.setAdapter(adapter);
        if (adapter.getCount() <= 0) return;

        long requestedSpeed = parseLong(preferredSpeed, Long.MIN_VALUE);
        double requestedMulti = parseDouble(preferredMulti, Double.NaN);

        // Exact desktop profile match: both real clock and multiplier matter.
        if (requestedSpeed != Long.MIN_VALUE && !Double.isNaN(requestedMulti)) {
            for (int i = 0; i < adapter.getCount(); i++) {
                NativeHardwareCatalog.Item it = adapter.getItem(i);
                if (it == null) continue;
                long speed = parseLong(it.value, Long.MIN_VALUE);
                double multi = it.extraDouble(0, Double.NaN);
                if (speed == requestedSpeed && !Double.isNaN(multi) &&
                        Math.abs(multi - requestedMulti) < 0.0000001d) {
                    spinner.setSelection(i);
                    DiagnosticLogger.i("CpuTrace", "setCpuSpeedAdapter EXACT index=" + i + " item=" + describeItem(it));
                    return;
                }
            }
        }

        // Mirror src/config.c: closest eligible CPU >= requested speed; if none,
        // use the fastest eligible CPU. This avoids the old unconditional row-0 fallback.
        if (requestedSpeed != Long.MIN_VALUE) {
            int closest = -1;
            int fastest = -1;
            for (int i = 0; i < adapter.getCount(); i++) {
                NativeHardwareCatalog.Item it = adapter.getItem(i);
                if (it == null) continue;
                long speed = parseLong(it.value, Long.MIN_VALUE);
                if (speed == Long.MIN_VALUE) continue;
                if (closest < 0 && speed >= requestedSpeed) closest = i;
                // src/config.c stores the last eligible CPU as its final fallback.
                fastest = i;
            }
            if (closest >= 0) {
                spinner.setSelection(closest);
                DiagnosticLogger.w("CpuTrace", "setCpuSpeedAdapter FALLBACK closest>=requested index=" + closest +
                        " requestedSpeed=" + requestedSpeed + " selected=" + describeItem(adapter.getItem(closest)));
                return;
            }
            if (fastest >= 0) {
                spinner.setSelection(fastest);
                DiagnosticLogger.w("CpuTrace", "setCpuSpeedAdapter FALLBACK fastest index=" + fastest +
                        " requestedSpeed=" + requestedSpeed + " selected=" + describeItem(adapter.getItem(fastest)));
                return;
            }
        }

        // No usable profile value: normal UI default.
        spinner.setSelection(0);
        DiagnosticLogger.w("CpuTrace", "setCpuSpeedAdapter FALLBACK row0 requestedSpeed=" + preferredSpeed +
                " requestedMulti=" + preferredMulti + " selected=" + describeItem(adapter.getItem(0)));
    }

    private String selectedCpuMultiplier() {
        NativeHardwareCatalog.Item it = selectedNative(cpuSpeed);
        return it != null && it.extra.length > 0 ? it.extra[0] : "";
    }

    private NativeHardwareCatalog.Item selectedNative(Spinner spinner) {
        Object o = spinner == null ? null : spinner.getSelectedItem();
        return o instanceof NativeHardwareCatalog.Item ? (NativeHardwareCatalog.Item) o : null;
    }

    private String selectedValue(Spinner spinner) {
        NativeHardwareCatalog.Item it = selectedNative(spinner);
        return it == null ? "" : it.value;
    }

    private void selectLocal(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            Object o = spinner.getItemAtPosition(i);
            if (o instanceof LocalOption && ((LocalOption) o).value.equalsIgnoreCase(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private String selectedLocal(Spinner spinner, String def) {
        Object o = spinner.getSelectedItem();
        return o instanceof LocalOption ? ((LocalOption) o).value : def;
    }

    private static String readableError(Throwable e) {
        if (e == null) return "unknown error";
        String message = e.getMessage();
        return (message == null || message.trim().isEmpty()) ? e.toString() : message;
    }

    private void updateMutationControls() {
        boolean canSaveHardware = configWritable && catalogReady;
        if (saveButton != null) saveButton.setEnabled(canSaveHardware);
        if (saveStartButton != null) saveStartButton.setEnabled(canSaveHardware);
        if (importHddButton != null) importHddButton.setEnabled(configWritable);
        if (createHddButton != null) createHddButton.setEnabled(configWritable);
        if (configStatus != null) {
            if (configWritable) {
                configStatus.setText("");
                configStatus.setVisibility(View.GONE);
            } else {
                configStatus.setText("CONFIG LOAD FAILED — read-only protection is active.\n" + configLoadError);
                configStatus.setVisibility(View.VISIBLE);
            }
        }
    }

    private void markConfigReadOnly(Throwable e) {
        configWritable = false;
        configLoadError = readableError(e);
        updateMutationControls();
    }

    private boolean ensureConfigWritable() {
        if (configWritable) return true;
        showConfigLoadFailure(true);
        return false;
    }

    private void showConfigLoadFailure(boolean dialog) {
        updateMutationControls();
        if (!dialog) return;
        new AlertDialog.Builder(this)
                .setTitle("86box.cfg could not be loaded")
                .setMessage("The existing profile configuration is protected from being overwritten.\n\n" +
                        configLoadError +
                        "\n\nRe-open the profile after repairing/restoring 86box.cfg. SAVE, SAVE & START and HDD changes remain disabled in this editor session.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void refreshHddStatus() {
        if (hddStatus == null || cfgFile == null) return;
        if (!configWritable) {
            hddStatus.setText("HDD editing disabled because 86box.cfg could not be loaded safely.");
            return;
        }
        try {
            config = IniConfig.load(cfgFile);
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < HddProfileStorage.SLOT_COUNT; i++) {
                if (i > 0) text.append('\n');
                text.append(HddProfileStorage.slotSummary(profileDir, config, i));
            }
            hddStatus.setText(text.toString());
        } catch (Exception e) {
            markConfigReadOnly(e);
            hddStatus.setText("Could not read HDD configuration: " + readableError(e));
        }
    }

    private String[] hddSlotLabels() {
        String[] out = new String[HddProfileStorage.SLOT_COUNT];
        for (int i = 0; i < out.length; i++)
            out[i] = HddProfileStorage.slotSummary(profileDir, config, i);
        return out;
    }

    private void chooseHddSlotForImport() {
        if (!ensureConfigWritable()) return;
        new AlertDialog.Builder(this)
                .setTitle("Import HDD image into slot")
                .setItems(hddSlotLabels(), (dialog, which) -> confirmHddSlotReplacement(which, () -> openHddPicker(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openHddPicker(int slot) {
        pendingHddImportSlot = slot;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_IMPORT_HDD_IMAGE);
        } catch (Throwable t) {
            pendingHddImportSlot = -1;
            Toast.makeText(this, "No Android file picker available", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmHddSlotReplacement(int slot, Runnable action) {
        if (!ensureConfigWritable()) return;
        try {
            config = IniConfig.load(cfgFile);
        } catch (Exception e) {
            markConfigReadOnly(e);
            showConfigLoadFailure(true);
            return;
        }
        if (!HddProfileStorage.slotOccupied(config, slot)) {
            action.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Replace HDD " + (slot + 1) + "?")
                .setMessage("The current HDD mapping in this slot will be replaced. The old image file itself will not be deleted.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Replace", (d, w) -> action.run())
                .show();
    }

    private AlertDialog showBusy(String message) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(28), dp(20), dp(28), dp(20));
        ProgressBar bar = new ProgressBar(this);
        box.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView label = text(message, 14);
        label.setPadding(0, dp(12), 0, 0);
        box.addView(label);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).setCancelable(false).create();
        dialog.show();
        return dialog;
    }

    private void importHddImage(Uri uri, int slot) {
        if (!ensureConfigWritable()) return;
        final AlertDialog busy = showBusy("Copying HDD image into profile…");
        new Thread(() -> {
            try {
                HddProfileStorage.Result result = HddProfileStorage.importImage(this, profileDir, cfgFile, uri, slot);
                runOnUiThread(() -> {
                    busy.dismiss();
                    refreshHddStatus();
                    Toast.makeText(this,
                            "HDD " + (slot + 1) + " imported: " + result.file.getName() +
                                    " (" + result.bus.toUpperCase(Locale.ROOT) + " " + result.channel + ")",
                            Toast.LENGTH_LONG).show();
                    FullscreenUi.apply(this);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("HDD import failed")
                            .setMessage(e.toString())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }, "86Box-hdd-import").start();
    }

    private void showCreateHddDialog() {
        if (!ensureConfigWritable()) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        form.setPadding(pad, 0, pad, 0);

        TextView slotLabel = text("HDD slot", 13);
        form.addView(slotLabel);
        Spinner slot = new Spinner(this);
        slot.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new ArrayList<>(Arrays.asList(hddSlotLabels()))));
        form.addView(slot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView nameLabel = text("Image file name", 13);
        form.addView(nameLabel);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setText("new_disk.img");
        form.addView(name);

        TextView sizeLabel = text("Size in MB (1 - 130000)", 13);
        sizeLabel.setPadding(0, dp(8), 0, 0);
        form.addView(sizeLabel);
        EditText size = new EditText(this);
        size.setInputType(InputType.TYPE_CLASS_NUMBER);
        size.setSingleLine(true);
        size.setText("2048");
        form.addView(size);

        TextView note = text("The RAW image is created in media/hdd. On Android app storage it is normally sparse, so empty space is not physically written immediately.", 11);
        note.setPadding(0, dp(8), 0, 0);
        form.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create HDD")
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Create", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int which = slot.getSelectedItemPosition();
            long sizeMb;
            try { sizeMb = Long.parseLong(size.getText().toString().trim()); }
            catch (Exception e) { sizeMb = -1; }
            if (sizeMb < 1 || sizeMb > 130000) {
                size.setError("Enter 1 - 130000 MB");
                return;
            }
            String filename = name.getText().toString().trim();
            dialog.dismiss();
            final long finalSizeMb = sizeMb;
            confirmHddSlotReplacement(which, () -> createHddImage(which, filename, finalSizeMb));
        }));
        dialog.show();
    }

    private void createHddImage(int slot, String filename, long sizeMb) {
        if (!ensureConfigWritable()) return;
        final AlertDialog busy = showBusy("Creating HDD image…");
        new Thread(() -> {
            try {
                HddProfileStorage.Result result = HddProfileStorage.createRawImage(
                        profileDir, cfgFile, slot, filename, sizeMb);
                runOnUiThread(() -> {
                    busy.dismiss();
                    refreshHddStatus();
                    Toast.makeText(this,
                            "HDD " + (slot + 1) + " created: " + result.file.getName(),
                            Toast.LENGTH_LONG).show();
                    FullscreenUi.apply(this);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("Could not create HDD")
                            .setMessage(e.toString())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }, "86Box-hdd-create").start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        FullscreenUi.apply(this);
        if (requestCode != REQ_IMPORT_HDD_IMAGE) return;
        int slot = pendingHddImportSlot;
        pendingHddImportSlot = -1;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || slot < 0) return;
        importHddImage(data.getData(), slot);
    }

    private void save(boolean start) {
        if (!ensureConfigWritable()) return;
        traceSelection("SAVE requested start=" + start);
        try {
            IniConfig c = IniConfig.load(cfgFile);

            NativeHardwareCatalog.Item m = selectedNative(machine);
            NativeHardwareCatalog.Item fam = selectedNative(cpuFamily);
            NativeHardwareCatalog.Item speed = selectedNative(cpuSpeed);
            NativeHardwareCatalog.Item fpuItem = selectedNative(fpu);

            if (m != null) c.set("Machine", "machine", m.value);
            if (fam != null) c.set("Machine", "cpu_family", fam.value);
            if (speed != null) {
                c.set("Machine", "cpu_speed", speed.value);
                c.set("Machine", "cpu_multi", speed.extra.length > 0 ? speed.extra[0] : "1");
            }
            if (fpuItem != null) c.set("Machine", "fpu_type", fpuItem.value);

            c.set("Machine", "mem_size", Integer.toString(normalizedRamKb()));
            c.set("Machine", "cpu_use_dynarec", dynarec.isChecked() ? "1" : "0");
            c.set("Machine", "android_cpu_engine", selectedLocal(cpuExecutionMode, "native"));
            c.set("Machine", "fpu_softfloat", softFloat.isChecked() ? "1" : "0");
            c.set("Machine", "cpu_waitstates", selectedLocal(waitStates, "0"));
            c.set("Machine", "pit_mode", selectedLocal(pitMode, "-1"));
            String ts = selectedLocal(timeSync, "local");
            if ("local".equals(ts)) c.remove("Machine", "time_sync");
            else c.set("Machine", "time_sync", ts);

            c.set("Video", "gfxcard", selectedValue(video1));
            c.set("Video", "gfxcard_2", selectedValue(video2));
            setBoolOrRemove(c, "Video", "voodoo", voodoo.isChecked());
            setBoolOrRemove(c, "Video", "8514a", ibm8514.isChecked());
            setBoolOrRemove(c, "Video", "xga", xga.isChecked());
            setBoolOrRemove(c, "Video", "da2", da2.isChecked());

            String mouseValue = selectedValue(mouse);
            if (mouseValue.isEmpty()) c.remove("Input devices", "mouse_type");
            else c.set("Input devices", "mouse_type", mouseValue);

            for (int i = 0; i < sound.length; i++) {
                String key = i == 0 ? "sndcard" : "sndcard" + (i + 1);
                String value = selectedValue(sound[i]);
                if (value.isEmpty() || "none".equals(value)) c.remove("Sound", key);
                else c.set("Sound", key, value);
            }

            String midi = selectedValue(midiOut);
            if (midi.isEmpty() || "none".equals(midi)) c.remove("Sound", "midi_device");
            else c.set("Sound", "midi_device", midi);

            for (int i = 0; i < network.length; i++) {
                String prefix = String.format(Locale.ROOT, "net_%02d_", i + 1);
                String card = selectedValue(network[i]);
                if (card.isEmpty()) card = "none";
                c.set("Network", prefix + "card", card);
                c.set("Network", prefix + "net_type",
                        "none".equals(card) ? "none" : (slirp[i].isChecked() ? "slirp" : "none"));
                c.set("Network", prefix + "host_device", "none");
            }

            String fdcValue = selectedValue(fdc);
            if (fdcValue.isEmpty() || "internal".equals(fdcValue)) c.remove("Storage controllers", "fdc");
            else c.set("Storage controllers", "fdc", fdcValue);

            String cdif = selectedValue(cdromInterface);
            if (cdif.isEmpty() || "none".equals(cdif)) c.remove("Storage controllers", "cdrom_interface");
            else c.set("Storage controllers", "cdrom_interface", cdif);

            for (int i = 0; i < hdc.length; i++) {
                String key = "hdc_" + (i + 1);
                String value = selectedValue(hdc[i]);
                String def = defaultHdcValue(i);
                if (value.isEmpty() || def.equals(value)) c.remove("Storage controllers", key);
                else c.set("Storage controllers", key, value);
            }

            for (int i = 0; i < scsi.length; i++) {
                String key = "scsicard_" + (i + 1);
                String value = selectedValue(scsi[i]);
                if (value.isEmpty() || "none".equals(value)) c.remove("Storage controllers", key);
                else c.set("Storage controllers", key, value);
            }

            for (int i = 0; i < serial.length; i++) {
                String enKey = "serial" + (i + 1) + "_enabled";
                String devKey = "serial" + (i + 1) + "_device";
                boolean defaultEnabled = i < 2;
                if (serialEnabled[i].isChecked() == defaultEnabled) c.remove("Ports (COM & LPT)", enKey);
                else c.set("Ports (COM & LPT)", enKey, serialEnabled[i].isChecked() ? "1" : "0");
                String value = selectedValue(serial[i]);
                if (value.isEmpty() || "none".equals(value)) c.remove("Ports (COM & LPT)", devKey);
                else c.set("Ports (COM & LPT)", devKey, value);
            }
            for (int i = 0; i < lpt.length; i++) {
                String enKey = "lpt" + (i + 1) + "_enabled";
                String devKey = "lpt" + (i + 1) + "_device";
                boolean defaultEnabled = i == 0;
                if (lptEnabled[i].isChecked() == defaultEnabled) c.remove("Ports (COM & LPT)", enKey);
                else c.set("Ports (COM & LPT)", enKey, lptEnabled[i].isChecked() ? "1" : "0");
                String value = selectedValue(lpt[i]);
                if (value.isEmpty() || "none".equals(value)) c.remove("Ports (COM & LPT)", devKey);
                else c.set("Ports (COM & LPT)", devKey, value);
            }

            String rtc = selectedValue(isaRtc);
            if (rtc.isEmpty() || "none".equals(rtc)) c.remove("Other peripherals", "isartc_type");
            else c.set("Other peripherals", "isartc_type", rtc);
            for (int i = 0; i < isaMem.length; i++) {
                String key = "isamem" + i + "_type";
                String value = selectedValue(isaMem[i]);
                if (value.isEmpty() || "none".equals(value)) c.remove("Other peripherals", key);
                else c.set("Other peripherals", key, value);
            }
            for (int i = 0; i < mcaMem.length; i++) {
                String key = "mcamem" + i + "_type";
                String value = selectedValue(mcaMem[i]);
                if (value.isEmpty() || "none".equals(value)) c.remove("Other peripherals", key);
                else c.set("Other peripherals", key, value);
            }
            for (int i = 0; i < isaRom.length; i++) {
                String key = "isarom" + i + "_type";
                String value = selectedValue(isaRom[i]);
                if (value.isEmpty() || "none".equals(value)) c.remove("Other peripherals", key);
                else c.set("Other peripherals", key, value);
            }
            setBoolOrRemove(c, "Other peripherals", "bugger_enabled", bugger.isChecked());
            setBoolOrRemove(c, "Other peripherals", "postcard_enabled", postcard.isChecked());
            setBoolOrRemove(c, "Other peripherals", "unittester_enabled", unittester.isChecked());
            setBoolOrRemove(c, "Other peripherals", "novell_keycard_enabled", novellKeycard.isChecked());
            setBoolOrRemove(c, "Other peripherals", "softpower_enabled", softPower.isChecked());

            c.save(cfgFile);
            /* Keep the in-memory snapshot aligned with the file we just wrote.
               v0.9.12 traced the pre-save object here, which could make the
               diagnostics claim that an old CPU speed survived even when the
               file was saved correctly. */
            config = c;
            traceRawConfig("after SAVE");
            DiagnosticLogger.i("HardwareConfig", "Hardware profile saved start=" + start + " cfg=" + cfgFile.getAbsolutePath());
            Toast.makeText(this, "Full hardware profile saved", Toast.LENGTH_SHORT).show();
            if (start) startVm();
            else { setResult(RESULT_OK); finish(); }
        } catch (Exception e) {
            DiagnosticLogger.e("HardwareConfig", "Could not save hardware", e);
            new AlertDialog.Builder(this)
                    .setTitle("Could not save hardware")
                    .setMessage(e.toString())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private static void setBoolOrRemove(IniConfig c, String section, String key, boolean value) {
        if (value) c.set(section, key, "1");
        else c.remove(section, key);
    }

    private void startVm() {
        catalogStatus.setText("Preparing ROMs/assets…");
        new Thread(() -> {
            try {
                RuntimeAssets.prepare(this);
                runOnUiThread(() -> {
                    Intent i = new Intent(this, Box86Activity.class);
                    i.putExtra(ProfileManagerActivity.EXTRA_PROFILE_PATH, profileDir.getAbsolutePath());
                    i.putExtra(ProfileManagerActivity.EXTRA_PROFILE_NAME,
                            getIntent().getStringExtra(ProfileManagerActivity.EXTRA_PROFILE_NAME));
                    startActivity(i);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Start failed")
                        .setMessage(e.toString())
                        .setPositiveButton("OK", null)
                        .show());
            }
        }, "86Box-hw-start").start();
    }

    private void traceRawConfig(String stage) {
        if (config == null) return;
        DiagnosticLogger.i("CpuTrace", stage +
                " cfgMachine=" + config.get("Machine", "machine", "<missing>") +
                " cfgFamily=" + config.get("Machine", "cpu_family", "<missing>") +
                " cfgSpeed=" + config.get("Machine", "cpu_speed", "<missing>") +
                " cfgMulti=" + config.get("Machine", "cpu_multi", "<missing>") +
                " cfgFpu=" + config.get("Machine", "fpu_type", "<missing>"));
    }

    private void traceSelection(String stage) {
        try {
            DiagnosticLogger.i("CpuTrace", stage +
                    " selectedMachineType=" + describeItem(selectedNative(machineType)) +
                    " selectedMachine=" + describeItem(selectedNative(machine)) +
                    " selectedFamily=" + describeItem(selectedNative(cpuFamily)) +
                    " selectedSpeed=" + describeItem(selectedNative(cpuSpeed)) +
                    " selectedMulti=" + selectedCpuMultiplier() +
                    " selectedFpu=" + describeItem(selectedNative(fpu)));
        } catch (Throwable t) {
            DiagnosticLogger.w("CpuTrace", stage + " selection trace failed", t);
        }
    }

    private static String describeItem(NativeHardwareCatalog.Item it) {
        if (it == null) return "<null>";
        return "{label='" + it.label + "',value='" + it.value + "',id=" + it.id +
                ",extra=" + Arrays.toString(it.extra) + "}";
    }

    private static String describeItems(List<NativeHardwareCatalog.Item> items, int max) {
        if (items == null) return "<null>";
        StringBuilder b = new StringBuilder("[");
        int n = Math.min(items.size(), Math.max(0, max));
        for (int i = 0; i < n; i++) {
            if (i > 0) b.append(", ");
            b.append(describeItem(items.get(i)));
        }
        if (items.size() > n) b.append(", ... +").append(items.size() - n).append(" more");
        return b.append(']').toString();
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    private boolean isCompactLayout() {
        Configuration c = getResources().getConfiguration();
        return (c.screenWidthDp > 0 && c.screenWidthDp < 420) ||
                (c.screenHeightDp > 0 && c.screenHeightDp < 480);
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

    private int dp(int v) {
        return Math.max(1, Math.round(v * getResources().getDisplayMetrics().density * uiScaleFactor()));
    }
}
