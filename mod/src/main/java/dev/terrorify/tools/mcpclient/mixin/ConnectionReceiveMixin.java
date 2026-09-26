package dev.terrorify.tools.mcpclient.mixin;

import dev.terrorify.tools.mcpclient.net.NetTaps;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Taps inbound position/teleport corrections as decoded by Netty — this is
 * the server's authority telling the client "you are here", the other half
 * of the movement-authority diff (client-sent vs server-corrected).
 */
@Mixin(Connection.class)
public abstract class ConnectionReceiveMixin {
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void mcpclient$onReceive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ClientboundPlayerPositionPacket position) {
            NetTaps.recordInbound(position);
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket chunkPacket) {
            System.out.println("[mcpclient-debug] Received LevelChunkWithLightPacket: (" + chunkPacket.getX() + ", " + chunkPacket.getZ() + ")");
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket) {
            System.out.println("[mcpclient-debug] Received ChunkBatchStartPacket");
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket finished) {
            System.out.println("[mcpclient-debug] Received ChunkBatchFinishedPacket: batchSize=" + finished.batchSize());
        }
    }
}
