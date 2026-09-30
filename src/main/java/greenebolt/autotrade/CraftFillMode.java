package greenebolt.autotrade;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

public enum CraftFillMode implements IConfigOptionListEntry {
    SIMULATED_CLICK("simulated_click", "模拟点击"),
    RECIPE_BOOK("recipe_book", "配方书");

    private final String stringValue;
    private final String displayName;

    CraftFillMode(String stringValue, String displayName) {
        this.stringValue = stringValue;
        this.displayName = displayName;
    }

    @Override
    public String getStringValue() {
        return stringValue;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        CraftFillMode[] values = values();
        int index = (ordinal() + (forward ? 1 : -1) + values.length) % values.length;
        return values[index];
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        for (CraftFillMode mode : values()) {
            if (mode.stringValue.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return SIMULATED_CLICK;
    }
}
