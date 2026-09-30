package greenebolt.autotrade.stlocator;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** 单条多环定位规则的编辑子界面：最小/最大/种子输入框 + 数据包勾选列表。 */
public class StRuleEditScreen extends GuiBase {
    private static final int FIELD_WIDTH = 220;
    private static final int PACK_ROW_HEIGHT = 16;

    private final Screen parent;
    private final StLocator.StRule original;
    private final int index;
    private final Consumer<StLocator.StRule> onConfirm;

    private GuiTextFieldGeneric minField;
    private GuiTextFieldGeneric maxField;
    private GuiTextFieldGeneric seedField;
    private final List<String> availablePacks = new ArrayList<>();
    /** 勾选中的数据包（文件名，含 .zip） */
    private final LinkedHashSet<String> checkedPacks = new LinkedHashSet<>();
    private int packScroll;

    private static final int PACKS_TOP = 148;

    public StRuleEditScreen(Screen parent, StLocator.StRule original, int index,
                            Consumer<StLocator.StRule> onConfirm) {
        this.setParent(parent);
        this.parent = parent;
        this.original = original;
        this.index = index;
        this.onConfirm = onConfirm;
        this.title = index >= 0 ? "编辑规则" : "添加规则";
        readAvailablePacks();
        // 新规则或省略数据包段（=全部）时默认全选
        if (original == null || original.packs() == null) {
            this.checkedPacks.addAll(this.availablePacks);
        } else {
            for (String pack : original.packs()) {
                this.availablePacks.stream()
                    .filter(a -> normalize(a).equalsIgnoreCase(normalize(pack)))
                    .findFirst().ifPresent(this.checkedPacks::add);
            }
        }
    }

    private static String normalize(String name) {
        return name.endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
    }

    private void readAvailablePacks() {
        Path dir = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve("satella").resolve("datapacks");
        this.availablePacks.clear();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.getFileName().toString().endsWith(".zip"))
                .forEach(f -> this.availablePacks.add(f.getFileName().toString()));
        } catch (IOException e) {
            StLocator.LOGGER.warn("读取数据包目录失败", e);
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        int w = this.getScreenWidth();
        int x = w / 2 - FIELD_WIDTH / 2;
        int y = 30;

        if (this.minField == null) {
            this.minField = new GuiTextFieldGeneric(x, y, FIELD_WIDTH, 16, this.font);
            this.maxField = new GuiTextFieldGeneric(x, y + 34, FIELD_WIDTH, 16, this.font);
            this.seedField = new GuiTextFieldGeneric(x, y + 68, FIELD_WIDTH, 16, this.font);
            if (this.original != null) {
                this.minField.setValue(String.valueOf(this.original.min()));
                this.maxField.setValue(String.valueOf(this.original.max()));
                this.seedField.setValue(String.valueOf(this.original.seed()));
            }
        }
        this.minField.setPosition(x, y);
        this.maxField.setPosition(x, y + 34);
        this.seedField.setPosition(x, y + 68);
        this.minField.setFocusedWrapper(true);
        addTextField(this.minField, null);
        addTextField(this.maxField, null);
        addTextField(this.seedField, null);

        // 数据包勾选列表
        int packBottom = this.getScreenHeight() - 30;
        int visible = Math.max(1, (packBottom - PACKS_TOP) / PACK_ROW_HEIGHT);
        int maxScroll = Math.max(0, this.availablePacks.size() - visible);
        if (this.packScroll > maxScroll) this.packScroll = maxScroll;
        if (this.packScroll < 0) this.packScroll = 0;
        int py = PACKS_TOP;
        for (int i = this.packScroll; i < this.availablePacks.size() && py + PACK_ROW_HEIGHT <= packBottom; i++, py += PACK_ROW_HEIGHT) {
            String name = this.availablePacks.get(i);
            boolean checked = this.checkedPacks.contains(name);
            addButton(new ButtonGeneric(x, py, FIELD_WIDTH, 14,
                (checked ? "[x] " : "[ ] ") + name), (b, mb) -> {
                if (!this.checkedPacks.remove(name)) {
                    this.checkedPacks.add(name);
                }
                this.initGui();
            });
        }

        int by = this.getScreenHeight() - 26;
        addButton(new ButtonGeneric(x, by, 100, 18, "确定"), (b, mb) -> confirm());
        addButton(new ButtonGeneric(x + 120, by, 100, 18, "取消"), (b, mb) -> closeGui(true));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        GuiContext g = GuiContext.fromGuiGraphics(extractor);
        int w = this.getScreenWidth();
        int x = w / 2 - FIELD_WIDTH / 2;
        int y = 30;
        drawStringWithShadow(g, this.title, 10, 8, 0xFFFFFFC0);
        drawStringWithShadow(g, "最小切比雪夫距离", x, y - 11, 0xFFE0E0E0);
        drawStringWithShadow(g, "最大切比雪夫距离", x, y + 23, 0xFFE0E0E0);
        drawStringWithShadow(g, "种子", x, y + 57, 0xFFE0E0E0);
        drawStringWithShadow(g, "数据包（勾选 = 检索时加载；全部不勾 = 不加载数据包 zip）",
            x, PACKS_TOP - 12, 0xFFE0E0E0);
        if (this.availablePacks.isEmpty()) {
            drawStringWithShadow(g, "config/satella/datapacks 目录下没有数据包 zip",
                x, PACKS_TOP + 4, 0xFF808080);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        this.packScroll -= (int) Math.signum(vertical);
        if (this.packScroll < 0) this.packScroll = 0;
        this.initGui();
        return true;
    }

    private void confirm() {
        int min;
        int max;
        long seed;
        try {
            min = Integer.parseInt(this.minField.getValue().trim());
        } catch (NumberFormatException e) {
            addMessage(Message.MessageType.ERROR, "最小距离必须是整数", new Object[0]);
            return;
        }
        try {
            max = Integer.parseInt(this.maxField.getValue().trim());
        } catch (NumberFormatException e) {
            addMessage(Message.MessageType.ERROR, "最大距离必须是整数", new Object[0]);
            return;
        }
        try {
            seed = Long.parseLong(this.seedField.getValue().trim());
        } catch (NumberFormatException e) {
            addMessage(Message.MessageType.ERROR, "种子必须是整数", new Object[0]);
            return;
        }
        if (min > max) {
            addMessage(Message.MessageType.ERROR, "最小距离不能大于最大距离", new Object[0]);
            return;
        }
        this.onConfirm.accept(new StLocator.StRule(min, max, seed,
            new ArrayList<>(this.checkedPacks)));
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }
}
