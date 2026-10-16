package com.lucky.mod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tiny server-tick scheduler used to animate the VFX over time. */
public class Scheduler {
    private static class Task {
        int delay;
        final Runnable action;
        Task(int delay, Runnable action) { this.delay = delay; this.action = action; }
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> QUEUE = new ArrayList<>();

    public static void later(int ticks, Runnable action) {
        QUEUE.add(new Task(Math.max(1, ticks), action));
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TASKS.addAll(QUEUE);
            QUEUE.clear();
            Iterator<Task> it = TASKS.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (--t.delay <= 0) {
                    it.remove();
                    try {
                        t.action.run();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            try {
                s.getCommandManager().getDispatcher().execute("kill @e[tag=lbm_beam]", s.getCommandSource());
            } catch (Exception ignored) {
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            TASKS.clear();
            QUEUE.clear();
        });
    }
}
