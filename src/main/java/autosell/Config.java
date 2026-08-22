package autosell;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/autosell.json");
    private static Config INSTANCE;

    // Config settings
    public String key = "";
    public String sellMode = "Gui"; // "Gui" or "All"
    public String sellCommand = "sell all";
    public int delay = 10; // delay in seconds for All mode
    public int moveDelayTicks = 0; // delay between moving item stacks in ticks
    public int sellDelayTicks = 0; // delay before clicking sell button in ticks
    public int sellCommandDelayTicks = 0; // anti-spam command cooldown in ticks (5 seconds)
    public int sellSlot = 53; // slot index of the sell button (green glass pane)
    public boolean autoCoordinatesEnabled = true;
    public double positionThreshold = 5.0;
    public String guiTitleKeyword = "Sell";
    public boolean autoSync = true;
    public int requiredSlots = 36;
    public String offlineKeyHash = "";
    public String offlineHwidHash = "";
    public long offlineLicenseExpiresAt = 0L;

    public static Config get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        try {
            if (FILE.exists()) {
                try (FileReader reader = new FileReader(FILE)) {
                    INSTANCE = GSON.fromJson(reader, Config.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (INSTANCE == null) {
            INSTANCE = new Config();
            save();
        } else if (!INSTANCE.key.isBlank() && !INSTANCE.autoSync) {
            INSTANCE.autoSync = true;
            save();
        }
    }

    public static void save() {
        if (INSTANCE == null) {
            INSTANCE = new Config();
        }
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
