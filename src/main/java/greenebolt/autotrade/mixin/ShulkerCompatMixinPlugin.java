package greenebolt.autotrade.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

/**
 * 可选兼容 mixin 的开关（移植自 1.21.11 主版）。
 *
 * <p>itemscroller / tweakeroo / ipn 三组 mixin 的目标类来自可选的第三方模组，
 * 对应的模组没装时整条 mixin 必须跳过，否则 {@code required=true} 的配置会在启动时报
 * 「target class not found」。判定按包名分类，主配置里其余 mixin 一律放行。
 */
public final class ShulkerCompatMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.contains(".itemscroller.")) {
            if (mixinClassName.contains(".ipn.")) {
                return FabricLoader.getInstance().isModLoaded("inventoryprofilesnext");
            }
            if (mixinClassName.contains(".tweakeroo.")) {
                return FabricLoader.getInstance().isModLoaded("tweakeroo");
            }
            return true;
        }
        return FabricLoader.getInstance().isModLoaded("itemscroller");
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
