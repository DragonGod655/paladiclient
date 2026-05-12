/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.message.LastSeenMessagesCollector;
import net.minecraft.network.message.MessageChain;
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.resource.featuretoggle.FeatureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayNetworkHandler.class)
public interface ClientPlayNetworkHandlerAccessor {
    @Accessor("chunkLoadDistance")
    int Paladi$getChunkLoadDistance();

    @Accessor("messagePacker")
    MessageChain.Packer Paladi$getMessagePacker();

    @Accessor("lastSeenMessagesCollector")
    LastSeenMessagesCollector Paladi$getLastSeenMessagesCollector();

    @Accessor("combinedDynamicRegistries")
    DynamicRegistryManager.Immutable Paladi$getCombinedDynamicRegistries();

    @Accessor("enabledFeatures")
    FeatureSet Paladi$getEnabledFeatures();

    @Accessor("COMMAND_NODE_FACTORY")
    static CommandTreeS2CPacket.NodeFactory<ClientCommandSource> Paladi$getCommandNodeFactory() {
        return null;
    }
}
