package com.cardpricer.gui.panel.trade;
import com.cardpricer.model.Card;
import com.cardpricer.model.ParsedCode;
import com.cardpricer.service.*;
import javax.swing.SwingWorker;
import java.util.function.Consumer;

/** Owns lookup concurrency and request lifetime; only the current request can update its view. */
public final class TradeEntryPresenter implements AutoCloseable {
    private final CardRepository catalog;
    private final ScryfallApiService api;
    private SwingWorker<Card,Void> worker;
    private long generation;
    public TradeEntryPresenter(CardRepository catalog,ScryfallApiService api) {this.catalog=catalog;this.api=api;}
    public void lookup(ParsedCode code,Consumer<Card> success,Consumer<Throwable> failure) {
        cancel();long request=generation;
        worker=new SwingWorker<>() {
            protected Card doInBackground() throws Exception {
                var cached=catalog.lookup(code.setCode,code.collectorNumber);
                if (cached.isPresent() && hasPrice(cached.get(),code.finish)) return cached.get();
                return api.fetchCard(code.setCode,code.collectorNumber);
            }
            protected void done() {
                if (isCancelled() || request!=generation) return;
                try {success.accept(get());}
                catch (java.util.concurrent.ExecutionException e) {failure.accept(e.getCause());}
                catch (InterruptedException e) {Thread.currentThread().interrupt();}
            }
        };
        TaskCoordinator.execute(worker);
    }
    public static boolean hasPrice(Card card,String finish) {
        return switch(finish) {case "E" -> card.hasEtchedPrice();case "F","S" -> card.hasFoilPrice();default -> card.hasNormalPrice();};
    }
    public void cancel() {generation++;if(worker!=null)worker.cancel(true);}
    public void close() {cancel();}
}
