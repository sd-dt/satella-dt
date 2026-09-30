package greenebolt.autotrade;

import fi.dy.masa.malilib.util.InfoUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;

import java.util.ArrayList;
import java.util.List;

public final class TradeExecutor {
    private TradeExecutor() {}

    // 每 gt 最多执行一轮成交的去重标记
    private static int lastPurchaseTickId = -1;
    // 最近一次交易列表快照：界面已打开时复用它继续成交
    private static List<Integer> lastIndices = List.of();
    private static List<Integer> lastUses = List.of();
    // 最近一次交易包的原始交易列表：每交易间隔据此重新检查背包
    private static List<MerchantOffer> cachedOffers = List.of();

    public static void handleOffers(ClientboundMerchantOffersPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        cachedOffers = List.copyOf(packet.getOffers());
        refreshBuyLists(minecraft.player.getInventory(), cachedOffers);
        if (lastIndices.isEmpty()) {
            InfoUtils.sendVanillaMessage(Component.literal("没有可购买的匹配交易或输入物品不足").withStyle(ChatFormatting.YELLOW));
            closeUnlessAutoTrade(minecraft, packet.getContainerId());
            return;
        }
        minecraft.execute(() -> buy(minecraft, packet.getContainerId(), new ArrayList<>(lastIndices), new ArrayList<>(lastUses)));
    }

