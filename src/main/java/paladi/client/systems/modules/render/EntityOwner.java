/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.render;

import paladi.client.events.render.Render2DEvent;
import paladi.client.renderer.Renderer2D;
import paladi.client.renderer.text.TextRenderer;
import paladi.client.settings.DoubleSetting;
import paladi.client.settings.Setting;
import paladi.client.settings.SettingGroup;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.Utils;
import paladi.client.utils.network.Http;
import paladi.client.utils.network.PaladiExecutor;
import paladi.client.utils.render.NametagUtils;
import paladi.client.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EntityOwner extends Module {
    private static final Color BACKGROUND = new Color(0, 0, 0, 75);
    private static final Color TEXT = new Color(255, 255, 255);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale of the text.")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Vector3d pos = new Vector3d();
    private final Map<UUID, String> uuidToName = new HashMap<>();

    public EntityOwner() {
        super(Categories.Render, "entity-owner", "Displays the name of the player who owns the entity you're looking at.");
    }

    @Override
    public void onDeactivate() {
        uuidToName.clear();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        for (Entity entity : mc.world.getEntities()) {
            @Nullable LazyEntityReference<LivingEntity> owner;

            if (entity instanceof TameableEntity tameable) owner = tameable.getOwnerReference();
            else if (entity instanceof EnderPearlEntity pearl) owner = LazyEntityReference.of((LivingEntity)pearl.getOwner());
            else continue;

            if (owner != null) {
                Utils.set(pos, entity, event.tickDelta);
                pos.add(0, entity.getEyeHeight(entity.getPose()) + 0.75, 0);

                if (NametagUtils.to2D(pos, scale.get())) {
                    renderNametag(getOwnerName(owner));
                }
            }
        }
    }

    private void renderNametag(String name) {
        TextRenderer text = TextRenderer.get();

        NametagUtils.begin(pos);
        text.beginBig();

        double w = text.getWidth(name);

        double x = -w / 2;
        double y = -text.getHeight();

        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(x - 1, y - 1, w + 2, text.getHeight() + 2, BACKGROUND);
        Renderer2D.COLOR.render();

        text.render(name, x, y, TEXT);

        text.end();
        NametagUtils.end();
    }

    private String getOwnerName(LazyEntityReference<LivingEntity> owner) {
        // Check if the player is online
        @Nullable LivingEntity ownerEntity = LazyEntityReference.resolve(owner, mc.world, LivingEntity.class);
        if (ownerEntity instanceof PlayerEntity playerEntity) return playerEntity.getName().getString();

        UUID uuid = owner.getUuid();

        // Check cache
        String name = uuidToName.get(uuid);
        if (name != null) return name;

        // Makes an HTTP request to Mojang API
        PaladiExecutor.execute(() -> {
            if (isActive()) {
                ProfileResponse res = Http.get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "")).sendJson(ProfileResponse.class);

                if (isActive()) {
                    if (res == null) uuidToName.put(uuid, "Failed to get name");
                    else uuidToName.put(uuid, res.name);
                }
            }
        });

        name = "Retrieving";
        uuidToName.put(uuid, name);
        return name;
    }

    private static class ProfileResponse {
        public String name;
    }
}
