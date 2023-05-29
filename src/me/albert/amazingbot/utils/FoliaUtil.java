package me.albert.amazingbot.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FoliaUtil {
    public static boolean folia = isFolia();

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            Class.forName("io.papermc.paper.threadedregions.RegionizedServerInitEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static Object asyncScheduler;
    private static Object globalRegionScheduler;
    private static Method runNowAsync;
    private static Method runDelayedAsync;
    private static Method runDelayed;
    private static Method run;
    private static Method cancel;
    private static Class<?> scheduledTask;

    static {
        // init reflect for folia
        if (folia) {
            try {
                // folia scheduler
                String asyncSchedulerName = "io.papermc.paper.threadedregions.scheduler.AsyncScheduler";
                String globalRegionSchedulerName = "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler";
                String scheduledTaskName = "io.papermc.paper.threadedregions.scheduler.ScheduledTask";

                Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
                Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");

                scheduledTask = Class.forName(scheduledTaskName);

                asyncScheduler = getAsyncScheduler.invoke(Bukkit.class);
                globalRegionScheduler = getGlobalRegionScheduler.invoke(Bukkit.class);

                runNowAsync = Class.forName(asyncSchedulerName).getMethod("runNow", Plugin.class, Consumer.class);
                runDelayedAsync = Class.forName(asyncSchedulerName).getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);

                runDelayed = Class.forName(globalRegionSchedulerName).getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
                run = Class.forName(globalRegionSchedulerName).getMethod("run", Plugin.class, Consumer.class);
                cancel = scheduledTask.getMethod("cancel");

            } catch (NoSuchMethodException | ClassNotFoundException | IllegalAccessException |
                     InvocationTargetException e) {
                e.printStackTrace();
                folia = false;
            }
        }
    }

    // for all codes that use org.bukkit.scheduler.BukkitScheduler#runTask
    public static Task runTask(Plugin plugin, Runnable runnable) {
        Object task;
        if (folia) {
            try {
                task = run.invoke(globalRegionScheduler, plugin, (Consumer<?>) t -> runnable.run());
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else {
            task = Bukkit.getScheduler().runTask(plugin, runnable);
        }
        return new Task(task);
    }

    // for all codes that use org.bukkit.scheduler.BukkitScheduler#runTaskLater
    public static Task runTaskLater(Plugin plugin, Runnable runnable, long l) {
        Object task;
        if (folia) {
            try {
                task = runDelayed.invoke(globalRegionScheduler, plugin, (Consumer<?>) t -> runnable.run(), l);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else {
            task = Bukkit.getScheduler().runTaskLater(plugin, runnable, l);
        }
        return new Task(task);
    }

    /// for all codes that use org.bukkit.scheduler.BukkitScheduler#runTaskAsynchronously
    public static Task runTaskAsync(Plugin plugin, Runnable runnable) {
        Object task;
        if (folia) {
            try {
                task = runNowAsync.invoke(asyncScheduler, plugin, (Consumer<?>) t -> runnable.run());
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else {
            task = Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
        return new Task(task);
    }

    /// for all codes that use org.bukkit.scheduler.BukkitScheduler#runTaskAsynchronously
    public static Task runTaskLaterAsync(Plugin plugin, Runnable runnable, long l) {
        Object task;
        if (folia) {
            try {
                task = runDelayedAsync.invoke(asyncScheduler, plugin, (Consumer<?>) t -> runnable.run(), l * 50, TimeUnit.MILLISECONDS);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else {
            task = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, l);
        }
        return new Task(task);
    }

    public static class Task{
        Object task;
        public Task(Object task){
            try {
                if (!(task instanceof BukkitTask)) scheduledTask.cast(task);
                this.task = task;
            } catch (ClassCastException e){
                this.task = null;
                throw new IllegalArgumentException("The task passed in is neither a BukkitTask nor a ScheduledTask");
            }

        }

        public void cancel() {
            if (task == null) return;
            if (task instanceof BukkitTask){
                ((BukkitTask) task).cancel();
            } else {
                try {
                    cancel.invoke(scheduledTask.cast(task));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
