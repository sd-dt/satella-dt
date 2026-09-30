package greenebolt.autotrade;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

public enum AutomationMode implements IConfigOptionListEntry {
    CRAFTING("crafting", "合成"),
    STONECUTTING("stonecutting", "切石");

    private final String stringValue;
    private final String displayName;

    AutomationMode(String stringValue, String displayName) {
        this.stringValue = stringValue;
        this.displayName = displayName;
    }

    @Override
    public String getStringValue() {
        return this.stringValue;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        return this == CRAFTING ? STONECUTTING : CRAFTING;
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        return "stonecutting".equals(value) ? STONECUTTING : CRAFTING;
    }
}
