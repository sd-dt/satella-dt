package greenebolt.autotrade.stlocator;

import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.DataFixer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * /st 定位器：纯客户端、离线的群系/结构查找。
 * 算法移植自 map.jacobsjo.eu（deepslate）并换用原版实现：
 *  - 群系：BiomeSource.findBiomeHorizontal 的螺旋搜索（32 格水平步长，6400 半径）；
 *  - 结构：结构集 placement 粗筛（RandomSpread 环形遍历 / ConcentricRings 预计算），
 *    候选区块再用 Structure.findValidGenerationPoint 精确判定。
 */
public final class StLocator {
    public static final Logger LOGGER = LoggerFactory.getLogger("satella-st");
    /** 同 LocateCommand 的结构搜索半径 */
    private static final int LOCATE_STRUCTURE_RADIUS = 100;

    /** 未命中环规则时使用的全局默认种子（/st seed 设置，持久化到 st-seed.txt） */
    private static volatile long seed;
    private static volatile boolean seedInitialized;
    private static Path configDir;
    /** (种子, 数据包子集) -> 世界生成栈缓存，各建一份互不影响 */
    private static final java.util.Map<WorldgenKey, DatapackWorldgen> worldgenCache =
        new java.util.LinkedHashMap<>();

    private StLocator() {}

    public static void init(Path satellaConfigDir) {
        configDir = satellaConfigDir;
        Path seedFile = satellaConfigDir.resolve("st-seed.txt");
        if (Files.isRegularFile(seedFile)) {
            try {
                setSeed(Long.parseLong(Files.readString(seedFile).trim()), false);
            } catch (Exception e) {
                LOGGER.warn("读取 st-seed.txt 失败", e);
            }
        }
    }

    public static boolean hasSeed() {
        return seedInitialized;
    }

    public static long getSeed() {
        return seed;
    }

    public static void setSeed(long newSeed, boolean persist) {
        seed = newSeed;
        seedInitialized = true;
        if (persist && configDir != null) {
            try {
                Path f = configDir.resolve("st-seed.txt");
                Files.createDirectories(configDir);
                Files.writeString(f, Long.toString(newSeed));
            } catch (IOException e) {
                LOGGER.warn("写入 st-seed.txt 失败", e);
            }
        }
    }

    private static LevelStorageSource storage;
    private static LevelStorageSource.LevelStorageAccess templateSession;

    /**
     * 模板管理器所需会话，进程内只创建一次并持久持有：
     * session.lock 一旦释放就无法再次获取，重建会话必然撞锁。
     */
    private static synchronized LevelStorageSource.LevelStorageAccess session(DataFixer dataFixer) throws Exception {
        if (templateSession == null) {
            Path sessionRoot = configDir.resolve("st-session");
            DirectoryValidator validator = LevelStorageSource.parseValidator(sessionRoot.resolve("allowed_symlinks.txt"));
            storage = new LevelStorageSource(
                sessionRoot.resolve("saves"), sessionRoot.resolve("backups"), validator, dataFixer);
            templateSession = storage.createAccess("satella-st");
        }
        return templateSession;
    }

    /** 是否配置了任何多环环规则（有规则时指令不再强制要求全局种子） */
    public static boolean hasRules() {
        return !parseRules(greenebolt.autotrade.AutoTradeConfigs.Trade.ST_RULES.getStrings()).isEmpty();
    }

    /** 单条多环规则：[min, max] 切比雪夫距离环 → 种子 + 数据包子集（空 = 全部） */
    public record StRule(int min, int max, long seed, List<String> packs) {} // packs: null=全部（省略数据包段），空=无，非空=指定列表

    /** 一次检索选中的配置 */
    public record Selected(long seed, List<String> packs, String description) {}

    private record WorldgenKey(long seed, boolean allPacks, List<String> packs) {}

    private static final int CACHE_LIMIT = 6;

