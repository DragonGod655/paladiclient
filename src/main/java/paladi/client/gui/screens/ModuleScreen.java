/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.screens;

import paladi.client.PaladiClient;
import paladi.client.events.Paladi.ActiveModulesChangedEvent;
import paladi.client.events.Paladi.ModuleBindChangedEvent;
import paladi.client.gui.GuiTheme;
import paladi.client.gui.WidgetScreen;
import paladi.client.gui.WindowScreen;
import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.utils.Cell;
import paladi.client.gui.widgets.WKeybind;
import paladi.client.gui.widgets.WWidget;
import paladi.client.gui.widgets.containers.WContainer;
import paladi.client.gui.widgets.containers.WHorizontalList;
import paladi.client.gui.widgets.containers.WSection;
import paladi.client.gui.widgets.pressable.WButton;
import paladi.client.gui.widgets.pressable.WCheckbox;
import paladi.client.gui.widgets.pressable.WFavorite;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Modules;
import paladi.client.utils.misc.NbtUtils;
import paladi.client.utils.render.prompts.OkPrompt;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.NbtCompound;

import java.util.Optional;

import static paladi.client.utils.Utils.getWindowWidth;

public class ModuleScreen extends WindowScreen {
    private final Module module;

    private WContainer settingsContainer;
    private WKeybind keybind;
    private WCheckbox active;

    public ModuleScreen(GuiTheme theme, Module module) {
        super(theme, theme.favorite(module.favorite), module.title);
        ((WFavorite) window.icon).action = () -> module.favorite = ((WFavorite) window.icon).checked;

        this.module = module;
    }

    private String getVietnameseHelp(Module module) {
        switch (module.name) {
            case "sus-chunk-finder": return "Quet tim ruong, lo nung, TNT de phat hien base/raid.";
            case "tunnel-base-finder": return "Phat hien duong ham 1x2 duoi dat de lan ra base.";
            case "netherite-finder": return "X-ray sieu cap tim Ancient Debris.";
            case "rtp-base-finder": return "Tu dong /rtp tim base (dung khi thay ruong/lo).";
            case "auto-sell": return "Tu dong go lenh ban do rac.";
            case "auto-spawner-sell": return "Tu dong ban spawner.";
            case "auto-shulker-buy": return "Tu dong mua hop shulker.";
            case "auction-sniper": return "Tu dong canh mua do re tren dau gia.";
            case "trigger-bot": return "Tu dong chem dich khi hoi xong vu khi.";
            case "mace-bomber": return "Roi tu tren cao tu doi Chuy (Mace) dap dich.";
            case "shield-breaker": return "Dich gio khien -> tu doi Riu chem vo khien.";
            case "auto-inv-totem": return "Tu lay Totem trong tui khi mau thap.";
            case "hover-totem": return "Tu dong chuan bi san Totem.";
            case "totem-offhand": return "Luon cam Totem o tay trai.";
            case "no-hit-delay": return "Xoa delay danh (dung cho server ko cooldown).";
            case "elytra-swap": return "Doi nhanh Ao giap va Canh.";
            case "mace-swap": return "Doi nhanh sang Chuy (Mace).";
            case "spear-swap": return "Doi nhanh sang Dinh Ba (Trident).";
            case "anti-trap": return "Canh bao khi bi dich nhot/xay block bao quanh.";
            case "auto-mine": return "Tu dong dao block dang nhin.";
            case "auto-loot": return "Tu hut do xin dang roi.";
            case "auto-tpa": return "Spam gui yeu cau TPA.";
            case "elytra-glide": return "Giup bay Elytra muot hon, ko bi roi dot ngot.";
            case "key-pearl": return "Phim tat nem ngoc Ender.";
            case "key-wind-charge": return "Phim tat nem bom gio.";
            case "auto-firework": return "Tu dong xai phao hoa khi bay.";
            case "spawner-protect": return "Bao dong khi co nguoi lai gan Spawner.";
            case "fake-stats": return "Hien chi so K/D gia.";
            case "skin-protect": return "An skin de dich kho nham.";
            case "chat-macro": return "Tu dong spam tin nhan.";
            case "radio": return "Nghe nhac khi choi.";
            case "friends": return "Them ban be de ko danh nham.";
            case "hud": return "Tuy chinh thong tin man hinh (FPS, Ping...).";
            case "swing-speed": return "Vung tay danh nhanh hon.";
            case "real-hitbox": return "Hien hitbox that cua nguoi choi.";
            case "static-hitboxes": return "Co dinh hitbox, de chem trung hon.";
            case "seed-chunk-finder": return "Du doan chunk ngon tu seed map.";
            case "jungle-finder": return "Tim rung ram (Jungle).";
            case "suspicious-esp": return "Hien ESP block bat thuong.";
            case "cord-snapper": return "Luu toa do vi tri hien tai.";
            case "item-dropper": return "Vut do sieu nhanh.";
            case "auto-double-hand": return "Quan ly do hai tay khi combat.";
            case "kill-aura": return "Tu dong chem moi thu xung quanh.";
            case "crystal-aura": return "Tu dong dat/pha End Crystal.";
            case "anchor-aura": return "Tu dong xai Respawn Anchor.";
            case "auto-totem": return "Bu Totem tu dong vao tay trai.";
            case "chunk-finder": return "Hien chunk da co nguoi di qua.";
            default: return module.description;
        }
    }

