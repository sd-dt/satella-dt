package greenebolt.autotrade;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

/**
 * 附魔光效的「让位」判定。
 *
 * <p>背景：本模组的「附魔显示颜色」会把 {@code ItemFeatureRenderer#getFoilBuffer} 里的
 * {@code RenderTypes.glint() / glintTranslucent()} 重定向到自己的贴图。材质包同样能替换
 * {@code minecraft:textures/misc/enchanted_glint_*.png}，光影（Iris）也会自己接管光效的绘制 ——
 * 这等于和它们抢优先级。按用户要求：<b>只要它们已经自己处理了附魔光效，本模组就让位</b>
 * （不创建自定义渲染层，直接返回原版 {@code RenderType}）。
 *
 * <p>两个让位条件：
 * <ol>
 *   <li><b>光影启用</b>：Iris {@code IrisApi#isShaderPackInUse()} 为真。
 *       反射调用，Iris 缺席或 API 变动时恒为 false（退化为「不让位」而不是报错）。</li>
 *   <li><b>材质包改过贴图</b>：当前生效的 {@code enchanted_glint_item.png} /
 *       {@code enchanted_glint_armor.png} 与 26.2 原版不一致（比对 SHA-1，见
 *       {@link #VANILLA_GLINT_ITEM_SHA1} / {@link #VANILLA_GLINT_ARMOR_SHA1}）。
 *       用哈希而不是「资源栈里有非 vanilla 包」来判断，是为了不依赖 Fabric 对原版包的命名。</li>
 * </ol>
 *
 * <p>判定结果每 2 秒重算一次（由 {@link #tick()} 驱动），避免每帧读资源；首次调用会立刻算一次。
 * 26.2 原版贴图的 SHA-1 可直接从客户端 jar 里读：{@code assets/minecraft/textures/misc/} 下的
 * {@code enchanted_glint_item.png}（12310 B）与 {@code enchanted_glint_armor.png}（6984 B）。
 */
public final class GlintCompat {

    /** 26.2 原版 {@code enchanted_glint_item.png} 的 SHA-1。 */
    private static final String VANILLA_GLINT_ITEM_SHA1 = "b41ddaee9ba0c8eaab3bd6c8d4c48050ab0a4cc9";
    /** 26.2 原版 {@code enchanted_glint_armor.png} 的 SHA-1。 */
    private static final String VANILLA_GLINT_ARMOR_SHA1 = "fb31ac130cc8a5c09bfe6fbb0ac3987c2805ebd6";

    private static final Identifier GLINT_ITEM =
            Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
    private static final Identifier GLINT_ARMOR =
            Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");

    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    /** 每 2 秒（40 客户端 tick）重算一次。 */
    private static final int REFRESH_TICKS = 40;

    // 反射缓存的 Iris API（缺席时为 null）
    private static boolean irisLookupDone;
    private static Method irisGetInstance;
    private static Method irisIsShaderPackInUse;

    private static boolean shaderPackInUse;
    private static boolean glintTextureOverridden;
    private static boolean computed;
    private static int refreshCountdown;

    private GlintCompat() {}

    /**
     * 本模组的附魔光效当前是否应当让位（true = 直接回退原版 {@code RenderType}）。
     *
     * <p>会被渲染线程每帧调用，所以只读缓存值。
     */
    public static boolean shouldYield() {
        if (!computed) {
            refresh();
        }
        return shaderPackInUse || glintTextureOverridden;
    }

    /** 由 {@code AutoTrade.tick} 每客户端 tick 调用；内部限频到 {@link #REFRESH_TICKS}。 */
    public static void tick() {
        if (--refreshCountdown > 0) {
            return;
        }
        refreshCountdown = REFRESH_TICKS;
        refresh();
    }

    private static void refresh() {
        computed = true;
        shaderPackInUse = queryShaderPackInUse();
        glintTextureOverridden = queryGlintTextureOverridden();
    }

    // ------------------------------------------------------------------ 光影

    private static boolean queryShaderPackInUse() {
        try {
            if (!irisLookupDone) {
                irisLookupDone = true;
                Class<?> api = Class.forName(IRIS_API_CLASS);
                irisGetInstance = api.getMethod("getInstance");
                irisIsShaderPackInUse = api.getMethod("isShaderPackInUse");
            }
            if (irisGetInstance == null || irisIsShaderPackInUse == null) {
                return false;
            }
            Object instance = irisGetInstance.invoke(null);
            return instance != null && Boolean.TRUE.equals(irisIsShaderPackInUse.invoke(instance));
        } catch (Throwable t) {
            // 没装 Iris / API 变动：永久视为「没有光影」
            irisGetInstance = null;
            irisIsShaderPackInUse = null;
            return false;
        }
    }

    // ------------------------------------------------------------------ 材质包

    private static boolean queryGlintTextureOverridden() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return false;
            }
            ResourceManager resourceManager = minecraft.getResourceManager();
            if (resourceManager == null) {
                return false;
            }
            return differsFromVanilla(resourceManager, GLINT_ITEM, VANILLA_GLINT_ITEM_SHA1)
                    || differsFromVanilla(resourceManager, GLINT_ARMOR, VANILLA_GLINT_ARMOR_SHA1);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean differsFromVanilla(ResourceManager resourceManager, Identifier id, String vanillaSha1) {
        Optional<Resource> resource = resourceManager.getResource(id);
        if (resource.isEmpty()) {
            // 资源不存在（极端情况）：交给原版判断，不因此让位
            return false;
        }
        try (InputStream in = resource.get().open()) {
            return !vanillaSha1.equalsIgnoreCase(sha1(in.readAllBytes()));
        } catch (Throwable t) {
            return false;
        }
    }

    private static String sha1(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 诊断用：当前的让位原因（写进日志/诊断文件）。 */
    public static String describe() {
        if (!computed) {
            refresh();
        }
        if (shaderPackInUse) {
            return "光影已启用（Iris）→ 让位";
        }
        if (glintTextureOverridden) {
            return "材质包替换过原版附魔光效贴图 → 让位";
        }
        return "无外部接管，使用本模组光效";
    }

    /** 供诊断日志读取：资源栈里提供原版光效贴图的包（便于确认是哪个材质包改的）。 */
    public static String glintSources() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.getResourceManager() == null) {
                return "(不可用)";
            }
            List<Resource> stack = minecraft.getResourceManager().getResourceStack(GLINT_ITEM);
            StringBuilder sb = new StringBuilder();
            for (Resource resource : stack) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(resource.sourcePackId());
            }
            return sb.length() == 0 ? "(无)" : sb.toString();
        } catch (Throwable t) {
            return "(读取失败)";
        }
    }
}
