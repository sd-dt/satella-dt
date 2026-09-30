package greenebolt.autotrade.stlocator;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.DirectoryValidator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 纯客户端世界生成栈：加载原版数据包与 config/satella/datapacks 下的数据包 zip，
 * 解析全部动态注册表，并实例化 overworld 噪声生成器，使任意种子下的群系/结构查询
 * 可以完全离线执行。会话目录位于 config/satella 内，EMT 之外不留文件。
 */
public final class DatapackWorldgen implements AutoCloseable {
    public final long seed;
    public final RegistryAccess.Frozen registryManager;
    public final NoiseBasedChunkGenerator noiseGenerator;
    public final RandomState randomState;
    public final BiomeSource biomeSource;
    public final LevelHeightAccessor heightView;
    public final ChunkGeneratorStructureState structureState;
    public final StructureTemplateManager templateManager;
    public final List<String> loadedPacks;
    /** 容错加载时被跳过的元素（第三方数据包引用模组自定义注册表等） */
    public final Map<ResourceKey<?>, Exception> registryErrors;
    private final CloseableResourceManager resources;

    private DatapackWorldgen(
        long seed,
        RegistryAccess.Frozen registryManager,
        NoiseBasedChunkGenerator noiseGenerator,
        RandomState randomState,
        BiomeSource biomeSource,
        LevelHeightAccessor heightView,
        ChunkGeneratorStructureState structureState,
        StructureTemplateManager templateManager,
        CloseableResourceManager resources,
        List<String> loadedPacks,
        Map<ResourceKey<?>, Exception> registryErrors
    ) {
        this.seed = seed;
        this.registryManager = registryManager;
        this.noiseGenerator = noiseGenerator;
        this.randomState = randomState;
        this.biomeSource = biomeSource;
        this.heightView = heightView;
        this.structureState = structureState;
        this.templateManager = templateManager;
        this.resources = resources;
        this.loadedPacks = loadedPacks;
        this.registryErrors = registryErrors;
    }

