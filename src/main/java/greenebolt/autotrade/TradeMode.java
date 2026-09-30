package greenebolt.autotrade;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

public enum TradeMode implements IConfigOptionListEntry {
    SINGLE("single", "单次交易"),
    AUTO("auto", "自动交易");

    private final String name;
    private final String displayName;

    TradeMode(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    @Override
    public String getStringValue() {
        return this.name;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        return this == SINGLE ? AUTO : SINGLE;
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        return "auto".equals(value) ? AUTO : SINGLE;
    }
}
