/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.network;

public class OnlinePlayers {
    private static long lastPingTime;

    private OnlinePlayers() {
    }

    public static void update() {
        long time = System.currentTimeMillis();

        if (time - lastPingTime > 5 * 60 * 1000) {
            PaladiExecutor.execute(() -> Http.post("https://PaladiClient.com/api/online/ping").ignoreExceptions().send());

            lastPingTime = time;
        }
    }

    public static void leave() {
        PaladiExecutor.execute(() -> Http.post("https://PaladiClient.com/api/online/leave").ignoreExceptions().send());
    }
}
