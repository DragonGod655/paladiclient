/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.misc.AutoReconnect;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

import static paladi.client.PaladiClient.mc;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    @Shadow
    @Final
    private DirectionalLayoutWidget grid;
    @Unique private ButtonWidget reconnectBtn;
    @Unique private double time = Modules.get().get(AutoReconnect.class).time.get() * 20;

    protected DisconnectedScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/DirectionalLayoutWidget;refreshPositions()V", shift = At.Shift.BEFORE))
    private void addButtons(CallbackInfo ci) {
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);

        if (autoReconnect.lastServerConnection != null && !autoReconnect.button.get()) {
            reconnectBtn = new ButtonWidget.Builder(Text.literal(getText()), button -> tryConnecting()).build();
            grid.add(reconnectBtn);

            grid.add(
                new ButtonWidget.Builder(Text.literal("Toggle Auto Reconnect"), button -> {
                    autoReconnect.toggle();
                    reconnectBtn.setMessage(Text.literal(getText()));
                    time = autoReconnect.time.get() * 20;
                }).build()
            );
        }
    }

    @Override
    public void tick() {
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (!autoReconnect.isActive() || autoReconnect.lastServerConnection == null) return;

        // Chặn tự động kết nối lại nếu có kích hoạt bảo vệ
        if (isAutoSellBlockReconnectEnabled()) {
            return;
        }

        if (time <= 0) {
            tryConnecting();
        } else {
            time--;
            if (reconnectBtn != null) reconnectBtn.setMessage(Text.literal(getText()));
        }
    }

    @Unique
    private boolean isAutoSellBlockReconnectEnabled() {
        try {
            Class<?> autoSellModClass = Class.forName("autosell.AutoSellMod");
            Field field = autoSellModClass.getField("blockReconnect");
            return field.getBoolean(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Unique
    private void resetAutoSellBlockReconnect() {
        try {
            Class<?> autoSellModClass = Class.forName("autosell.AutoSellMod");
            Field field = autoSellModClass.getField("blockReconnect");
            field.setBoolean(null, false);
        } catch (Throwable ignored) {
            // Ignore when the autosell module is not present in the current build.
        }
    }

    @Unique
    private String getText() {
        String reconnectText = "Reconnect";
        if (Modules.get().isActive(AutoReconnect.class)) reconnectText += " " + String.format("(%.1f)", time / 20);
        return reconnectText;
    }

    @Unique
    private void tryConnecting() {
        resetAutoSellBlockReconnect(); // Reset cờ chặn khi click kết nối thủ công
        var lastServer = Modules.get().get(AutoReconnect.class).lastServerConnection;
        ConnectScreen.connect(new TitleScreen(), mc, lastServer.left(), lastServer.right(), false, null);
    }
}
