/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.network;

import paladi.client.utils.PreInit;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class PaladiExecutor {
    public static ExecutorService executor;

    private PaladiExecutor() {
    }

    @PreInit
    public static void init() {
        AtomicInteger threadNumber = new AtomicInteger(1);

        executor = Executors.newCachedThreadPool((task) -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.setName("Paladi-Executor-" + threadNumber.getAndIncrement());
            return thread;
        });
    }

    public static void execute(Runnable task) {
        executor.execute(task);
    }
}
