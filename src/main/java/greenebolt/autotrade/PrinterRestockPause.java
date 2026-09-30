package greenebolt.autotrade;

import java.lang.reflect.Field;

/**
 * 检测 litematica-printer（litematica-printer-EMT）的「快捷潜影盒-自动补货」是否正在进行。
 * （26.2 官方名版本，移植自 1.21.11 主版）
 *
 * <p>补货触发链：HandRestockShulkerCompat 判定缺货后 addQuickShulkerDemand(item) + switchItem()，
 * 取货流程期间以下任一信号成立即视为进行中：
 * <ul>
 *   <li>{@code InventoryUtils.lastNeedItemList} 非空（补货需求挂起，即触发点）</li>
 *   <li>{@code InventoryUtils.isOpenHandler == true}（潜影盒打开取货中）</li>
 *   <li>{@code SwitchItem.reSwitchItem != null}（取货完成后待放回手部槽位）</li>
 * </ul>
 *
 * <p>打印机 mod 不在编译期依赖内，全部走反射；各字段独立解析，26.2 的打印机 fork 里没有
 * {@code SwitchItem} 类时只跳过那一条信号，其余判定照常工作。mod 未安装时恒返回 false。
 */
public final class PrinterRestockPause {
    private static final String INVENTORY_UTILS_CLASS =
            "me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils";
    private static final String SWITCH_ITEM_CLASS =
            "me.aleksilassila.litematica.printer.printer.zxy.inventory.SwitchItem";

    private static boolean initialized;
    private static Field lastNeedItemListField;
    private static Field isOpenHandlerField;
    private static Field reSwitchItemField;

    private PrinterRestockPause() {}

    public static boolean isRestockInProgress() {
        if (!initialized) {
            initialized = true;
            lastNeedItemListField = findField(INVENTORY_UTILS_CLASS, "lastNeedItemList");
            isOpenHandlerField = findField(INVENTORY_UTILS_CLASS, "isOpenHandler");
            reSwitchItemField = findField(SWITCH_ITEM_CLASS, "reSwitchItem");
        }

        try {
            if (lastNeedItemListField != null) {
                Object value = lastNeedItemListField.get(null);
                if (value instanceof java.util.Collection<?> collection && !collection.isEmpty()) {
                    return true;
                }
            }
            if (isOpenHandlerField != null && isOpenHandlerField.getBoolean(null)) {
                return true;
            }
            return reSwitchItemField != null && reSwitchItemField.get(null) != null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static Field findField(String className, String fieldName) {
        try {
            return Class.forName(className).getField(fieldName);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