    /** 解析 MaLiLib 里的多环规则字符串：最小-最大:种子[:数据包1|数据包2] */
    public static List<StRule> parseRules(List<String> entries) {
        List<StRule> rules = new ArrayList<>();
        for (String entry : entries) {
            String line = entry.trim();
            if (line.isEmpty()) continue;
            try {
                String[] parts = line.split(":", 3);
                String[] range = parts[0].split("-");
                int min = Integer.parseInt(range[0].trim());
                int max = Integer.parseInt(range[1].trim());
                long ruleSeed = Long.parseLong(parts[1].trim());
                List<String> packs = null; // 省略数据包段 = 全部
                if (parts.length >= 3) {
                    packs = new ArrayList<>();
                    for (String pack : parts[2].split("\\|")) {
                        String p = pack.trim();
                        if (!p.isEmpty()) {
                            packs.add(p);
                        }
                    }
                }
                rules.add(new StRule(min, max, ruleSeed, packs));
            } catch (Exception e) {
                LOGGER.warn("无法解析多环定位规则 \"{}\": {}", line, e.toString());
            }
        }
        return rules;
    }

    /**
     * 按检索中心到世界原点 (0,0) 的切比雪夫距离选择环规则；
     * 未命中任何环时回退到全局默认种子 + 全部数据包。
     */
    public static Selected select(BlockPos origin) {
        int dist = Math.max(Math.abs(origin.getX()), Math.abs(origin.getZ()));
        for (StRule rule : parseRules(greenebolt.autotrade.AutoTradeConfigs.Trade.ST_RULES.getStrings())) {
            if (dist >= rule.min() && dist <= rule.max()) {
                return new Selected(rule.seed(), rule.packs(),
                    "环 " + rule.min() + "-" + rule.max() + "（种子 " + rule.seed() + "，数据包 "
                        + packsDesc(rule.packs()) + "）");
            }
        }
        return new Selected(seed, null,
            "默认配置（未命中环规则，种子 " + seed + "，数据包：全部）");
    }

    /** 序列化一条规则为字符串（与 parseRules 互逆）：最小-最大:种子[:数据包1|数据包2] */
    public static String formatRule(StRule rule) {
        StringBuilder sb = new StringBuilder()
            .append(rule.min()).append('-').append(rule.max()).append(':').append(rule.seed());
        if (rule.packs() != null) {
            sb.append(':').append(String.join("|", rule.packs()));
        }
        return sb.toString();
    }

    /** 数据包描述文本 */
    private static String packsDesc(List<String> packs) {
        if (packs == null) return "全部";
        return packs.isEmpty() ? "无" : String.join("|", packs);
    }

    /** 获取（必要时构建）指定种子与数据包子集的世界生成栈，带缓存。
     * packs 为 null 表示全部数据包，空列表表示不加载任何数据包 zip。 */
    public static DatapackWorldgen worldgen(long seed, List<String> packs) throws Exception { // packs: null=全部，空=无
        boolean allPacks = packs == null;
        List<String> keyPacks = allPacks ? List.of() : packs.stream().map(String::trim).sorted().toList();
        WorldgenKey key = new WorldgenKey(seed, allPacks, keyPacks);
        synchronized (worldgenCache) {
            DatapackWorldgen cached = worldgenCache.get(key);
            if (cached != null) {
                // LRU 触碰
                worldgenCache.remove(key);
                worldgenCache.put(key, cached);
                return cached;
            }
        }
        Path packsDir = configDir.resolve("datapacks");
        Files.createDirectories(packsDir);
        Minecraft client = Minecraft.getInstance();
        Path sessionRoot = configDir.resolve("st-session");
        DirectoryValidator validator = LevelStorageSource.parseValidator(sessionRoot.resolve("allowed_symlinks.txt"));
        DatapackWorldgen built = DatapackWorldgen.load(
            packsDir,
            validator,
            session(client.getFixerUpper()),
            client.getResourceManager(),
            client.getFixerUpper(),
            seed,
            packs);
        synchronized (worldgenCache) {
            worldgenCache.put(key, built);
            while (worldgenCache.size() > CACHE_LIMIT) {
                WorldgenKey oldest = worldgenCache.keySet().iterator().next();
                DatapackWorldgen evicted = worldgenCache.remove(oldest);
                if (evicted != null) evicted.close();
            }
        }
        return built;
    }

