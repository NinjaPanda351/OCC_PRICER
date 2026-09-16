package com.cardpricer.service;
import java.util.concurrent.*;
/** Bounded application I/O executor; workers are daemon threads and callbacks stay on Swing. */
public final class TaskCoordinator {
    private TaskCoordinator() {}
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), r -> { Thread t=new Thread(r,"application-io"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());
    public static Future<?> submit(Runnable operation) { return IO.submit(operation); }
    public static void execute(javax.swing.SwingWorker<?,?> worker) {
        IO.getQueue().removeIf(task -> task instanceof Future<?> future && future.isCancelled());
        IO.execute(worker);
    }
}