    @Override
    public void initWidgets() {
        if (module.addon != null && module.addon != PaladiClient.ADDON) {
            WHorizontalList addon = add(theme.horizontalList()).expandX().widget();
            addon.add(theme.label("From: ").color(theme.textSecondaryColor())).widget();
            addon.add(theme.label(module.addon.name).color(module.addon.color)).widget();
        }

        // Settings
        if (!module.settings.groups.isEmpty()) {
            settingsContainer = add(theme.verticalList()).expandX().widget();
            settingsContainer.add(theme.settings(module.settings)).expandX();
        }

        // Custom widget
        WWidget widget = module.getWidget(theme);

        if (widget != null) {
            add(theme.horizontalSeparator()).expandX();
            Cell<WWidget> cell = add(widget);
            if (widget instanceof WContainer) cell.expandX();
        }

        // Bind
        WSection section = add(theme.section("Bind", true)).expandX().widget();

        // Keybind
        WHorizontalList bind = section.add(theme.horizontalList()).expandX().widget();

        bind.add(theme.label("Bind: "));
        keybind = bind.add(theme.keybind(module.keybind)).expandX().widget();
        keybind.actionOnSet = () -> Modules.get().setModuleToBind(module);

        WButton reset = bind.add(theme.button(GuiRenderer.RESET)).expandCellX().right().widget();
        reset.action = keybind::resetBind;
        reset.tooltip = "Reset";

        // Toggle on bind release
        WHorizontalList tobr = section.add(theme.horizontalList()).widget();

        tobr.add(theme.label("Toggle on bind release: "));
        WCheckbox tobrC = tobr.add(theme.checkbox(module.toggleOnBindRelease)).widget();
        tobrC.action = () -> module.toggleOnBindRelease = tobrC.checked;

        // Chat feedback
        WHorizontalList cf = section.add(theme.horizontalList()).widget();

        cf.add(theme.label("Chat Feedback: "));
        WCheckbox cfC = cf.add(theme.checkbox(module.chatFeedback)).widget();
        cfC.action = () -> module.chatFeedback = cfC.checked;

        add(theme.horizontalSeparator()).expandX();

        // Bottom
        WHorizontalList bottom = add(theme.horizontalList()).expandX().widget();

        // Active
        bottom.add(theme.label("Active: "));
        active = bottom.add(theme.checkbox(module.isActive())).expandCellX().widget();
        active.action = () -> {
            if (module.isActive() != active.checked) module.toggle();
        };

        // Config sharing
        WHorizontalList sharing = bottom.add(theme.horizontalList()).right().widget();
        WButton copy = sharing.add(theme.button(GuiRenderer.COPY)).widget();
        copy.action = () -> {
            if (toClipboard()) {
                OkPrompt.create()
                    .title("Module copied!")
                    .message("The settings for this module are now in your clipboard.")
                    .message("You can also copy settings using Ctrl+C.")
                    .message("Settings can be imported using Ctrl+V or the paste button.")
                    .id("config-sharing-guide")
                    .show();
            }
        };
        copy.tooltip = "Copy config";

        WButton paste = sharing.add(theme.button(GuiRenderer.PASTE)).widget();
        paste.action = this::fromClipboard;
        paste.tooltip = "Paste config";

        add(theme.horizontalSeparator()).expandX();

        // Help Button
        WButton helpBtn = add(theme.button("Help (Huong Dan)")).expandX().widget();
        helpBtn.action = () -> {
            OkPrompt.create()
                .title("Huong Dan: " + module.title)
                .message(getVietnameseHelp(module))
                .id("module-help-" + module.name)
                .show();
        };
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !Modules.get().isBinding();
    }

    @Override
    public void tick() {
        super.tick();

        module.settings.tick(settingsContainer, theme);
    }

    @EventHandler
    private void onModuleBindChanged(ModuleBindChangedEvent event) {
        keybind.reset();
    }

    @EventHandler
    private void onActiveModulesChanged(ActiveModulesChangedEvent event) {
        this.active.checked = module.isActive();
    }

    @Override
    public boolean toClipboard() {
        NbtCompound tag = new NbtCompound();

        tag.putString("name", module.name);

        NbtCompound settingsTag = module.settings.toTag();
        if (!settingsTag.isEmpty()) tag.put("settings", settingsTag);

        return NbtUtils.toClipboard(tag);
    }

    @Override
    public boolean fromClipboard() {
        NbtCompound tag = NbtUtils.fromClipboard();
        if (tag == null) return false;
        if (!tag.getString("name", "").equals(module.name)) return false;

        Optional<NbtCompound> settings = tag.getCompound("settings");

        if (settings.isPresent()) module.settings.fromTag(settings.get());
        else module.settings.reset();

        if (parent instanceof WidgetScreen p) p.reload();
        reload();

        return true;
    }
}
