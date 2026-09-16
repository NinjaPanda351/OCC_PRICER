package com.cardpricer.service;

import com.cardpricer.gui.panel.trade.TradeEntryPresenter;
import com.cardpricer.model.Card;
import com.cardpricer.util.CardCodeParser;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TradeEntryPresenterTest {
    @Test void supersededAndDisposedRequestsCannotUpdateTheView() throws Exception {
        CountDownLatch firstStarted=new CountDownLatch(1),releaseFirst=new CountDownLatch(1),secondDone=new CountDownLatch(1);
        AtomicReference<String> shown=new AtomicReference<>();
        CardRepository cards=(set,number)->{
            if(number.equals("1")) {
                firstStarted.countDown();
                boolean released=false;
                while(!released)try{released=releaseFirst.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
            }
            Card card=new Card(number,set,number);card.setPrice("1.00");return Optional.of(card);
        };
        var presenter=new TradeEntryPresenter(cards,new ScryfallApiService());
        try {
            SwingUtilities.invokeAndWait(()->presenter.lookup(CardCodeParser.parse("TST 1"),c->shown.set("stale"),e->shown.set("error")));
            assertTrue(firstStarted.await(3,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->presenter.lookup(CardCodeParser.parse("TST 2"),c->{shown.set(c.getName());secondDone.countDown();},e->secondDone.countDown()));
            assertTrue(secondDone.await(3,TimeUnit.SECONDS));assertEquals("2",shown.get());
            releaseFirst.countDown();
            SwingUtilities.invokeAndWait(()->{
                presenter.lookup(CardCodeParser.parse("TST 3"),c->shown.set("disposed"),e->shown.set("error"));
                presenter.close();
            });
            // Queue a barrier after SwingWorker's deferred done delivery.
            CountDownLatch delivered=new CountDownLatch(1);
            SwingUtilities.invokeAndWait(()->{var timer=new javax.swing.Timer(150,e->delivered.countDown());timer.setRepeats(false);timer.start();});
            assertTrue(delivered.await(2,TimeUnit.SECONDS));assertEquals("2",shown.get());
        } finally {releaseFirst.countDown();SwingUtilities.invokeAndWait(presenter::close);}
    }
}
