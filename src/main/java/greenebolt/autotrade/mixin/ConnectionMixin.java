package greenebolt.autotrade.mixin;

import greenebolt.autotrade.AutoTradeConfigs;
import greenebolt.autotrade.TradeExecutor;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Connection.class, remap = false)
public class ConnectionMixin {
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"), remap = false)
    private void autoTrade$onPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (!AutoTradeConfigs.isEnabled() || !(packet instanceof ClientboundMerchantOffersPacket offers)) return;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> TradeExecutor.handleOffers(offers));
    }
}
