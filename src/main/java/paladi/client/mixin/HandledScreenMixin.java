/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.misc.InventoryTweaks;
import paladi.client.systems.modules.render.BetterTooltips;
import paladi.client.systems.modules.render.ItemHighlight;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.packet.c2s.play.SlotChangedStateC2SPacket;
import net.minecraft.screen.CrafterScreenHandler;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Unique;
import paladi.client.systems.modules.world.EasyPlacePlus;
import paladi.client.systems.modules.misc.CrafterMemory;
import paladi.client.utils.player.ChatUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static paladi.client.PaladiClient.mc;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> extends Screen implements ScreenHandlerProvider<T> {
    @Shadow
    protected Slot focusedSlot;

    @Shadow
    protected int x;
    @Shadow
    protected int y;

    @Shadow
    @Nullable
    protected abstract Slot getSlotAt(double xPosition, double yPosition);

    @Shadow
    public abstract T getScreenHandler();

    @Shadow
    private boolean doubleClicking;

    @Shadow
    protected abstract void onMouseClick(Slot slot, int invSlot, int clickData, SlotActionType actionType);

    @Shadow
    public abstract void close();

    public HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        InventoryTweaks invTweaks = Modules.get().get(InventoryTweaks.class);

        if (invTweaks.isActive() && invTweaks.showButtons() && invTweaks.canSteal(getScreenHandler())) {
            addDrawableChild(
                new ButtonWidget.Builder(Text.literal("Steal"), button -> invTweaks.steal(getScreenHandler()))
                    .position(x, y - 22)
                    .size(40, 20)
                    .build()
            );

            addDrawableChild(
                new ButtonWidget.Builder(Text.literal("Dump"), button -> invTweaks.dump(getScreenHandler()))
                    .position(x + 42, y - 22)
                    .size(40, 20)
                    .build()
            );
        }

        if (getScreenHandler() instanceof CrafterScreenHandler crafterHandler) {
            initCrafterButtons(crafterHandler);
        }
    }

    @Unique
    private void initCrafterButtons(CrafterScreenHandler handler) {
        // Nút PASTE (Dán Mẫu Đã Nhớ) ở trên đỉnh GUI
        addDrawableChild(
            new ButtonWidget.Builder(Text.literal("📋 Paste"), button -> {
                EasyPlacePlus easyPlace = Modules.get().get(EasyPlacePlus.class);
                if (easyPlace != null) {
                    easyPlace.pasteToCurrentCrafter();
                } else {
                    applyCrafterSlots(handler, new boolean[9]);
                }
            })
            .position(x, y - 22)
            .size(54, 20)
            .build()
        );

        // Nút COPY (Lưu Mẫu Đang Mở)
        addDrawableChild(
            new ButtonWidget.Builder(Text.literal("📥 Copy"), button -> {
                EasyPlacePlus easyPlace = Modules.get().get(EasyPlacePlus.class);
                CrafterMemory crafterMemory = Modules.get().get(CrafterMemory.class);
                for (int i = 0; i < 9; i++) {
                    boolean isEnabled = !handler.isSlotDisabled(i);
                    if (easyPlace != null) easyPlace.crafterSlots[i].set(isEnabled);
                    if (crafterMemory != null) crafterMemory.slots[i].set(isEnabled);
                }
                ChatUtils.sendMsg(Formatting.GREEN, "[Crafter] Đã lưu cấu hình 9 ô hiện tại vào bộ nhớ Paste!");
            })
            .position(x + 56, y - 22)
            .size(54, 20)
            .build()
        );

        // Sidebar Presets bên phải GUI (x + 180)
        int rightX = x + 180;
        int btnW = 96;
        int btnH = 18;

        // 1. Bone -> Meal (Chỉ mở ô giữa)
        addDrawableChild(
            new ButtonWidget.Builder(Text.literal("🎯 Bone ➔ Meal"), button -> {
                boolean[] pattern = new boolean[] { true, true, true, true, false, true, true, true, true };
                applyCrafterSlots(handler, pattern);
                ChatUtils.sendMsg(Formatting.GREEN, "[Crafter] Đã chọn: Xương ➔ Bột Xương (Mở ô giữa)!");
            })
            .position(rightX, y + 4)
            .size(btnW, btnH)
            .build()
        );

        // 2. Bone Meal -> Bone Block (Mở cả 9 ô)
        addDrawableChild(
            new ButtonWidget.Builder(Text.literal("🔄 Bone Block"), button -> {
                boolean[] pattern = new boolean[9]; // Mở cả 9 ô
                applyCrafterSlots(handler, pattern);
                ChatUtils.sendMsg(Formatting.GREEN, "[Crafter] Đã chọn: Bột Xương ➔ Khối Xương (Mở cả 9 ô)!");
            })
            .position(rightX, y + 24)
            .size(btnW, btnH)
            .build()
        );

        // 3. Mở Ô 1 Góc Trên
        addDrawableChild(
            new ButtonWidget.Builder(Text.literal("↖ Mở Ô 1 (Góc)"), button -> {
                boolean[] pattern = new boolean[] { false, true, true, true, true, true, true, true, true };
                applyCrafterSlots(handler, pattern);
                ChatUtils.sendMsg(Formatting.GREEN, "[Crafter] Đã chọn: Mở ô góc trên bên trái!");
            })
            .position(rightX, y + 44)
            .size(btnW, btnH)
            .build()
        );

        // 4. Mở 4 Ô 2x2
        addDrawableChild(
            new ButtonWidget.Builder(Text.literal("🔲 Mở 4 Ô 2x2"), button -> {
                boolean[] pattern = new boolean[] { false, false, true, false, false, true, true, true, true };
                applyCrafterSlots(handler, pattern);
                ChatUtils.sendMsg(Formatting.GREEN, "[Crafter] Đã chọn: Mở 4 ô 2x2!");
            })
            .position(rightX, y + 64)
            .size(btnW, btnH)
            .build()
        );

        // 5. Khóa Hết 9 Ô
        addDrawableChild(
            new ButtonWidget.Builder(Text.literal("❌ Khóa Hết"), button -> {
                boolean[] pattern = new boolean[] { true, true, true, true, true, true, true, true, true };
                applyCrafterSlots(handler, pattern);
                ChatUtils.sendMsg(Formatting.RED, "[Crafter] Đã khóa toàn bộ 9 ô!");
            })
            .position(rightX, y + 84)
            .size(btnW, btnH)
            .build()
        );
    }

    @Unique
    private void applyCrafterSlots(CrafterScreenHandler handler, boolean[] desiredDisabled) {
        if (mc.getNetworkHandler() == null || mc.player == null) return;
        for (int i = 0; i < 9; i++) {
            boolean shouldBeDisabled = desiredDisabled[i];
            boolean shouldBeEnabled = !shouldBeDisabled;
            if (handler.isSlotDisabled(i) != shouldBeDisabled) {
                handler.setSlotEnabled(i, shouldBeEnabled);
                mc.getNetworkHandler().sendPacket(new SlotChangedStateC2SPacket(i, handler.syncId, shouldBeEnabled));
            }
        }
    }

    // Inventory Tweaks
    @Inject(method = "mouseDragged", at = @At("TAIL"))
    private void onMouseDragged(Click click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != GLFW_MOUSE_BUTTON_LEFT || doubleClicking || !Modules.get().get(InventoryTweaks.class).mouseDragItemMove()) return;

        Slot slot = getSlotAt(click.x(), click.y());
        if (slot != null && slot.hasStack() && mc.isShiftPressed()) onMouseClick(slot, slot.id, click.button(), SlotActionType.QUICK_MOVE);
    }

    // Middle click open
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        BetterTooltips tooltips = Modules.get().get(BetterTooltips.class);

        if (tooltips.shouldOpenContents(click) && focusedSlot != null && !focusedSlot.getStack().isEmpty() && getScreenHandler().getCursorStack().isEmpty()) {
            if (tooltips.openContent(focusedSlot.getStack())) {
                cir.setReturnValue(true);
            }
        }
    }

    // Keyboard input for middle click open
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void keyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        BetterTooltips tooltips = Modules.get().get(BetterTooltips.class);

        if (tooltips.shouldOpenContents(input) && focusedSlot != null && !focusedSlot.getStack().isEmpty() && getScreenHandler().getCursorStack().isEmpty()) {
            if (tooltips.openContent(focusedSlot.getStack())) {
                cir.setReturnValue(true);
            }
        }
    }

    // Item Highlight
    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void onDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        int color = Modules.get().get(ItemHighlight.class).getColor(slot.getStack());
        if (color != -1) context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }

    @ModifyReturnValue(method = "isItemTooltipSticky", at = @At("RETURN"))
    private boolean isTooltipSticky(boolean original, ItemStack item) {
        if (item.getTooltipData().orElse(null) instanceof TooltipComponent component) {
            return original || component.isSticky();
        }

        return original;
    }
}
