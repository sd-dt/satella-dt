package greenebolt.autotrade;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 服务器快捷潜影盒兼容配置（26.2 官方名版本，移植自 1.21.11 主版）。
 *
 * <p>读取 {@code config/satella/ignored-components.txt}：每行一个「比较潜影盒时忽略的物品组件 ID」，
 * 首行 {@code enabled=false} 可整体关闭。{@link StackNormalizer} 用这份名单让
 * Item Scroller / Tweakeroo / Inventory Profiles Next 把「只差这些组件」的潜影盒视为同一物品。
 */
public final class ShulkerCompatConfig {
    private static final String FILE_NAME = "ignored-components.txt";
    private static final Identifier DEFAULT_IGNORED_COMPONENT = Identifier.withDefaultNamespace("custom_data");
    private static volatile boolean enabled = false;
    private static volatile Set<Identifier> ignoredComponents = Set.of(DEFAULT_IGNORED_COMPONENT);

    private ShulkerCompatConfig() {
    }

    public static void load() {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve(AutoTrade.MOD_ID);
        Path file = directory.resolve(FILE_NAME);

        try {
            Files.createDirectories(directory);
            if (Files.notExists(file)) {
                Files.writeString(file, defaultTemplate(), StandardCharsets.UTF_8);
                enabled = true;
                ignoredComponents = Set.of(DEFAULT_IGNORED_COMPONENT);
                return;
            }

            boolean configEnabled = true;
            Set<Identifier> components = new LinkedHashSet<>();
            components.add(DEFAULT_IGNORED_COMPONENT);

            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                if (value.equalsIgnoreCase("enabled=false")) {
                    configEnabled = false;
                    continue;
                }
                Identifier identifier = Identifier.tryParse(value);
                if (identifier != null) {
                    components.add(identifier);
                }
            }

            enabled = configEnabled;
            ignoredComponents = Set.copyOf(components);
        } catch (IOException exception) {
            AutoTrade.LOGGER.warn("无法加载服务器快捷潜影盒兼容配置，将使用默认值", exception);
            enabled = true;
            ignoredComponents = Set.of(DEFAULT_IGNORED_COMPONENT);
        }
    }

    public static boolean isEnabled() {
        return enabled && AutoTradeConfigs.Trade.SERVER_SHULKER_COMPAT.getBooleanValue();
    }

    public static boolean isIgnored(Identifier identifier) {
        return ignoredComponents.contains(identifier);
    }

    private static String defaultTemplate() {
        return "# satella 服务器快捷潜影盒兼容配置\n"
                + "# 每行填写一个需要忽略的物品组件 ID\n"
                + "minecraft:custom_data\n"
                + "#\n"
                + "# enabled=false\n";
    }
}
