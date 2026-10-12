/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import paladi.client.PaladiClient;
import paladi.client.events.entity.player.DoAttackEvent;
import paladi.client.events.entity.player.DoItemUseEvent;
import paladi.client.events.entity.player.ItemUseCrosshairTargetEvent;
import paladi.client.events.game.GameLeftEvent;
import paladi.client.events.game.OpenScreenEvent;
import paladi.client.events.game.ResolutionChangedEvent;
import paladi.client.events.game.ResourcePacksReloadedEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.gui.WidgetScreen;
import paladi.client.mixininterface.IMinecraftClient;
import paladi.client.systems.config.Config;
import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.misc.InventoryTweaks;
import paladi.client.systems.modules.movement.GUIMove;
import paladi.client.systems.modules.player.FastUse;
import paladi.client.systems.modules.player.Multitask;
import paladi.client.systems.modules.render.ESP;
import paladi.client.utils.Utils;
import paladi.client.utils.misc.CPSUtils;
import paladi.client.utils.misc.PaladiStarscript;
import paladi.client.utils.network.OnlinePlayers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.profiler.Profilers;
import org.jetbrains.annotations.Nullable;
import org.meteordev.starscript.Script;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(value = MinecraftClient.class, priority = 1001)
public abstract class MinecraftClientMixin implements IMinecraftClient {
    @Unique private boolean doItemUseCalled;
    @Unique private boolean rightClick;
    @Unique private long lastTime;
    @Unique private boolean firstFrame;

    @Shadow public ClientWorld world;
    @Shadow @Final public Mouse mouse;
    @Shadow @Final private Window window;
    @Shadow public Screen currentScreen;
    @Shadow @Final public GameOptions options;

    @Shadow protected abstract void doItemUse();

    @Shadow
    @Nullable
    public ClientPlayerInteractionManager interactionManager;

    @Shadow
    private int itemUseCooldown;

    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    @Shadow
    @Final
    @Mutable
    private Framebuffer framebuffer;

