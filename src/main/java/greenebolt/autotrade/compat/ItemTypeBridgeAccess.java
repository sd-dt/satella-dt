package greenebolt.autotrade.compat;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;

/**
 * 由 Inventory Profiles Next 的 ItemType 通过 Mixin 实现，
 * 用于在不直接依赖 IPN 类的情况下读取物品与组件。（26.2 官方名版本）
 */
public interface ItemTypeBridgeAccess {
    Item autoTrade$item();

    DataComponentMap autoTrade$components();
}
