package dev.terrorify.tools.mcpclient.mixin;

import dev.terrorify.tools.mcpclient.net.NetTaps;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Taps every outbound packet at {@link Connection}'s single leaf send
 * overload (the 1-arg and 2-arg overloads both delegate here) — the exact
 * bytes handed to Netty, i.e. what internode sees.
 */
@Mixin(Connection.class)
public abstract class ConnectionSendMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"))
    private void mcpclient$onSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (packet instanceof ServerboundMovePlayerPacket move) {
            NetTaps.recordOutbound(move);
        }
    }
}