    @Shadow
    protected abstract void handleBlockBreaking(boolean breaking);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        PaladiClient.INSTANCE.onInitializeClient();
        firstFrame = true;
    }

    @Inject(at = @At("HEAD"), method = "tick")
    private void onPreTick(CallbackInfo info) {
        OnlinePlayers.update();

        doItemUseCalled = false;

        Profilers.get().push(PaladiClient.MOD_ID + "_pre_update");
        PaladiClient.EVENT_BUS.post(TickEvent.Pre.get());
        Profilers.get().pop();

        if (rightClick && !doItemUseCalled && interactionManager != null) doItemUse();
        rightClick = false;
    }

    @Inject(at = @At("TAIL"), method = "tick")
    private void onTick(CallbackInfo info) {
        Profilers.get().push(PaladiClient.MOD_ID + "_post_update");
        PaladiClient.EVENT_BUS.post(TickEvent.Post.get());
        Profilers.get().pop();
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void onAttack(CallbackInfoReturnable<Boolean> cir) {
        CPSUtils.onAttack();
        if (PaladiClient.EVENT_BUS.post(DoAttackEvent.get()).isCancelled()) cir.cancel();
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void onDoItemUseBeforeHands(CallbackInfo ci) {
        doItemUseCalled = true;
        if (PaladiClient.EVENT_BUS.post(DoItemUseEvent.get()).isCancelled()) {
            this.itemUseCooldown = 4;
            ci.cancel();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;ZZ)V", at = @At("HEAD"))
    private void onDisconnect(Screen screen, boolean transferring, boolean stopSound, CallbackInfo info) {
        if (world != null) {
            PaladiClient.EVENT_BUS.post(GameLeftEvent.get());
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo info) {
        if (screen instanceof WidgetScreen) screen.mouseMoved(mouse.getX() * window.getScaleFactor(), mouse.getY() * window.getScaleFactor());

        OpenScreenEvent event = OpenScreenEvent.get(screen);
        PaladiClient.EVENT_BUS.post(event);

        if (event.isCancelled()) info.cancel();
    }

    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;unpressAll()V"))
    private void onSetScreenKeyBindingUnpressAll(Operation<Void> op) {
        Modules modules = Modules.get();
        if (modules == null) {
            op.call();
            return;
        }

        GUIMove guimove = modules.get(GUIMove.class);
        if (guimove == null || !guimove.isActive() || guimove.skip()) {
            op.call();
            return;
        }

        GameOptions options = PaladiClient.mc.options;
        for (KeyBinding kb : KeyBindingAccessor.getKeysById().values()) {
            if (kb == options.forwardKey) continue;
            if (kb == options.leftKey) continue;
            if (kb == options.rightKey) continue;
            if (kb == options.backKey) continue;
            if (guimove.sneak.get() && kb == options.sneakKey) continue;
            if (guimove.sprint.get() && kb == options.sprintKey) continue;
            if (guimove.jump.get() && kb == options.jumpKey) continue;
            ((KeyBindingAccessor) kb).Paladi$invokeReset();
        }
    }

    @Inject(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isItemEnabled(Lnet/minecraft/resource/featuretoggle/FeatureSet;)Z"))
    private void onDoItemUseHand(CallbackInfo ci, @Local ItemStack itemStack) {
        FastUse fastUse = Modules.get().get(FastUse.class);
        if (fastUse.isActive()) {
            itemUseCooldown = fastUse.getItemUseCooldown(itemStack);
        }
    }



    @ModifyExpressionValue(method = "doItemUse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;crosshairTarget:Lnet/minecraft/util/hit/HitResult;", ordinal = 1))
    private HitResult doItemUseMinecraftClientCrosshairTargetProxy(HitResult original) {
        return PaladiClient.EVENT_BUS.post(ItemUseCrosshairTargetEvent.get(original)).target;
    }

    @ModifyReturnValue(method = "reloadResources(ZLnet/minecraft/client/MinecraftClient$LoadingContext;)Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"))
    private CompletableFuture<Void> onReloadResourcesNewCompletableFuture(CompletableFuture<Void> original) {
        return original.thenRun(() -> PaladiClient.EVENT_BUS.post(ResourcePacksReloadedEvent.get()));
    }

    @ModifyArg(method = "updateWindowTitle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;setTitle(Ljava/lang/String;)V"))
    private String setTitle(String original) {
        if (Config.get() == null || !Config.get().customWindowTitle.get()) return original;

        String customTitle = Config.get().customWindowTitleText.get();
        Script script = PaladiStarscript.compile(customTitle);

        if (script != null) {
            String title = PaladiStarscript.run(script);
            if (title != null) customTitle = title;
        }

        return customTitle;
    }


    @Inject(method = "onResolutionChanged", at = @At("TAIL"))
    private void onResolutionChanged(CallbackInfo info) {
        PaladiClient.EVENT_BUS.post(ResolutionChangedEvent.get());
    }

    // Time delta

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(CallbackInfo info) {
        long time = System.currentTimeMillis();

        if (firstFrame) {
            lastTime = time;
            firstFrame = false;
        }

        Utils.frameTime = (time - lastTime) / 1000.0;
        lastTime = time;
    }

    // Multitask

    @ModifyExpressionValue(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;isBreakingBlock()Z"))
    private boolean doItemUseModifyIsBreakingBlock(boolean original) {
        return !Modules.get().isActive(Multitask.class) && original;
    }

    @ModifyExpressionValue(method = "handleBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean handleBlockBreakingModifyIsUsingItem(boolean original) {
        return !Modules.get().isActive(Multitask.class) && original;
    }

    @ModifyExpressionValue(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z", ordinal = 0))
    private boolean handleInputEventsModifyIsUsingItem(boolean original) {
        return !Modules.get().get(Multitask.class).attackingEntities() && original;
    }

    @Inject(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z", ordinal = 0, shift = At.Shift.BEFORE))
    private void handleInputEventsInjectStopUsingItem(CallbackInfo info) {
        if (Modules.get().get(Multitask.class).attackingEntities() && player.isUsingItem()) {
            if (!options.useKey.isPressed()) interactionManager.stopUsingItem(player);
            //noinspection StatementWithEmptyBody
            while (options.useKey.wasPressed());
        }
    }

    // Glow esp

    @ModifyReturnValue(method = "hasOutline", at = @At("RETURN"))
    private boolean hasOutlineModifyIsOutline(boolean original, Entity entity) {
        ESP esp = Modules.get().get(ESP.class);
        if (esp == null) return original;
        if (!esp.isGlow() || esp.shouldSkip(entity)) return original;

        return esp.getColor(entity) != null || original;
    }


    // faster inputs

    @Unique
    private boolean isBreaking = false;

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;handleInputEvents()V"))
    private boolean wrapHandleInputEvents(MinecraftClient instance) {
        return !Modules.get().get(InventoryTweaks.class).frameInput();
    }

    @WrapWithCondition(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;handleBlockBreaking(Z)V"))
    private boolean wrapHandleBlockBreaking(MinecraftClient instance, boolean breaking) {
        isBreaking = breaking;
        return !Modules.get().get(InventoryTweaks.class).frameInput();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;handleInputEvents()V", shift = At.Shift.AFTER))
    private void afterHandleInputEvents(CallbackInfo ci) {
        if (!Modules.get().get(InventoryTweaks.class).frameInput()) return;

        handleBlockBreaking(isBreaking);
        isBreaking = false;
    }

    // Interface

    @Override
    public void Paladi$rightClick() {
        rightClick = true;
    }

    @Override
    public void Paladi$setItemUseCooldown(int cooldown) {
        this.itemUseCooldown = cooldown;
    }

    @Override
    public void Paladi$setFramebuffer(Framebuffer framebuffer) {
        this.framebuffer = framebuffer;
    }
}
