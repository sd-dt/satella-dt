package greenebolt.autotrade.stlocator;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 多环定位规则列表编辑器：增删改排序后写回 MaLiLib 配置（Satella.json）。 */
public class StRulesEditorScreen extends GuiBase {
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 26;
    private static final int LIST_BOTTOM_MARGIN = 30;

    private final List<StLocator.StRule> rules = new ArrayList<>();
    private int scroll;

    public StRulesEditorScreen(Screen parent) {
        this.setParent(parent);
        this.rules.addAll(StLocator.parseRules(
            greenebolt.autotrade.AutoTradeConfigs.Trade.ST_RULES.getStrings()));
        this.title = "多环定位规则编辑器";
    }

    @Override
    public void initGui() {
        super.initGui();
        int w = this.getScreenWidth();
        int listBottom = this.getScreenHeight() - LIST_BOTTOM_MARGIN;

        int visible = Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);
        int maxScroll = Math.max(0, this.rules.size() - visible);
        if (this.scroll > maxScroll) this.scroll = maxScroll;
        if (this.scroll < 0) this.scroll = 0;

        int y = LIST_TOP;
        for (int i = this.scroll; i < this.rules.size() && y + ROW_HEIGHT <= listBottom; i++, y += ROW_HEIGHT) {
            final int index = i;
            int bx = w - 170;
            addButton(new ButtonGeneric(bx, y + 2, 40, 16, "编辑"), (b, mb) -> openEdit(index));
            addButton(new ButtonGeneric(bx + 42, y + 2, 40, 16, "删除"), (b, mb) -> {
                rules.remove(index);
                this.initGui();
            });
            addButton(new ButtonGeneric(bx + 84, y + 2, 20, 16, "↑"), (b, mb) -> {
                if (index > 0) {
                    Collections.swap(rules, index, index - 1);
                    this.initGui();
                }
            });
            addButton(new ButtonGeneric(bx + 106, y + 2, 20, 16, "↓"), (b, mb) -> {
                if (index < rules.size() - 1) {
                    Collections.swap(rules, index, index + 1);
                    this.initGui();
                }
            });
        }

        int by = this.getScreenHeight() - 26;
        addButton(new ButtonGeneric(10, by, 90, 18, "添加规则"), (b, mb) -> openEdit(-1));
        addButton(new ButtonGeneric(w - 204, by, 94, 18, "保存并关闭"), (b, mb) -> saveAndClose());
        addButton(new ButtonGeneric(w - 104, by, 94, 18, "取消"), (b, mb) -> closeGui(true));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        GuiContext g = GuiContext.fromGuiGraphics(extractor);
        drawStringWithShadow(g, this.title, 10, 8, 0xFFFFFFC0);
        drawStringWithShadow(g, "格式：最小-最大:种子[:数据包1|数据包2]，距离为到世界原点 (0,0) 的切比雪夫距离",
            10, LIST_TOP - 14, 0xFF909090);
        int listBottom = this.getScreenHeight() - LIST_BOTTOM_MARGIN;
        int y = LIST_TOP;
        for (int i = this.scroll; i < this.rules.size() && y + ROW_HEIGHT <= listBottom; i++, y += ROW_HEIGHT) {
            drawStringWithShadow(g, describe(this.rules.get(i)), 12, y + 6, 0xFFE0E0E0);
        }
        if (this.rules.isEmpty()) {
            drawStringWithShadow(g, "暂无规则：未命中任何环时将使用 /st seed 的全局种子和全部数据包",
                12, LIST_TOP + 8, 0xFF808080);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        this.scroll -= (int) Math.signum(vertical);
        if (this.scroll < 0) this.scroll = 0;
        this.initGui();
        return true;
    }

    private void openEdit(int index) {
        StLocator.StRule original = index >= 0 && index < this.rules.size() ? this.rules.get(index) : null;
        StRuleEditScreen screen = new StRuleEditScreen(this, original, index, rule -> {
            if (index >= 0 && index < this.rules.size()) {
                this.rules.set(index, rule);
            } else {
                this.rules.add(rule);
            }
            this.initGui();
        });
        Minecraft.getInstance().setScreenAndShow(screen);
    }

    private void saveAndClose() {
        List<String> out = new ArrayList<>();
        for (StLocator.StRule rule : this.rules) {
            out.add(StLocator.formatRule(rule));
        }
        greenebolt.autotrade.AutoTradeConfigs.Trade.ST_RULES.setStrings(out);
        greenebolt.autotrade.AutoTradeConfigs.saveNow();
        closeGui(true);
    }

    private static String describe(StLocator.StRule rule) {
        String packs = rule.packs() == null ? "全部"
            : (rule.packs().isEmpty() ? "无" : String.join("|", rule.packs()));
        return rule.min() + " - " + rule.max() + "    种子 " + rule.seed() + "    数据包: " + packs;
    }
}
