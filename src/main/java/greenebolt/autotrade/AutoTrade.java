package greenebolt.autotrade;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import net.fabricmc.api.ModInitializer;
import greenebolt.autotrade.gui.AutoTradeConfigGui;
import greenebolt.autotrade.mixin.MinecraftInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AutoTrade implements ModInitializer, IKeybindProvider, IHotkeyCallback {
    public static final String MOD_ID = "satella";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);
    // 用村民的 UUID（跨重进不变）做交易目标标识；实体数字 ID 每次登录会重新分配，不能持久跟踪
    private static final Set<UUID> TRACKED_VILLAGERS = new LinkedHashSet<>();
    private static boolean autoOpening;

    private static int betterCrossbowCounter;
    private static boolean betterCrossbowActive;
    private static boolean physicalUseKeyDown;

    /**
     * 本次按住右键期间是否已经触发过方块交互。
     *
     * <p>周期射击会每 tick 调用 {@code startUseItem}，而只要准星命中方块就会走
     * {@code useItemOn}，导致可交互方块被反复打开。原版只在「使用键刚按下」时走一次方块交互，
     * 因此这里做同样的限制：每次物理按下只放行一次，松开后复位。
     */
    private static boolean blockInteractionDoneThisPress;

    @Override public void onInitialize() {
        AutoTradeConfigs.register();
        ShulkerCompatConfig.load();
        greenebolt.autotrade.stlocator.StLocator.init(
                net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("satella"));
        greenebolt.autotrade.stlocator.StCommands.register();
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(new ModInfo(MOD_ID, "Satella", AutoTradeConfigGui::new));
        AutoTradeConfigs.Trade.TOGGLE_KEY.getKeybind().setCallback(this);
        AutoTradeConfigs.Trade.MODE_KEY.getKeybind().setCallback(this);
        AutoTradeConfigs.Trade.AUTOMATION_KEY.getKeybind().setCallback(this);
        AutoTradeConfigs.Trade.AUTOMATION_MODE_KEY.getKeybind().setCallback(this);
        InputEventHandler.getKeybindManager().registerKeybindProvider(this);
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level != null) ItemNameUtils.warmup();
        tickBetterCrossbow(minecraft);
        AutoCraftController.tick(minecraft);
        AutoStonecutController.tick(minecraft);
        tickId++;
        // 定时刷新交易界面：每 N gt 关闭一次容器（关闭包先于右键到达，服务端清状态后当次右键即可重新打开）
        if (minecraft.player != null && AutoTradeConfigs.isEnabled() && AutoTradeConfigs.isAutoMode()) {
            int refreshInterval = AutoTradeConfigs.Trade.REFRESH_TRADE_GUI.getIntegerValue();
            if (refreshInterval > 0 && ++refreshTradeGuiCounter >= refreshInterval) {
                refreshTradeGuiCounter = 0;
                TradeExecutor.closeTradeGui(minecraft);
            }
        }
        if (++tickCounter < AutoTradeConfigs.Trade.TICK_INTERVAL.getIntegerValue()) return;
        tickCounter = 0;
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null || !AutoTradeConfigs.isEnabled() || !AutoTradeConfigs.isAutoMode()) return;
        TRACKED_VILLAGERS.removeIf(uuid -> {
            Entity entity = findEntity(minecraft, uuid);
            return !(entity instanceof Villager) || entity.isRemoved() || minecraft.player.distanceToSqr(entity) > 64.0;
        });
        if (TRACKED_VILLAGERS.isEmpty()) return;
        Entity entity = findEntity(minecraft, TRACKED_VILLAGERS.iterator().next());
        if (entity instanceof Villager villager) {
            autoOpening = true;
            minecraft.gameMode.interact(minecraft.player, villager, new EntityHitResult(villager), InteractionHand.MAIN_HAND);
            autoOpening = false;
        }
        // 开始交易条件之二：村民交易界面已打开，直接在现有容器上开始一轮交易（无需等新的交易列表包）
        if (minecraft.player.containerMenu instanceof MerchantMenu) {
            TradeExecutor.purchaseCurrent(minecraft);
        }
    }

    /** 自增 tick 计数：用于“每 gt 最多执行一轮成交”去重 */
    public static int tickId;

    /** “刷新交易界面”的计时器 */
    private static int refreshTradeGuiCounter;

    /** 26.2 的 ClientLevel 没有公开的按 UUID 查找，只能遍历已加载实体比对 */
    private static Entity findEntity(Minecraft minecraft, UUID uuid) {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (uuid.equals(entity.getUUID())) {
                return entity;
            }
        }
        return null;
    }

    private static int tickCounter;

    @Override public void addKeysToMap(IKeybindManager manager) {
        manager.addKeybindToMap(AutoTradeConfigs.Trade.TOGGLE_KEY.getKeybind());
        manager.addKeybindToMap(AutoTradeConfigs.Trade.MODE_KEY.getKeybind());
        manager.addKeybindToMap(AutoTradeConfigs.Trade.AUTOMATION_KEY.getKeybind());
        manager.addKeybindToMap(AutoTradeConfigs.Trade.AUTOMATION_MODE_KEY.getKeybind());
    }

    @Override public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(MOD_ID, "自动交易", List.of(AutoTradeConfigs.Trade.TOGGLE_KEY,
                AutoTradeConfigs.Trade.MODE_KEY, AutoTradeConfigs.Trade.AUTOMATION_KEY,
                AutoTradeConfigs.Trade.AUTOMATION_MODE_KEY));
    }

    @Override public boolean onKeyAction(KeyAction action, IKeybind key) {
        if (key == AutoTradeConfigs.Trade.TOGGLE_KEY.getKeybind()) toggle();
        else if (key == AutoTradeConfigs.Trade.MODE_KEY.getKeybind()) cycleMode();
        else if (key == AutoTradeConfigs.Trade.AUTOMATION_KEY.getKeybind()) toggleAutomation();
        else if (key == AutoTradeConfigs.Trade.AUTOMATION_MODE_KEY.getKeybind()) cycleAutomationMode();
        return true;
    }

    /**
     * 「更NB的弩」：手持弩长按右键时保持使用键按下，并按周期重复执行右键（{@code startUseItem}）。
     * 移植自 1.21.11 主版的 {@code tickBetterCrossbow}，API 换成 26.2 官方名。
     */
    private static void tickBetterCrossbow(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null
                || !AutoTradeConfigs.Trade.BETTER_CROSSBOW.getBooleanValue()
                || !physicalUseKeyDown || !isHoldingCrossbow(minecraft)) {
            resetBetterCrossbow(minecraft);
            return;
        }

        // 背包/其他容器界面打开、或 litematica-printer 快捷潜影盒-自动补货进行中时，
        // 立即暂停连射：松开模拟的使用键并清零计数，恢复后从激活步骤重新开始
        // （先按住使用键，再进入周期射击），避免补货期间射击导致补货失败
        if (minecraft.gui.screen() != null || PrinterRestockPause.isRestockInProgress()) {
            if (betterCrossbowActive) {
                setUseKey(minecraft, false);
            }
            betterCrossbowActive = false;
            betterCrossbowCounter = 0;
            return;
        }

        if (!betterCrossbowActive) {
            betterCrossbowActive = true;
            betterCrossbowCounter = 0;
            setUseKey(minecraft, true);
            return;
        }

        // Keep the vanilla use key pressed first, then perform the periodic click.
        setUseKey(minecraft, true);

        // 蓄力中不要重复触发物品使用。
        //
        // 原因：弩的蓄力需要计时器连续递减到蓄满。而每次 CrossbowItem.use() 都会把使用状态
        // 重置回满值；贴墙时客户端每 tick 都会走方块分支，周期射击的额外调用会让计时器
        // 反复归零、永远蓄不满。
        var stack = minecraft.player.getMainHandItem();
        boolean charged = CrossbowItem.isCharged(stack);
        if (minecraft.player.isUsingItem() && !charged) {
            return;
        }

        // 已蓄满：立即发射，不受周期计数限制（原版「按住右键不放」也是蓄满即发射）。
        if (charged) {
            betterCrossbowCounter = 0;
            ((MinecraftInvoker) (Object) minecraft).autoTrade$startUseItem();
            return;
        }

        // 同一次按住期间，方块交互只放行一次（避免可交互方块被反复打开）。
        boolean aimingBlock = isAimingAtBlock(minecraft);
        if (aimingBlock && blockInteractionDoneThisPress) {
            return;
        }

        if (++betterCrossbowCounter >= AutoTradeConfigs.Trade.BETTER_CROSSBOW_INTERVAL.getIntegerValue()) {
            betterCrossbowCounter = 0;
            if (aimingBlock) {
                blockInteractionDoneThisPress = true;
            }
            ((MinecraftInvoker) (Object) minecraft).autoTrade$startUseItem();
        }
    }

    /** 准星当前是否命中方块。 */
    private static boolean isAimingAtBlock(Minecraft minecraft) {
        return minecraft.hitResult != null
                && minecraft.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    private static boolean isHoldingCrossbow(Minecraft minecraft) {
        return isCrossbow(minecraft.player.getMainHandItem()) || isCrossbow(minecraft.player.getOffhandItem());
    }

    private static boolean isCrossbow(ItemStack stack) {
        return stack.getItem() instanceof CrossbowItem;
    }

    private static void resetBetterCrossbow(Minecraft minecraft) {
        if (betterCrossbowActive) {
            setUseKey(minecraft, false);
        }
        betterCrossbowActive = false;
        betterCrossbowCounter = 0;
    }

    private static void setUseKey(Minecraft minecraft, boolean pressed) {
        net.minecraft.client.KeyMapping.set(minecraft.options.keyUse.getDefaultKey(), pressed);
    }

    /** 由 {@code MouseMixin} 在物理右键按下/松开时调用。 */
    public static void updatePhysicalUseKeyState(boolean pressed) {
        physicalUseKeyDown = pressed;
        if (!pressed) {
            // 松开右键后复位，让下一次按下的方块交互重新放行
            blockInteractionDoneThisPress = false;
        }
    }

    /**
     * 临时诊断：记录「工具优先」判定链的每一步。
     *
     * <p>写入游戏目录下的 {@code satella-diag.txt}（存在该文件时才写）。
     * 用于定位「陆地手持激流三叉戟未接管」的问题，排查结束后删除。
     */
    public static void diagToolFirst(net.minecraft.world.entity.player.Player player,
                                     net.minecraft.world.InteractionHand hand, boolean selfInvoke) {
        try {
            java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
            java.nio.file.Path log = dir.resolve("satella-diag.txt");
            if (!java.nio.file.Files.exists(log)) {
                return;
            }
            var stack = player == null ? ItemStack.EMPTY : player.getItemInHand(hand);
            StringBuilder sb = new StringBuilder();
            sb.append(java.time.LocalTime.now().withNano(0))
                    .append("  hand=").append(hand)
                    .append(" item=").append(stack.getItem())
                    .append(" 模式=").append(ToolInteractionHandler.mode().getStringValue())
                    .append(" 自己发包=").append(selfInvoke);
            if (player != null) {
                sb.append(" 激流强度=").append(riptideStrength(player, stack))
                        .append(" 水中/雨中=").append(player.isInWaterOrRain())
                        .append(" 使用中=").append(player.isUsingItem())
                        .append(" 剩余=").append(player.getUseItemRemainingTicks());
            }
            sb.append(System.lineSeparator());
            java.nio.file.Files.writeString(log, sb.toString(), java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }

    /** 诊断用：读取三叉戟的激流强度（与原版 use() 的判定同源）。 */
    private static float riptideStrength(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        try {
            return net.minecraft.world.item.enchantment.EnchantmentHelper.getTridentSpinAttackStrength(stack, player);
        } catch (Throwable t) {
            return -1.0F;
        }
    }

    private static void toggle() {
        boolean enabled = !AutoTradeConfigs.isEnabled();
        AutoTradeConfigs.Trade.ENABLED.setBooleanValue(enabled);
        ConfigManager.getInstance().onConfigsChanged(MOD_ID);
        if (!enabled) {
            TRACKED_VILLAGERS.clear();
            TradeExecutor.closeTradeGui(Minecraft.getInstance());
        }
        InfoUtils.sendVanillaMessage(Component.literal(enabled ? "自动交易已开启" : "自动交易已关闭").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static void cycleMode() {
        var mode = AutoTradeConfigs.Trade.MODE.getOptionListValue().cycle(true);
        AutoTradeConfigs.Trade.MODE.setOptionListValue(mode);
        ConfigManager.getInstance().onConfigsChanged(MOD_ID);
        InfoUtils.sendVanillaMessage(Component.literal("交易模式已切换: " + mode.getDisplayName()).withStyle(ChatFormatting.YELLOW));
    }

    private static void toggleAutomation() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        boolean enabled = !AutoTradeConfigs.Trade.AUTOMATION.getBooleanValue();
        AutoTradeConfigs.Trade.AUTOMATION.setBooleanValue(enabled);
        if (!enabled) {
            AutoCraftController.close(minecraft);
            AutoStonecutController.close(minecraft);
        }
        InfoUtils.sendVanillaMessage(Component.literal(enabled ? "自动化已开启" : "自动化已关闭")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static void cycleAutomationMode() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        var newMode = AutoTradeConfigs.Trade.AUTOMATION_MODE.getOptionListValue().cycle(true);
        AutoTradeConfigs.Trade.AUTOMATION_MODE.setOptionListValue(newMode);
        AutoCraftController.close(minecraft);
        AutoStonecutController.close(minecraft);
        InfoUtils.sendVanillaMessage(Component.literal("自动化模式: " + newMode.getDisplayName())
                .withStyle(ChatFormatting.GOLD));
    }

    public static void onInteractEntity(Entity entity) {
        if (autoOpening || !(entity instanceof Villager)) return;
        if (AutoTradeConfigs.isEnabled() && AutoTradeConfigs.isAutoMode()) {
            TRACKED_VILLAGERS.clear();
            TRACKED_VILLAGERS.add(entity.getUUID());
            InfoUtils.sendVanillaMessage(Component.literal("已标记为目标村民").withStyle(ChatFormatting.GREEN));
        }
    }

    public static boolean isHighlighted(Entity entity) {
        return TRACKED_VILLAGERS.contains(entity.getUUID());
    }
}
