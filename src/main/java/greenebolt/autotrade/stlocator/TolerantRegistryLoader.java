package greenebolt.autotrade.stlocator;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 逐元素容错的动态注册表加载：解析失败的数据包元素只记入错误表并跳过，
 * 不再导致整个注册表/整个加载失败。这允许带第三方自定义注册表
 * （如 lithostitched:fast_noise_config）的数据包在纯客户端离线栈中尽可能完整地加载。
 */
final class TolerantRegistryLoader {

    private TolerantRegistryLoader() {}

    static List<Registry<?>> load(
        List<HolderLookup.RegistryLookup<?>> baseLookups,
        List<RegistryDataLoader.RegistryData<?>> entries,
        ResourceManager resourceManager,
        Map<ResourceKey<?>, Exception> errors
    ) {
        List<MappedRegistry<?>> registries = new ArrayList<>(entries.size());
        Map<ResourceKey<? extends Registry<?>>, RegistryOps.RegistryInfo<?>> infos = new HashMap<>();
        for (HolderLookup.RegistryLookup<?> lookup : baseLookups) {
            infos.put(lookup.key(), RegistryOps.RegistryInfo.fromRegistryLookup(lookup));
        }
        // 与原版一致：先注册所有 in-flight 注册表再解析，保证注册表之间的交叉引用可解析
        for (RegistryDataLoader.RegistryData<?> entry : entries) {
            MappedRegistry<?> registry = new MappedRegistry<>(entry.key(), Lifecycle.stable());
            registries.add(registry);
            infos.put(entry.key(), infoOf(registry));
        }
        RegistryOps.RegistryInfoLookup lookup = new RegistryOps.RegistryInfoLookup() {
            @Override
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef) {
                return Optional.ofNullable((RegistryOps.RegistryInfo<T>) infos.get(registryRef));
            }
        };

        for (int i = 0; i < entries.size(); i++) {
            loadEntry(resourceManager, lookup, entries.get(i), registries.get(i), errors);
        }
        // freeze 失败（如存在无法解析的悬空引用）时跳过该注册表，避免影响整体加载
        List<Registry<?>> frozen = new ArrayList<>(registries.size());
        for (MappedRegistry<?> registry : registries) {
            try {
                registry.freeze();
                frozen.add(registry);
            } catch (Exception e) {
                errors.put(registry.key(), e);
            }
        }
        return frozen;
    }

    private static <T> RegistryOps.RegistryInfo<T> infoOf(WritableRegistry<T> registry) {
        // 与原版一致使用 createRegistrationLookup：允许引用尚未解析的元素（惰性占位 Holder）
        return new RegistryOps.RegistryInfo<>(registry, registry.createRegistrationLookup(), Lifecycle.stable());
    }

    private static void loadEntry(
        ResourceManager resourceManager,
        RegistryOps.RegistryInfoLookup lookup,
        RegistryDataLoader.RegistryData<?> data,
        WritableRegistry<?> registry,
        Map<ResourceKey<?>, Exception> errors
    ) {
        loadEntryRaw(resourceManager, lookup, (RegistryDataLoader.RegistryData<Object>) data, (WritableRegistry<Object>) registry, errors);
    }

    private static <T> void loadEntryRaw(
        ResourceManager resourceManager,
        RegistryOps.RegistryInfoLookup lookup,
        RegistryDataLoader.RegistryData<T> data,
        WritableRegistry<T> registry,
        Map<ResourceKey<?>, Exception> errors
    ) {
        FileToIdConverter finder = FileToIdConverter.registry(data.key());
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
        RegistrationInfo registrationInfo = new RegistrationInfo(Optional.empty(), Lifecycle.stable());
        for (Map.Entry<Identifier, Resource> e : finder.listMatchingResources(resourceManager).entrySet()) {
            Identifier path = e.getKey();
            Resource resource = e.getValue();
            ResourceKey<T> key = ResourceKey.create(data.key(), finder.fileToId(path));
            try (java.io.BufferedReader reader = resource.openAsReader()) {
                JsonElement json = net.minecraft.util.StrictJsonParser.parse(reader);
                T value = data.elementCodec().parse(ops, json).getOrThrow();
                registry.register(key, value, registrationInfo);
            } catch (Exception ex) {
                errors.put(key, new IllegalStateException("Failed to parse " + path, ex));
            }
        }
        TagLoader.loadTagsForRegistry(resourceManager, registry);
    }
}
