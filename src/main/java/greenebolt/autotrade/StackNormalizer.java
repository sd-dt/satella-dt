package greenebolt.autotrade;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashSet;
import java.util.Objects;

/**
 * 潜影盒「同身份」归一化（26.2 官方名版本，移植自 1.21.11 主版）。
 *
 * <p>服务器常用「快捷潜影盒」类插件，会给同一批潜影盒塞入各不相同的
 * {@code minecraft:custom_data} 等组件。原版与各库存模组按「物品 + 全部组件」比较，
 * 于是这些潜影盒被当成互不相同的物品，自动补货/整理就抓不到它们。
 * 这里按 {@link ShulkerCompatConfig} 的忽略名单比较，只保留真正影响内容的组件。
 */
public final class StackNormalizer {
    private StackNormalizer() {
    }

    public static boolean isCompatEnabled() {
        return ShulkerCompatConfig.isEnabled();
    }

    public static boolean sameIdentity(ItemStack first, ItemStack second) {
        if (!isCompatEnabled()) {
            return false;
        }
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
            return first != null && second != null && first.isEmpty() && second.isEmpty();
        }
        if (first.getItem() != second.getItem()) {
            return false;
        }

        boolean shulker = isShulkerBox(first) || isShulkerBox(second);
        return normalizedComponentsEqual(first.getComponents(), second.getComponents(), shulker);
    }

    public static boolean isShulkerBox(ItemStack stack) {
        return stack != null && !stack.isEmpty() && isShulkerBoxItem(stack.getItem());
    }

    public static boolean isShulkerBoxItem(Item item) {
        return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    public static boolean normalizedComponentsEqual(DataComponentMap first, DataComponentMap second,
                                                    boolean ignoreConfigured) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }

        HashSet<DataComponentType<?>> types = new HashSet<>();
        types.addAll(first.keySet());
        types.addAll(second.keySet());
        for (DataComponentType<?> type : types) {
            if (ignoreConfigured && isIgnoredComponent(type)) {
                continue;
            }
            if (!Objects.equals(first.get(type), second.get(type))) {
                return false;
            }
        }
        return true;
    }

    public static int normalizedComponentsHash(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return normalizedComponentsHash(stack.getComponents(), isShulkerBox(stack));
    }

    public static int normalizedComponentsHash(DataComponentMap components, boolean ignoreConfigured) {
        if (components == null) {
            return 0;
        }

        int result = 1;
        return components.keySet().stream()
                .filter(type -> !ignoreConfigured || !isIgnoredComponent(type))
                .sorted((first, second) -> componentId(first).toString().compareTo(componentId(second).toString()))
                .reduce(result, (hash, type) -> 31 * (31 * hash + Objects.hashCode(type))
                        + Objects.hashCode(components.get(type)), (left, right) -> 31 * left + right);
    }

    private static boolean isIgnoredComponent(DataComponentType<?> type) {
        Identifier identifier = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return identifier != null && ShulkerCompatConfig.isIgnored(identifier);
    }

    private static Identifier componentId(DataComponentType<?> type) {
        Identifier identifier = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return identifier != null ? identifier : Identifier.fromNamespaceAndPath("satella", "unknown_component");
    }
}
