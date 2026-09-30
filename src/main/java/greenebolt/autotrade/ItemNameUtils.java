package greenebolt.autotrade;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ItemNameUtils {
    private static final Map<String, Item> NAMES = new HashMap<>();
    private static Language language;

    private ItemNameUtils() {}

    public static Item parseItem(String value) {
        String text = value.trim();
        if (text.isEmpty()) return null;
        Identifier id = Identifier.tryParse((text.contains(":") ? text : "minecraft:" + text).toLowerCase(Locale.ROOT));
        if (id != null) {
            Item item = BuiltInRegistries.ITEM.get(id).map(reference -> reference.value()).orElse(null);
            if (item != null) return item;
        }
        refreshNames();
        return NAMES.get(text.toLowerCase(Locale.ROOT));
    }

    public static void warmup() { refreshNames(); }

    private static void refreshNames() {
        Language current = Language.getInstance();
        if (language == current) return;
        language = current;
        NAMES.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            String name = current.getOrDefault(item.getDescriptionId());
            if (name != null && !name.isEmpty()) NAMES.put(name.toLowerCase(Locale.ROOT), item);
        }
    }
}
