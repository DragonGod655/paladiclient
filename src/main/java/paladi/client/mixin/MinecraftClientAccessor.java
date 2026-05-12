/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.resource.ResourceReloadLogger;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.util.ApiServices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("currentFps")
    static int Paladi$getFps() {
        return 0;
    }

    @Mutable
    @Accessor("session")
    void Paladi$setSession(Session session);

    @Accessor("resourceReloadLogger")
    ResourceReloadLogger Paladi$getResourceReloadLogger();

    @Accessor("attackCooldown")
    int Paladi$getAttackCooldown();

    @Accessor("attackCooldown")
    void Paladi$setAttackCooldown(int attackCooldown);

    @Invoker("doAttack")
    boolean Paladi$leftClick();

    @Mutable
    @Accessor("profileKeys")
    void Paladi$setProfileKeys(ProfileKeys keys);

    @Mutable
    @Accessor("userApiService")
    void Paladi$setUserApiService(UserApiService apiService);

    @Mutable
    @Accessor("skinProvider")
    void Paladi$setSkinProvider(PlayerSkinProvider skinProvider);

    @Mutable
    @Accessor("socialInteractionsManager")
    void Paladi$setSocialInteractionsManager(SocialInteractionsManager socialInteractionsManager);

    @Mutable
    @Accessor("abuseReportContext")
    void Paladi$setAbuseReportContext(AbuseReportContext abuseReportContext);

    @Mutable
    @Accessor("gameProfileFuture")
    void Paladi$setGameProfileFuture(CompletableFuture<ProfileResult> future);

    @Mutable
    @Accessor("apiServices")
    void Paladi$setApiServices(ApiServices apiServices);

    @Invoker("handleInputEvents")
    void Paladi$handleInputEvents();
}