    @SuppressWarnings("unchecked")
    public static DatapackWorldgen load(Path packsDir, DirectoryValidator validator,
                                        LevelStorageSource.LevelStorageAccess session,
                                        ResourceManager clientResources, DataFixer dataFixer, long seed,
                                        List<String> rulePacks) throws Exception {
        CloseableResourceManager resources = null;
        try {
            // 原版 + config/satella/datapacks + （fabric resource-loader 注入的）模组内置数据包
            PackRepository repo = ServerPacksSource.createPackRepository(packsDir, validator);
            WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(repo, WorldDataConfiguration.DEFAULT, false, false);
            Pair<WorldDataConfiguration, CloseableResourceManager> pair = packConfig.createResourceManager();
            resources = pair.getSecond();

            // STATIC 层作为基础查找（方块等原版内置注册表）；Registry 本身就是 RegistryLookup
            List<Registry<?>> staticRegistries = new ArrayList<>();
            RegistryLayer.createRegistryAccess().getLayer(RegistryLayer.STATIC)
                .registries().forEach(entry -> staticRegistries.add(entry.value()));
            List<HolderLookup.RegistryLookup<?>> base = new ArrayList<>(staticRegistries);

            // 容错加载：第三方数据包引用模组自定义注册表（如 lithostitched:fast_noise_config）时
            // 只跳过对应元素，不让整个注册表加载失败
            // fabric DynamicRegistries（Datapack Registries）维护的动态注册表列表 =
            // 原版 WORLDGEN_REGISTRIES + 模组自定义注册表（如 lithostitched:fast_noise_config），
            // 必须用它而不是原版列表拼接，否则所有注册表都会重复
            java.util.LinkedHashMap<ResourceKey<?>, RegistryDataLoader.RegistryData<?>> entriesByKey = new java.util.LinkedHashMap<>();
            for (RegistryDataLoader.RegistryData<?> entry : net.fabricmc.fabric.api.event.registry.DynamicRegistries.getWorldRegistries()) {
                entriesByKey.putIfAbsent(entry.key(), entry);
            }
            List<RegistryDataLoader.RegistryData<?>> worldgenEntries = List.copyOf(entriesByKey.values());

            Map<ResourceKey<?>, Exception> registryErrors = new HashMap<>();
            List<Registry<?>> worldgen = TolerantRegistryLoader.load(
                base, worldgenEntries, resources, registryErrors);
            for (Map.Entry<ResourceKey<?>, Exception> err : registryErrors.entrySet()) {
                StLocator.LOGGER.warn("跳过注册表元素 {}: {}", err.getKey().identifier(), (err.getValue().getCause() != null ? err.getValue().getCause().toString() : err.getValue().toString()));
            }
            List<HolderLookup.RegistryLookup<?>> all = new ArrayList<>(base);
            for (Registry<?> registry : worldgen) {
                all.add((HolderLookup.RegistryLookup<?>) registry);
            }
            List<Registry<?>> dimensionList = TolerantRegistryLoader.load(
                all, RegistryDataLoader.DIMENSION_REGISTRIES, resources, registryErrors);

            // 合并为一个 Frozen RegistryAccess 供查询与模板管理器使用
            // 静态层也要并入：BLOCK 等内置注册表只在 staticRegistries 里
            List<Registry<?>> combined = new ArrayList<>(staticRegistries);
            combined.addAll(worldgen);
            combined.addAll(dimensionList);
            RegistryAccess.Frozen dimsAccess = frozenAccess(combined);

            // 原版维度不在 level_stem 数据目录中，而是定义在 world preset（#minecraft:normal）里；
            // level_stem 注册表通常只有第三方数据包自带的维度（如 nullscape 的 end），不能直接选
            LevelStem stem = null;
            NoiseBasedChunkGenerator generator = null;
            Registry<net.minecraft.world.level.levelgen.presets.WorldPreset> presets =
                dimsAccess.lookupOrThrow(Registries.WORLD_PRESET);
            net.minecraft.world.level.levelgen.presets.WorldPreset normal = presets
                .get(ResourceKey.create(Registries.WORLD_PRESET, Identifier.withDefaultNamespace("normal")))
                .map(Holder::value).orElse(null);
            LevelStem overworld = normal != null ? normal.overworld().orElse(null) : null;
            if (overworld == null) {
                for (Holder<net.minecraft.world.level.levelgen.presets.WorldPreset> entry : presets.getTagOrEmpty(net.minecraft.tags.WorldPresetTags.NORMAL)) {
                    overworld = entry.value().overworld().orElse(null);
                    if (overworld != null) break;
                }
            }
            if (overworld != null && overworld.generator() instanceof NoiseBasedChunkGenerator noiseGen) {
                stem = overworld;
                generator = noiseGen;
            }
            if (generator == null) {
                Registry<LevelStem> stems = dimsAccess.lookupOrThrow(Registries.LEVEL_STEM);
                for (LevelStem candidate : stems) {
                    boolean overworldKey = LevelStem.OVERWORLD.equals(stems.getResourceKey(candidate).orElse(null));
                    if ((overworldKey || generator == null) && candidate.generator() instanceof NoiseBasedChunkGenerator noiseGen) {
                        stem = candidate;
                        generator = noiseGen;
                    }
                    if (overworldKey) {
                        break;
                    }
                }
            }
            if (generator == null) {
                throw new IllegalStateException("数据包中没有基于噪声的维度");
            }

            NoiseGeneratorSettings settings = generator.generatorSettings().value();
            RandomState randomState = RandomState.create(settings, dimsAccess.lookupOrThrow(Registries.NOISE), seed);
            LevelHeightAccessor heightView = LevelHeightAccessor.create(settings.noiseSettings().minY(), settings.noiseSettings().height());
            ChunkGeneratorStructureState structureState = ChunkGeneratorStructureState.createForNormal(
                randomState, seed, generator.getBiomeSource(), dimsAccess.lookupOrThrow(Registries.STRUCTURE_SET));
            StructureTemplateManager templateManager = new StructureTemplateManager(
                clientResources, session, dataFixer, dimsAccess.lookupOrThrow(Registries.BLOCK));

            // createResourceManager 内部已完成 scanPacks 并自动启用数据包目录中的 zip
            List<String> loadedPacks = new ArrayList<>(repo.getSelectedIds());
            StLocator.LOGGER.info("世界生成栈已构建: seed={}, 数据包={}", seed, loadedPacks.size());
            StLocator.LOGGER.info("启用的数据包: {}", String.join(", ", loadedPacks));

            return new DatapackWorldgen(seed, dimsAccess, generator, randomState, generator.getBiomeSource(),
                heightView, structureState, templateManager, resources, loadedPacks, registryErrors);
        } catch (Exception e) {
            // 失败时释放已打开的资源包（zip 句柄）；模板会话由外部持久持有，不受影响
            if (resources != null) {
                resources.close();
            }
            throw e;
        }
    }

    /** file/<name>.zip 形式的包 id 是否命中规则里配置的数据包名 */
    /** 用加载完成的注册表构造一个 Frozen RegistryAccess。 */
    @SuppressWarnings("unchecked")
    private static RegistryAccess.Frozen frozenAccess(List<Registry<?>> registries) {
        Map<ResourceKey<? extends Registry<?>>, Registry<?>> byKey = new HashMap<>();
        for (Registry<?> registry : registries) {
            byKey.put(registry.key(), registry);
        }
        return new RegistryAccess.Frozen() {
            @Override
            public <E> Optional<Registry<E>> lookup(ResourceKey<? extends Registry<? extends E>> registryRef) {
                return Optional.ofNullable((Registry<E>) byKey.get(registryRef));
            }

            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public java.util.stream.Stream<RegistryEntry<?>> registries() {
                return byKey.entrySet().stream()
                    .map(e -> new RegistryAccess.RegistryEntry(e.getKey(), e.getValue()));
            }

            @Override
            public java.util.stream.Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
                return byKey.keySet().stream();
            }

            @Override
            public RegistryAccess.Frozen freeze() {
                return this;
            }
        };
    }

    @Override
    public void close() {
        if (resources != null) {
            resources.close();
        }
    }
}
