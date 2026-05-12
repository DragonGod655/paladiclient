/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.misc;

import paladi.client.PaladiClient;
import paladi.client.events.world.TickEvent;
import paladi.client.utils.PreInit;
import meteordevelopment.orbit.EventHandler;


public class CPSUtils {
    private static int clicks;
    private static int cps;
    private static int secondsClicking;
    private static long lastTime;

    private CPSUtils() {
    }

    @PreInit
    public static void init() {
        PaladiClient.EVENT_BUS.subscribe(CPSUtils.class);
    }

    @EventHandler
    private static void onTick(TickEvent.Pre event) {
        long currentTime = System.currentTimeMillis();
        // Run every second
        if (currentTime - CPSUtils.lastTime >= 1000) {
            if (CPSUtils.cps == 0) {
                CPSUtils.clicks = 0;
                CPSUtils.secondsClicking = 0;
            } else {
                CPSUtils.lastTime = currentTime;
                CPSUtils.secondsClicking++;
                CPSUtils.cps = 0;
            }
        }
    }


    public static void onAttack() {
        CPSUtils.clicks++;
        CPSUtils.cps++;
    }

    public static int getCpsAverage() {
        return clicks / (secondsClicking == 0 ? 1 : secondsClicking);
    }
}
