package greenebolt.autotrade;

import fi.dy.masa.malilib.util.InfoUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class AutoStonecutController {
    private static final int INPUT_SLOT = StonecutterMenu.INPUT_SLOT;
    private static final int RESULT_SLOT = StonecutterMenu.RESULT_SLOT;

    private static int openCooldown;
    private static int stonecutTicker;
    /** “未找到目标配方”在物品栏上方停留的剩余刻数，期间不再覆盖显示“全自动切石中...” */
    private static int missingMessageTicks;

    // 环形扫描游标：与全自动合成相同，记住上次扫到的位置，下次从下一个槽位继续
    private static AbstractContainerMenu scanMenu;
    private static String scanInputKey;
    private static int scanCursor = -1;

    private AutoStonecutController() {}

    public static boolean isActive() {
        return AutoTradeConfigs.Trade.AUTOMATION.getBooleanValue()
                && AutoTradeConfigs.Trade.AUTOMATION_MODE.getOptionListValue() == AutomationMode.STONECUTTING;
    }

    public static void tick(Minecraft minecraft) {
        if (!isActive() || minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            stonecutTicker = 0;
            return;
        }

        if (missingMessageTicks > 0) {
            missingMessageTicks--;
        } else {
            InfoUtils.sendVanillaMessage(Component.literal("全自动切石中...").withStyle(ChatFormatting.GREEN));
        }

        if (minecraft.player.containerMenu instanceof StonecutterMenu menu) {
            if (++stonecutTicker >= AutoTradeConfigs.Trade.AUTOMATION_INTERVAL.getIntegerValue()) {
                stonecutTicker = 0;
                stonecutHidden(menu, minecraft);
            }
            return;
        }

        stonecutTicker = 0;
        if (openCooldown > 0) {
            --openCooldown;
            return;
        }

        BlockPos cutter = findStonecutter(minecraft);
        if (cutter != null) {
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(cutter), Direction.UP, cutter, false));
        }
        openCooldown = 10;
    }

    private static BlockPos findStonecutter(Minecraft minecraft) {
        BlockPos center = minecraft.player.blockPosition();
        BlockPos best = null;
        double bestDistance = 4.5 * 4.5;
        for (int x = -4; x <= 4; x++) for (int y = -4; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            BlockPos pos = center.offset(x, y, z);
            if (!minecraft.level.getBlockState(pos).is(Blocks.STONECUTTER)) continue;
            double distance = minecraft.player.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance <= bestDistance) { best = pos; bestDistance = distance; }
        }
        return best;
    }

    private static void stonecutHidden(StonecutterMenu menu, Minecraft minecraft) {
        if (!menu.getCarried().isEmpty()) {
            returnCursorToInventoryOrDrop(menu, minecraft);
            return;
        }

        String inputKey = AutoTradeConfigs.Trade.STONECUTTING_INPUT.getStringValue();
        ItemStack input = menu.getSlot(INPUT_SLOT).getItem();
        if (!input.isEmpty() && !matches(inputKey, input)) {
            // 输入槽里是错误物品：放回背包
            click(minecraft, menu, INPUT_SLOT, 0, ContainerInput.PICKUP);
            returnCursorToInventoryOrDrop(menu, minecraft);
            return;
        }
        if (input.isEmpty()) {
            if (!inputKey.isBlank()) {
                placeInput(menu, inputKey, minecraft);
            }
            return;
        }

        String outputKey = AutoTradeConfigs.Trade.STONECUTTING_OUTPUT.getStringValue();
        ItemStack output = menu.getSlot(RESULT_SLOT).getItem();
        if (!output.isEmpty() && matches(outputKey, output)) {
            // button=1：原版会对同一输出槽循环取出，直到原料耗尽，全部丢出
            throwInternal(menu, RESULT_SLOT, minecraft);
            return;
        }

        int desired = findRecipeIndex(menu, outputKey, minecraft);
        if (desired < 0) {
            missingMessageTicks = 40;
            InfoUtils.sendVanillaMessage(Component.literal("未找到目标配方").withStyle(ChatFormatting.RED));
            return;
        }
        if (menu.getSelectedRecipeIndex() != desired) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, desired);
        }
    }

    private static void placeInput(StonecutterMenu menu, String inputKey, Minecraft minecraft) {
        int slotCount = menu.slots.size();
        if (slotCount == 0) {
            return;
        }
        prepareScanState(menu, inputKey);
        int start = (scanCursor + 1 + slotCount) % slotCount;
        for (int offset = 0; offset < slotCount; offset++) {
            int source = (start + offset) % slotCount;
            scanCursor = source;
            if (source == INPUT_SLOT || source == RESULT_SLOT) {
                continue;
            }
            ItemStack sourceStack = menu.getSlot(source).getItem();
            if (sourceStack.isEmpty() || !matches(inputKey, sourceStack)) {
                continue;
            }
            // 与全自动合成相同：数量过少的材料堆不动，避免把零散的原料拆散
            int residue = AutoTradeConfigs.Trade.CRAFT_RESIDUE.getIntegerValue();
            if (sourceStack.getCount() < 2 * residue) {
                continue;
            }

            ItemStack input = menu.getSlot(INPUT_SLOT).getItem();
            int amount = (sourceStack.getCount() + 1) / 2;
            if (!input.isEmpty() && input.getCount() + amount > input.getMaxStackSize()) {
                return;
            }

            click(minecraft, menu, source, 1, ContainerInput.PICKUP);
            if (!matches(inputKey, menu.getCarried())) {
                returnCursorToInventoryOrDrop(menu, minecraft);
                continue;
            }
            click(minecraft, menu, INPUT_SLOT, 0, ContainerInput.PICKUP);
            if (!menu.getCarried().isEmpty()) {
                click(minecraft, menu, source, 0, ContainerInput.PICKUP);
            }
            if (!menu.getCarried().isEmpty()) {
                returnCursorToInventoryOrDrop(menu, minecraft);
            }
            return;
        }
    }

    private static int findRecipeIndex(StonecutterMenu menu, String outputKey, Minecraft minecraft) {
        if (outputKey.isBlank() || minecraft.level == null) {
            return -1;
        }
        SelectableRecipe.SingleInputSet<StonecutterRecipe> recipes = menu.getVisibleRecipes();
        if (recipes.isEmpty()) {
            return -1;
        }
        ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries = recipes.entries();
        for (int index = 0; index < entries.size(); index++) {
            ItemStack display = entries.get(index).recipe().optionDisplay().resolveForFirstStack(context);
            if (!display.isEmpty() && matches(outputKey, display)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean matches(String config, ItemStack stack) {
        Item expected = ItemNameUtils.parseItem(config);
        return expected != null && stack.getItem() == expected;
    }

    private static void prepareScanState(AbstractContainerMenu menu, String inputKey) {
        if (scanMenu == menu
                && inputKey.equals(scanInputKey)
                && scanCursor < menu.slots.size()) {
            return;
        }
        scanMenu = menu;
        scanInputKey = inputKey;
        scanCursor = menu.slots.size() - 1;
    }

    private static void returnCursorToInventoryOrDrop(AbstractContainerMenu menu, Minecraft minecraft) {
        if (menu.getCarried().isEmpty()) return;
        for (int slot = RESULT_SLOT + 1; slot < menu.slots.size() && !menu.getCarried().isEmpty(); slot++) {
            ItemStack target = menu.getSlot(slot).getItem();
            ItemStack carried = menu.getCarried();
            if (!target.isEmpty() && (!ItemStack.isSameItemSameComponents(target, carried)
                    || target.getCount() >= target.getMaxStackSize())) continue;
            click(minecraft, menu, slot, 0, ContainerInput.PICKUP);
        }
        if (!menu.getCarried().isEmpty()) {
            boolean previous = DropBlock.suppressInternal;
            DropBlock.suppressInternal = true;
            try {
                click(minecraft, menu, -999, 0, ContainerInput.THROW);
            } finally {
                DropBlock.suppressInternal = previous;
            }
        }
    }

    private static void throwInternal(StonecutterMenu menu, int slot, Minecraft minecraft) {
        boolean previous = DropBlock.suppressInternal;
        DropBlock.suppressInternal = true;
        try {
            click(minecraft, menu, slot, 1, ContainerInput.THROW);
        } finally {
            DropBlock.suppressInternal = previous;
        }
    }

    private static void click(Minecraft minecraft, AbstractContainerMenu menu, int slot, int button, ContainerInput action) {
        minecraft.gameMode.handleContainerInput(menu.containerId, slot, button, action, minecraft.player);
    }

    /** 关闭隐藏的切石机容器，供自动化开关/模式切换调用 */
    public static void close(Minecraft minecraft) {
        if (minecraft.player == null || !(minecraft.player.containerMenu instanceof StonecutterMenu)) return;
        if (minecraft.getConnection() != null) minecraft.getConnection().getConnection()
                .send(new ServerboundContainerClosePacket(minecraft.player.containerMenu.containerId));
        minecraft.player.containerMenu.removed(minecraft.player);
        minecraft.player.containerMenu = minecraft.player.inventoryMenu;
    }
}
