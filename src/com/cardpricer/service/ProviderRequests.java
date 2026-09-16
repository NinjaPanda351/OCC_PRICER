package com.cardpricer.service;
import com.cardpricer.util.AppVersion;
import java.io.IOException;
import java.net.*;
/** Shared conservative request policy. All API screens and catalog metadata use this limiter. */
public final class ProviderRequests {
    private ProviderRequests() {}
    private static long nextRequest;
    public static synchronized void acquire() throws InterruptedException {
        long delay=nextRequest-System.nanoTime();
        if (delay>0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(delay);
        nextRequest=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(500);
    }
    public static HttpURLConnection open(String url) throws IOException {
        try { acquire(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException("Request cancelled"); }
        HttpURLConnection connection=(HttpURLConnection)URI.create(url).toURL().openConnection();
        connection.setRequestProperty("User-Agent","OCC-Trade-Pricer/"+AppVersion.CURRENT+" (+https://github.com/NinjaPanda351/OCC_PRICER)");
        connection.setRequestProperty("Accept","application/json;q=0.9,*/*;q=0.8");
        connection.setConnectTimeout(10_000); connection.setReadTimeout(30_000);
        return connection;
    }
}
