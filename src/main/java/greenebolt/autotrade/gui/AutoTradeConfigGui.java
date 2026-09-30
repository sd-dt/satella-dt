package greenebolt.autotrade.gui;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import greenebolt.autotrade.AutoTrade;
import greenebolt.autotrade.AutoTradeConfigs;

import java.util.List;

public class AutoTradeConfigGui extends GuiConfigsBase {
    private static Tab tab = Tab.TRADE;

    public AutoTradeConfigGui() {
        super(10, 50, AutoTrade.MOD_ID, null, "satella.title.configs");
        // 标题不经过语言文件，所有语言下固定显示
        setTitle("愛してる...愛してる...愛してる......");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearOptions();

        int x = 10;
        int y = 26;
        for (Tab t : Tab.values()) {
            ButtonGeneric button = new ButtonGeneric(x, y, -1, 20, t.displayName);
            button.setEnabled(tab != t);
            this.addButton(button, new TabListener(t, this));
            x += button.getWidth() + 2;
        }
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return ConfigOptionWrapper.createFor(tab.options);
    }

    private enum Tab {
        TRADE("交易", AutoTradeConfigs.Trade.TRADE_OPTIONS),
        AUTOMATION("自动化", AutoTradeConfigs.Trade.AUTOMATION_OPTIONS),
        MISC("杂项", AutoTradeConfigs.Trade.MISC_OPTIONS);

        private final String displayName;
        private final List<IConfigBase> options;

        Tab(String displayName, List<IConfigBase> options) {
            this.displayName = displayName;
            this.options = options;
        }
    }

    private record TabListener(Tab tab, AutoTradeConfigGui parent) implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            AutoTradeConfigGui.tab = this.tab;

            this.parent.reCreateListWidget();
            if (this.parent.getListWidget() != null) {
                this.parent.getListWidget().resetScrollbarPosition();
            }
            this.parent.initGui();
        }
    }
}