    /** 按当前背包重建可买列表（写入 lastIndices/lastUses）；返回是否有可买的交易 */
    private static boolean refreshBuyLists(Inventory inventory, List<MerchantOffer> offers) {
        TradeEntry target = TradeEntry.build(AutoTradeConfigs.Trade.INPUT_ITEM_1.getStringValue(),
                AutoTradeConfigs.Trade.INPUT_ITEM_2.getStringValue(), AutoTradeConfigs.Trade.OUTPUT_ITEM.getStringValue());
        List<Integer> indices = new ArrayList<>();
        List<Integer> available = new ArrayList<>();
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer offer = offers.get(index);
            if (target == null || !target.matches(offer) || offer.isOutOfStock()) continue;
            int uses = Math.min(remainingUses(inventory, offer.getItemCostA()),
                    offer.getItemCostB().map(cost -> remainingUses(inventory, cost)).orElse(Integer.MAX_VALUE));
            uses = Math.min(uses, offer.getMaxUses() - offer.getUses());
            if (uses > 0) {
                indices.add(index);
                available.add(uses);
            }
        }
        lastIndices = List.copyOf(indices);
        lastUses = List.copyOf(available);
        return !lastIndices.isEmpty();
    }

    /** 自动交易模式下村民界面保持打开（按周期重新打开，关闭功能时才关）；单次交易照旧买完即关 */
    private static void closeUnlessAutoTrade(Minecraft minecraft, int containerId) {
        if (AutoTradeConfigs.isAutoMode()) return;
        close(minecraft, containerId);
    }

    /** 开始交易条件之二：村民交易界面已打开时，每交易间隔重新检查背包并在现有容器上开始一轮交易 */
    public static void purchaseCurrent(Minecraft minecraft) {
        if (!AutoTradeConfigs.isEnabled() || minecraft.player == null) return;
        if (!(minecraft.player.containerMenu instanceof MerchantMenu menu)) return;
        if (cachedOffers.isEmpty()) return;
        // 按交易间隔重新检查背包：用当前背包重算可买列表
        refreshBuyLists(minecraft.player.getInventory(), cachedOffers);
        if (lastIndices.isEmpty()) return;
        buy(minecraft, menu.containerId, new ArrayList<>(lastIndices), new ArrayList<>(lastUses));
    }

    /** 关闭自动交易时调用：关闭村民交易界面（真实界面或隐藏占位容器） */
    public static void closeTradeGui(Minecraft minecraft) {
        if (minecraft.player == null || !(minecraft.player.containerMenu instanceof MerchantMenu)) return;
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) connection.getConnection().send(new ServerboundContainerClosePacket(minecraft.player.containerMenu.containerId));
        boolean screenOpen = minecraft.gui.screen() instanceof MerchantScreen;
        minecraft.player.containerMenu.removed(minecraft.player);
        minecraft.player.containerMenu = minecraft.player.inventoryMenu;
        cachedOffers = List.of();
        if (screenOpen) {
            minecraft.gui.setScreen(null);
        }
    }

    private static void buy(Minecraft minecraft, int containerId, List<Integer> indices, List<Integer> available) {
        if (!AutoTradeConfigs.isEnabled() || minecraft.player == null || minecraft.gameMode == null
                || !(minecraft.player.containerMenu instanceof MerchantMenu menu) || menu.containerId != containerId) return;
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) return;
        // 每 gt 最多执行一轮成交：交易列表包触发与“界面已打开”触发可能同 gt到达，去重
        if (AutoTrade.tickId == lastPurchaseTickId) return;
        lastPurchaseTickId = AutoTrade.tickId;
        // 光标有残余时点输出槽无效，会卡死成交；先清理光标再开始
        clearCursor(minecraft, containerId);
        int limit = AutoTradeConfigs.Trade.TRADES_PER_SESSION.getIntegerValue();
        int completed = 0;
        for (int i = 0; i < indices.size() && completed < limit; i++) {
            connection.getConnection().send(new ServerboundSelectTradePacket(indices.get(i)));
            for (int count = 0; count < available.get(i) && completed < limit; count++) {
                minecraft.gameMode.handleContainerInput(containerId, 2, 0, ContainerInput.PICKUP, minecraft.player);
                if (AutoTradeConfigs.Trade.DROP_OUTPUTS.getBooleanValue()) {
                    minecraft.gameMode.handleContainerInput(containerId, -999, 0, ContainerInput.PICKUP, minecraft.player);
                } else {
                    minecraft.gameMode.handleContainerInput(containerId, findDestination(menu.getCarried(), minecraft.player.getInventory()), 0,
                            ContainerInput.PICKUP, minecraft.player);
                }
                completed++;
            }
        }
        closeUnlessAutoTrade(minecraft, containerId);
    }

    // 清理光标残余：优先合并进背包同类堆，其次放空位，都放不下就整堆丢出
    // 只考虑 36 个可映射到容器槽位的格子（盔甲/副手不可放入容器槽，映射会越界）
    private static void clearCursor(Minecraft minecraft, int containerId) {
        if (minecraft.player.containerMenu.getCarried().isEmpty()) return;
        List<ItemStack> stacks = minecraft.player.getInventory().getNonEquipmentItems();
        int storageSlots = Math.min(stacks.size(), 36);
        for (int i = 0; i < storageSlots; i++) {
            ItemStack stack = stacks.get(i);
            ItemStack carried = minecraft.player.containerMenu.getCarried();
            if (carried.isEmpty()) return;
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, carried)
                    && stack.getCount() < stack.getMaxStackSize()) {
                minecraft.gameMode.handleContainerInput(containerId, i < 27 ? i + 3 : i + 3, 0, ContainerInput.PICKUP, minecraft.player);
            }
        }
        for (int i = 0; i < storageSlots; i++) {
            if (minecraft.player.containerMenu.getCarried().isEmpty()) return;
            if (stacks.get(i).isEmpty()) {
                minecraft.gameMode.handleContainerInput(containerId, i < 27 ? i + 3 : i + 3, 0, ContainerInput.PICKUP, minecraft.player);
            }
        }
        if (!minecraft.player.containerMenu.getCarried().isEmpty()) {
            // 背包放不下：点击界面外整堆丢出
            minecraft.gameMode.handleContainerInput(containerId, -999, 0, ContainerInput.PICKUP, minecraft.player);
        }
    }

    private static int remainingUses(Inventory inventory, ItemCost cost) {
        int count = 0;
        Item target = cost.item().value();
        for (ItemStack stack : inventory.getNonEquipmentItems()) if (stack.getItem() == target) count += stack.getCount();
        return count / Math.max(1, cost.count());
    }

    private static int findDestination(ItemStack carried, Inventory inventory) {
        List<ItemStack> stacks = inventory.getNonEquipmentItems();
        int storageSlots = Math.min(stacks.size(), 36);
        for (int i = 0; i < storageSlots; i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, carried) && stack.getCount() < stack.getMaxStackSize()) return i + 3;
        }
        for (int i = 0; i < storageSlots; i++) if (stacks.get(i).isEmpty()) return i + 3;
        return 3;
    }

    public static void close(Minecraft minecraft, int containerId) {
        if (minecraft.player == null || !(minecraft.player.containerMenu instanceof MerchantMenu) || minecraft.player.containerMenu.containerId != containerId) return;
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) connection.getConnection().send(new ServerboundContainerClosePacket(containerId));
        minecraft.player.containerMenu.removed(minecraft.player);
        minecraft.player.containerMenu = minecraft.player.inventoryMenu;
        lastIndices = List.of();
        lastUses = List.of();
        cachedOffers = List.of();
    }
}
