package com.cardpricer.gui.panel;

import com.cardpricer.model.Card;
import com.cardpricer.model.BuyRateRule;
import com.cardpricer.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PriceCheckPanelTest {
    @TempDir Path temp;
    private static Card card() {
        Card card=new Card("Example card","TST","1");card.setRarity("rare");
        card.setPrice("10.00");card.setFoilPrice("20.00");card.setEtchedPrice("30.00");return card;
    }
    private static <T> T field(PriceCheckPanel panel,String name,Class<T> type) {
        try {var field=PriceCheckPanel.class.getDeclaredField(name);field.setAccessible(true);return type.cast(field.get(panel));}
        catch(Exception e){throw new RuntimeException(e);}
    }
    private static String text(PriceCheckPanel panel,String field) {return field(panel,field,JLabel.class).getText();}

    @Test void f2AndControlFUseNameSearchAndSelectedFinishAndConditionDrivePrices() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var seen=new AtomicReference<String>();
            var panel=new PriceCheckPanel((set,number)->Optional.empty(),new ScryfallApiService(),new BuyRateService(temp.resolve("rates.json")),query->{
                seen.set(query);return new PriceCheckPanel.Selection(card(),"F");
            });
            try {
                var input=field(panel,"inputField",JTextField.class);input.setText("Example card");
                var bindings=panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
                var action=bindings.get(KeyStroke.getKeyStroke(KeyEvent.VK_F2,0));
                assertEquals(action,bindings.get(KeyStroke.getKeyStroke(KeyEvent.VK_F,InputEvent.CTRL_DOWN_MASK)));
                panel.getActionMap().get(action).actionPerformed(new ActionEvent(panel,0,"F2"));
                assertEquals("Example card",seen.get());assertEquals("TST 1f",input.getText());
                assertEquals("$20.00",text(panel,"marketLabel"));assertEquals("$20.00",text(panel,"priceLabel"));
                field(panel,"conditionCombo",JComboBox.class).setSelectedItem("LP");
                assertEquals("$20.00",text(panel,"marketLabel"));assertEquals("$16.00",text(panel,"priceLabel"));
                assertEquals("$8.00",text(panel,"creditLabel"));assertEquals("$6.40",text(panel,"checkLabel"));
            } finally {panel.removeNotify();}
        });
    }

    @Test void unavailableFinishDoesNotFallBackToAnotherPriceAndUpdatedRatesApply() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var rates=new BuyRateService(temp.resolve("rates.json"));
            var panel=new PriceCheckPanel((set,number)->Optional.empty(),new ScryfallApiService(),rates,query->null);
            try {
                var card=card();card.setFoilPrice("N/A");panel.displayCard(card,"F");
                assertEquals("N/A",text(panel,"priceLabel"));assertEquals("N/A",text(panel,"creditLabel"));
                new BuyRateService(temp.resolve("rates.json")).saveRules(List.of(
                        new BuyRateRule(BigDecimal.ZERO,new BigDecimal("0.80"),new BigDecimal("0.60"))));
                field(panel,"finishCombo",JComboBox.class).setSelectedItem("Normal");
                assertEquals("$10.00",text(panel,"priceLabel"));assertEquals("$8.00",text(panel,"creditLabel"));
                assertEquals("$6.00",text(panel,"checkLabel"));
                field(panel,"inputField",JTextField.class).setText("");
                assertEquals("$10.00",text(panel,"priceLabel"));
                assertTrue(field(panel,"finishCombo",JComboBox.class).isEnabled());
            } finally {panel.removeNotify();}
        });
    }

    @Test void enteringAListCodeUsesExactPrintingAndFinishFromTheTradeLookup() throws Exception {
        var seen=new AtomicReference<String>();var reference=new AtomicReference<PriceCheckPanel>();
        SwingUtilities.invokeAndWait(()->{
            var panel=new PriceCheckPanel((set,number)->{
                seen.set(set.toUpperCase(Locale.ROOT)+" "+number);
                var card=card();card.setSetCode("PLST");card.setCollectorNumber("ARB-73");return Optional.of(card);
            },new ScryfallApiService(),new BuyRateService(temp.resolve("rates.json")),query->null);
            reference.set(panel);var input=field(panel,"inputField",JTextField.class);
            input.setText("PLST ARB 73e");input.postActionEvent();
        });
        try {
            waitFor(()->"$30.00".equals(text(reference.get(),"marketLabel")));
            assertEquals("PLST ARB-73",seen.get());
            SwingUtilities.invokeAndWait(()->{
                assertEquals("Etched",field(reference.get(),"finishCombo",JComboBox.class).getSelectedItem());
                assertEquals("",field(reference.get(),"inputField",JTextField.class).getText());
            });
        } finally {SwingUtilities.invokeAndWait(()->reference.get().removeNotify());}
    }

    @Test void changingInputCancelsLookupBeforeItsOldResultCanReappear() throws Exception {
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var reference=new AtomicReference<PriceCheckPanel>();
        SwingUtilities.invokeAndWait(()->{
            var panel=new PriceCheckPanel((set,number)->{
                started.countDown();
                boolean done=false;while(!done)try{done=release.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
                finished.countDown();return Optional.of(card());
            },new ScryfallApiService(),new BuyRateService(temp.resolve("rates.json")),query->null);
            reference.set(panel);var input=field(panel,"inputField",JTextField.class);input.setText("TST 1");
            var debounce=field(panel,"debounce",javax.swing.Timer.class);debounce.stop();
            for(var listener:debounce.getActionListeners())listener.actionPerformed(new ActionEvent(debounce,0,"preview"));
        });
        try {
            assertTrue(started.await(3,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->field(reference.get(),"inputField",JTextField.class).setText("A different name"));
            release.countDown();assertTrue(finished.await(3,TimeUnit.SECONDS));
            var delivered=new CountDownLatch(1);
            SwingUtilities.invokeAndWait(()->{var timer=new javax.swing.Timer(150,e->delivered.countDown());timer.setRepeats(false);timer.start();});
            assertTrue(delivered.await(3,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->{
                assertEquals("—",text(reference.get(),"priceLabel"));
                assertEquals("Find a card to see its prices",text(reference.get(),"nameLabel"));
            });
        } finally {release.countDown();SwingUtilities.invokeAndWait(()->reference.get().removeNotify());}
    }

    @Test void enterKeepsSelectedCardPricesAndFinishWhilePreparingTheNextEntry() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=new PriceCheckPanel((set,number)->{throw new AssertionError("Displayed card should not be fetched again");},
                    new ScryfallApiService(),new BuyRateService(temp.resolve("rates.json")),
                    query->new PriceCheckPanel.Selection(card(),"F"));
            try {
                var input=field(panel,"inputField",JTextField.class);
                input.setText("Example card");input.postActionEvent();
                assertEquals("",input.getText());
                assertEquals("$20.00",text(panel,"marketLabel"));
                panel.displayCard(card(),"F");
                field(panel,"conditionCombo",JComboBox.class).setSelectedItem("LP");
                input.postActionEvent();
                assertEquals("",input.getText());
                input.setText("Next card name");
                assertEquals("Example card",text(panel,"nameLabel"));
                assertEquals("$20.00",text(panel,"marketLabel"));
                assertEquals("$16.00",text(panel,"priceLabel"));
                assertEquals("Foil",field(panel,"finishCombo",JComboBox.class).getSelectedItem());
            } finally {panel.removeNotify();}
        });
    }

    @Test void submittedLookupFinishesWithoutOverwritingTheNextTypedEntry() throws Exception {
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        var reference=new AtomicReference<PriceCheckPanel>();
        SwingUtilities.invokeAndWait(()->{
            var panel=new PriceCheckPanel((set,number)->{
                started.countDown();
                try {release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                return Optional.of(card());
            },new ScryfallApiService(),new BuyRateService(temp.resolve("rates.json")),query->null);
            reference.set(panel);
            var input=field(panel,"inputField",JTextField.class);input.setText("TST 1");input.postActionEvent();
            assertEquals("",input.getText());
        });
        try {
            assertTrue(started.await(3,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->field(reference.get(),"inputField",JTextField.class).setText("Next card name"));
            release.countDown();
            waitFor(()->"$10.00".equals(text(reference.get(),"marketLabel")));
            SwingUtilities.invokeAndWait(()->{
                assertEquals("Next card name",field(reference.get(),"inputField",JTextField.class).getText());
                assertEquals("Example card",text(reference.get(),"nameLabel"));
            });
        } finally {release.countDown();SwingUtilities.invokeAndWait(()->reference.get().removeNotify());}
    }

    private static void waitFor(java.util.function.BooleanSupplier predicate) throws Exception {
        for(int i=0;i<100;i++) {
            boolean[] done={false};SwingUtilities.invokeAndWait(()->done[0]=predicate.getAsBoolean());
            if(done[0])return;Thread.sleep(25);
        }
        fail("Price-check lookup did not complete");
    }
}
