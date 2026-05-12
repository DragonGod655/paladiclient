/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.addons;

import paladi.client.PaladiClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import java.util.ArrayList;
import java.util.List;

public class AddonManager {
    public static final List<PaladiAddon> ADDONS = new ArrayList<>();

    public static void init() {
        // Paladi pseudo addon
        {
            PaladiClient.ADDON = new PaladiAddon() {
                @Override
                public void onInitialize() {}

                @Override
                public String getPackage() {
                    return "paladi.client";
                }

                @Override
                public String getWebsite() {
                    return "https://PaladiClient.com";
                }

                @Override
                public GithubRepo getRepo() {
                    return new GithubRepo("meteordevelopment", "paladi-client");
                }

                @Override
                public String getCommit() {
                    String commit = PaladiClient.MOD_META.getCustomValue(PaladiClient.MOD_ID + ":commit").getAsString();
                    return commit.isEmpty() ? null : commit;
                }
            };

            ModMetadata metadata = FabricLoader.getInstance().getModContainer(PaladiClient.MOD_ID).get().getMetadata();

            PaladiClient.ADDON.name = metadata.getName();
            PaladiClient.ADDON.authors = new String[metadata.getAuthors().size()];
            if (metadata.containsCustomValue(PaladiClient.MOD_ID + ":color")) {
                PaladiClient.ADDON.color.parse(metadata.getCustomValue(PaladiClient.MOD_ID + ":color").getAsString());
            }

            int i = 0;
            for (Person author : metadata.getAuthors()) {
                PaladiClient.ADDON.authors[i++] = author.getName();
            }

            ADDONS.add(PaladiClient.ADDON);
        }

        // Addons
        for (EntrypointContainer<PaladiAddon> entrypoint : FabricLoader.getInstance().getEntrypointContainers("Paladi", PaladiAddon.class)) {
            ModMetadata metadata = entrypoint.getProvider().getMetadata();
            PaladiAddon addon;
            try {
                addon = entrypoint.getEntrypoint();
            } catch (Throwable throwable) {
                throw new RuntimeException("Exception during addon init \"%s\".".formatted(metadata.getName()), throwable);
            }

            addon.name = metadata.getName();

            if (metadata.getAuthors().isEmpty()) throw new RuntimeException("Addon \"%s\" requires at least 1 author to be defined in it's fabric.mod.json. See https://fabricmc.net/wiki/documentation:fabric_mod_json_spec".formatted(addon.name));
            addon.authors = new String[metadata.getAuthors().size()];

            if (metadata.containsCustomValue(PaladiClient.MOD_ID + ":color")) {
                addon.color.parse(metadata.getCustomValue(PaladiClient.MOD_ID + ":color").getAsString());
            }

            int i = 0;
            for (Person author : metadata.getAuthors()) {
                addon.authors[i++] = author.getName();
            }

            ADDONS.add(addon);
        }
    }
}
