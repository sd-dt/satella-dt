package greenebolt.autotrade.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = RenderType.class, remap = false)
public interface RenderTypeInvoker {
    @Invoker("create")
    static RenderType autoTrade$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