    public static void reload() {
        synchronized (worldgenCache) {
            for (DatapackWorldgen worldgen : worldgenCache.values()) {
                worldgen.close();
            }
            worldgenCache.clear();
        }
    }

    /** 供指令补全使用：任一已缓存的世界生成栈，没有则 null（不触发构建）。 */
    public static DatapackWorldgen peekWorldgen() {
        synchronized (worldgenCache) {
            return worldgenCache.values().stream().findFirst().orElse(null);
        }
    }

    /** 最近群系搜索，返回 null 表示半径内没找到。 */
    
    public static Pair<BlockPos, Holder<Biome>> findBiome(DatapackWorldgen worldgen, Identifier biomeId, BlockPos origin) {
        Registry<Biome> biomeRegistry = worldgen.registryManager.lookupOrThrow(Registries.BIOME);
        ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeId);
        if (biomeRegistry.get(biomeKey).isEmpty()) {
            throw new IllegalArgumentException("未知群系: " + biomeId);
        }
        // 螺旋搜索（同 /locate biome 的步长与半径），在固定 Y 层采样
        int sampleY = Math.max(worldgen.heightView.getMinY(), Math.min(64, worldgen.heightView.getMaxY() - 1));
        return worldgen.biomeSource.findBiomeHorizontal(
            origin.getX(), sampleY, origin.getZ(), 6400, 32,
            holder -> holder.is(biomeKey),
            RandomSource.create(worldgen.seed),
            true,
            worldgen.randomState.sampler());
    }

    /** 最近结构搜索，返回锚点位置；null 表示半径内没找到。 */
    
    /** 单结构搜索结果：pos=null 未找到，附诊断计数 */
    public record StructureFindResult(BlockPos pos, int placements, int candidates,
                                      int passed, int biomeFails, int validBiomeCount) {}

    public static StructureFindResult findStructure(DatapackWorldgen worldgen, Identifier structureId, BlockPos origin) {
        Registry<Structure> registry = worldgen.registryManager.lookupOrThrow(Registries.STRUCTURE);
        Holder<Structure> entry = registry.getOrThrow(ResourceKey.create(Registries.STRUCTURE, structureId));
        List<StructurePlacement> placements = worldgen.structureState.getPlacementsForStructure(entry);
        if (placements.isEmpty()) {
            LOGGER.info("结构搜索 {}: placements 为空（结构集被群系过滤或未注册），biomeSource.possibleBiomes 数量={}",
                structureId, worldgen.biomeSource.possibleBiomes().size());
            return new StructureFindResult(null, 0, 0, 0, 0, 0);
        }
        worldgen.structureState.ensureStructuresGenerated();

        Structure structure = entry.value();
        int centerChunkX = origin.getX() >> 4;
        int centerChunkZ = origin.getZ() >> 4;

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int[] diag = new int[4]; // 0=候选数 1=shouldGenerate通过 2=位置判定通过 3=群系失败

        for (StructurePlacement placement : placements) {
            if (placement instanceof ConcentricRingsStructurePlacement concentric) {
                // 要塞类：候选区块由同心环预计算直接给出
                List<ChunkPos> positions = worldgen.structureState.getRingPositionsFor(concentric);
                if (positions == null) continue;
                for (ChunkPos chunkPos : positions) {
                    diag[0]++;
                    BlockPos pos = checkStructureAt(worldgen, structure, placement, chunkPos, diag);
                    if (pos != null) {
                        double d = pos.distSqr(origin);
                        if (d < bestDist) { bestDist = d; best = pos; }
                    }
                }
            } else if (placement instanceof RandomSpreadStructurePlacement spread) {
                // 网格式：按 spacing 为步长的区域环遍历，同 /locate 的 100 半径
                // 注意 getPotentialStructureChunk 的入参是区块坐标（内部按 spacing 取模），
                // 与原版 ChunkGenerator.locateRandomSpreadStructure 一致：中心区块 + spacing*环偏移
                int spacing = spread.spacing();
                outer:
                for (int k = 0; k <= LOCATE_STRUCTURE_RADIUS; k++) {
                    for (int dx = -k; dx <= k; dx++) {
                        for (int dz = -k; dz <= k; dz++) {
                            if (Math.abs(dx) != k && Math.abs(dz) != k) continue;
                            ChunkPos start = spread.getPotentialStructureChunk(worldgen.seed, centerChunkX + spacing * dx, centerChunkZ + spacing * dz);
                            diag[0]++;
                            BlockPos pos = checkStructureAt(worldgen, structure, placement, start, diag);
                            if (pos != null) {
                                double d = pos.distSqr(origin);
                                if (d < bestDist) { bestDist = d; best = pos; }
                                // 更内环必定更近，命中后停止
                                break outer;
                            }
                        }
                    }
                }
            }
        }
        LOGGER.info("结构搜索 {}: seed={}, placements={}, 候选={}, shouldGenerate 通过={}, 位置+群系判定通过={}, 群系失败={}",
            structureId, worldgen.seed, placements.size(), diag[0], diag[1], diag[2], diag[3]);
        int tagSize;
        try {
            tagSize = structure.biomes().size();
        } catch (Exception e) {
            tagSize = -1;
        }
        return new StructureFindResult(best, placements.size(), diag[0], diag[2], diag[3], tagSize);
    }

    
    private static BlockPos checkStructureAt(DatapackWorldgen worldgen, Structure structure, StructurePlacement placement, ChunkPos chunkPos, int[] diag) {
        if (!placement.isStructureChunk(worldgen.structureState, chunkPos.x(), chunkPos.z())) {
            return null;
        }
        if (diag != null) diag[1]++;
        Structure.GenerationContext context = new Structure.GenerationContext(
            worldgen.registryManager,
            worldgen.noiseGenerator,
            worldgen.biomeSource,
            worldgen.randomState,
            worldgen.templateManager,
            worldgen.seed,
            chunkPos,
            worldgen.heightView,
            structure.biomes()::contains);
        return structure.findValidGenerationPoint(context)
            .map(stub -> {
                // findValidGenerationPoint 不一定应用群系判定，这里再确认一次锚点群系
                BlockPos p = stub.position();
                Holder<Biome> biome = worldgen.biomeSource.getNoiseBiome(
                    p.getX() >> 2, p.getY() >> 2, p.getZ() >> 2, worldgen.randomState.sampler());
                if (!structure.biomes().contains(biome)) {
                    if (diag != null) diag[3]++;
                    return null;
                }
                if (diag != null) diag[2]++;
                return placement.getLocatePos(chunkPos);
            })
            .orElseGet(() -> {
                if (diag != null) diag[3]++;
                return null;
            });
    }

    /** 就近结构命中：结构 id + 锚点坐标 + 水平距离 */
    public record StructureHit(Identifier id, BlockPos pos, int distance) {}

    /**
     * 在全部结构集中搜索离 origin 最近的 limit 个结构（跨结构类型）。
     * 结构集 placement 粗筛 + findValidGenerationPoint 精判，扫描半径 100 环。
     */
    public static List<StructureHit> findNearestStructures(DatapackWorldgen worldgen, BlockPos origin, int limit) {
        Registry<Structure> structureRegistry = worldgen.registryManager.lookupOrThrow(Registries.STRUCTURE);
        List<StructureHit> hits = new ArrayList<>();
        int centerChunkX = origin.getX() >> 4;
        int centerChunkZ = origin.getZ() >> 4;

        for (net.minecraft.world.level.levelgen.structure.StructureSet set
                : worldgen.registryManager.lookupOrThrow(Registries.STRUCTURE_SET)) {
            for (net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry weighted
                    : set.structures()) {
                Holder<Structure> structureEntry = weighted.structure();
                List<StructurePlacement> placements = worldgen.structureState.getPlacementsForStructure(structureEntry);
                if (placements.isEmpty()) continue;
                Structure structure = structureEntry.value();
                Identifier id = structureRegistry.getKey(structure);
                if (id == null) continue;

                for (StructurePlacement placement : placements) {
                    collectPlacementHits(worldgen, structure, id, placement, centerChunkX, centerChunkZ, origin, hits);
                }
            }
        }

        hits.sort((a, b) -> Integer.compare(a.distance(), b.distance()));
        return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
    }

    private static void collectPlacementHits(DatapackWorldgen worldgen, Structure structure, Identifier id,
                                             StructurePlacement placement, int centerChunkX, int centerChunkZ,
                                             BlockPos origin, List<StructureHit> hits) {
        if (placement instanceof ConcentricRingsStructurePlacement concentric) {
            List<ChunkPos> positions = worldgen.structureState.getRingPositionsFor(concentric);
            if (positions == null) return;
            for (ChunkPos chunkPos : positions) {
                BlockPos pos = checkStructureAt(worldgen, structure, placement, chunkPos, null);
                if (pos != null) {
                    hits.add(new StructureHit(id, pos, horizontalDistance(pos, origin)));
                }
            }
        } else if (placement instanceof RandomSpreadStructurePlacement spread) {
            int spacing = spread.spacing();
            for (int k = 0; k <= LOCATE_STRUCTURE_RADIUS; k++) {
                for (int dx = -k; dx <= k; dx++) {
                    for (int dz = -k; dz <= k; dz++) {
                        if (Math.abs(dx) != k && Math.abs(dz) != k) continue;
                        ChunkPos start = spread.getPotentialStructureChunk(worldgen.seed,
                            centerChunkX + spacing * dx, centerChunkZ + spacing * dz);
                        BlockPos pos = checkStructureAt(worldgen, structure, placement, start, null);
                        if (pos != null) {
                            hits.add(new StructureHit(id, pos, horizontalDistance(pos, origin)));
                        }
                    }
                }
            }
        }
    }

    private static int horizontalDistance(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        return (int) Math.sqrt((double) dx * dx + (double) dz * dz);
    }

    /** 就近群系命中：群系 id + 位置 + 切比雪夫距离 */
    public record BiomeHit(Identifier id, BlockPos pos, int distance) {}

    /**
     * 以 origin 为中心螺旋扫描（32 格步长，6400 半径，同 /locate biome），
     * 返回最近的 limit 种不同群系及首次出现位置（按切比雪夫距离排序）。
     */
    public static List<BiomeHit> findNearestBiomes(DatapackWorldgen worldgen, BlockPos origin, int limit) {
        Registry<Biome> biomeRegistry = worldgen.registryManager.lookupOrThrow(Registries.BIOME);
        List<BiomeHit> hits = new ArrayList<>();
        int sampleY = Math.max(worldgen.heightView.getMinY(), Math.min(64, worldgen.heightView.getMaxY() - 1));
        var sampler = worldgen.randomState.sampler();

        for (int r = 0; r <= 6400 && hits.size() < limit; r += 32) {
            // 切比雪夫距离环：|dx|==r 或 |dz|==r；同环内所有首次出现的群系距离相同
            for (int dx = -r; dx <= r; dx += 32) {
                for (int dz = -r; dz <= r; dz += 32) {
                    if (r != 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    Holder<Biome> biome = worldgen.biomeSource.getNoiseBiome(
                        net.minecraft.core.QuartPos.fromBlock(x),
                        net.minecraft.core.QuartPos.fromBlock(sampleY),
                        net.minecraft.core.QuartPos.fromBlock(z),
                        sampler);
                    Identifier id = biomeRegistry.getKey(biome.value());
                    if (id == null) continue;
                    boolean seen = false;
                    for (BiomeHit hit : hits) {
                        if (hit.id().equals(id)) { seen = true; break; }
                    }
                    if (!seen) {
                        hits.add(new BiomeHit(id, new BlockPos(x, sampleY, z), r));
                    }
                }
            }
        }
        return hits;
    }
    /** 生成 Xaero 小地图航点分享串（聊天栏出现时可被 Xaero 识别导入） */
    public static String xaeroWaypoint(String id, BlockPos pos) {
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String abbr = name.length() > 2 ? name.substring(0, 2) : name;
        int y = pos.getY() <= 0 ? 64 : pos.getY();
        return "xaero-waypoint:" + name + ":" + abbr + ":" + pos.getX() + ":" + y + ":"
            + pos.getZ() + ":10:false:0:Internal-overworld";
    }
}
