package com.cardpricer.gui.panel;

import com.cardpricer.gui.AppIcon;
import com.cardpricer.gui.ResponsiveGrid;
import com.cardpricer.gui.WrapLayout;
import com.cardpricer.gui.dialog.CardSearchDialog;
import com.cardpricer.gui.panel.trade.TradeEntryPresenter;
import com.cardpricer.model.Card;
import com.cardpricer.model.ParsedCode;
import com.cardpricer.service.*;
import com.cardpricer.util.AppTheme;
import com.cardpricer.util.CardCodeParser;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.util.function.Function;

/** Standalone lookup using the same printing lookup and pricing rules as trade entry. */
public final class PriceCheckPanel extends JPanel {
    record Selection(Card card, String finish) {}
    private static final String[] FINISH_CODES={"","F","E","S"};
    private final JTextField inputField=new JTextField();
    private final JLabel nameLabel=new JLabel("Find a card to see its prices");
    private final JLabel printingLabel=AppTheme.mutedLabel("Set, collector number and finish");
    private final JLabel statusLabel=AppTheme.mutedLabel("Enter a code, or press F2 to search by name.");
    private final JLabel marketLabel=new JLabel("—");
    private final JLabel priceLabel=new JLabel("—");
    private final JLabel creditLabel=new JLabel("—");
    private final JLabel checkLabel=new JLabel("—");
    private final JComboBox<String> finishCombo=new JComboBox<>(new String[]{"Normal","Foil","Etched","Surge foil"});
    private final JComboBox<String> conditionCombo=new JComboBox<>(new String[]{"NM","LP","MP","HP","DMG"});
    private final TradeEntryPresenter lookup;
    private final BuyRateService rates;
    private final PricingService pricing=new PricingService();
    private final Function<String,Selection> nameSearch;
    private final Timer debounce=new Timer(450,e -> lookupCode());
    private Card currentCard;
    private boolean settingInput;
    private boolean submittedLookup;
    private String displayedInput;

    public PriceCheckPanel() { this(ScryfallCatalogService.getInstance(),new ScryfallApiService(),new BuyRateService(),null); }

    PriceCheckPanel(CardRepository catalog,ScryfallApiService api,BuyRateService rates,Function<String,Selection> nameSearch) {
        super(new BorderLayout(0,12));
        this.lookup=new TradeEntryPresenter(catalog,api);
        this.rates=rates;
        this.nameSearch=nameSearch==null ? text -> {
            CardSearchDialog dialog=new CardSearchDialog(SwingUtilities.getWindowAncestor(this),api);
            dialog.setInitialSearch(text);
            dialog.setVisible(true);
            return new Selection(dialog.getSelectedCard(),dialog.getSelectedFinish());
        } : nameSearch;
        setBackground(AppTheme.surface());
        setBorder(AppTheme.cardBorder(18));
        debounce.setRepeats(false);

        JPanel heading=AppTheme.transparent(new BorderLayout(8,0));
        JLabel title=new JLabel("Price check");title.setFont(AppTheme.FONT_HEADING);
        heading.add(title,BorderLayout.WEST);
        heading.add(AppTheme.mutedLabel("Card prices at a glance"),BorderLayout.EAST);
        JPanel entry=AppTheme.transparent(new BorderLayout(8,8));
        inputField.putClientProperty("JTextField.placeholderText","Set + number, e.g. TDM 3 or PLST ARB 1");
        inputField.putClientProperty("JTextField.leadingIcon",new AppIcon(AppIcon.Kind.SEARCH,16));
        inputField.setToolTipText("Enter a set and collector number; add F, E or S for finish. Enter checks and clears the line; F2 searches by name.");
        inputField.getAccessibleContext().setAccessibleName("Price check card code or name");
        inputField.addActionListener(e -> checkInput(true));
        entry.add(inputField,BorderLayout.CENTER);
        JButton check=AppTheme.primaryButton("Check price");check.addActionListener(e -> checkInput(false));
        JButton search=AppTheme.secondaryButton("Search name (F2)");search.addActionListener(e -> openNameSearch());
        JPanel buttons=AppTheme.transparent(new FlowLayout(FlowLayout.RIGHT,6,0));buttons.add(check);buttons.add(search);
        entry.add(buttons,BorderLayout.EAST);
        JPanel top=AppTheme.transparent(new BorderLayout(0,12));top.add(heading,BorderLayout.NORTH);top.add(entry,BorderLayout.CENTER);
        add(top,BorderLayout.NORTH);

        JPanel details=AppTheme.transparent(new BorderLayout(0,8));
        nameLabel.setFont(AppTheme.FONT_HEADING.deriveFont(16f));
        // Card names are plain text even when provider text starts with Swing's HTML marker.
        nameLabel.putClientProperty("html.disable",true);
        JPanel identity=AppTheme.transparent(new BorderLayout(0,3));identity.add(nameLabel,BorderLayout.NORTH);identity.add(printingLabel,BorderLayout.SOUTH);
        details.add(identity,BorderLayout.NORTH);
        JPanel options=AppTheme.transparent(new WrapLayout(FlowLayout.LEFT,8,0));
        options.add(new JLabel("Finish"));options.add(finishCombo);options.add(new JLabel("Condition"));options.add(conditionCombo);
        finishCombo.getAccessibleContext().setAccessibleName("Price check finish");
        conditionCombo.getAccessibleContext().setAccessibleName("Price check condition");
        finishCombo.addActionListener(e -> updatePrices());conditionCombo.addActionListener(e -> updatePrices());
        details.add(options,BorderLayout.CENTER);
        ResponsiveGrid prices=new ResponsiveGrid(4,125,10);
        prices.add(priceTile("Market (NM)",marketLabel));prices.add(priceTile("Store price",priceLabel));
        prices.add(priceTile("Store credit",creditLabel));prices.add(priceTile("Check offer",checkLabel));
        details.add(prices,BorderLayout.SOUTH);add(details,BorderLayout.CENTER);add(statusLabel,BorderLayout.SOUTH);
        clearPrices();

        inputField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { inputChanged(); }
            public void removeUpdate(DocumentEvent e) { inputChanged(); }
            public void changedUpdate(DocumentEvent e) { inputChanged(); }
        });
        var bindings=getInputMap(WHEN_IN_FOCUSED_WINDOW);
        bindings.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2,0),"searchPriceCheck");
        bindings.put(KeyStroke.getKeyStroke(KeyEvent.VK_F,InputEvent.CTRL_DOWN_MASK),"searchPriceCheck");
        getActionMap().put("searchPriceCheck",new AbstractAction() {
            public void actionPerformed(ActionEvent e) { openNameSearch(); }
        });
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED)!=0) {
                if (!isShowing()) cancelLookup();
                else if (currentCard!=null) updatePrices();
            }
        });
    }

    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE,getPreferredSize().height); }
    @Override public void removeNotify() { cancelLookup();super.removeNotify(); }

    private static JPanel priceTile(String title,JLabel amount) {
        JPanel panel=AppTheme.transparent(new BorderLayout(0,5));
        panel.add(AppTheme.mutedLabel(title),BorderLayout.NORTH);
        amount.setFont(AppTheme.FONT_HEADING.deriveFont(21f));panel.add(amount,BorderLayout.CENTER);
        return panel;
    }

    private void inputChanged() {
        if (settingInput) return;
        debounce.stop();
        if (!submittedLookup) lookup.cancel();
        String text=inputField.getText().trim();
        if (CardCodeParser.parse(text)!=null) { statusLabel.setText("Looking up card...");debounce.restart(); }
        else statusLabel.setText(text.isEmpty() ? "Enter a code, or press F2 to search by name." : "Press Enter or F2 to search by name.");
    }

    private void checkInput(boolean clearLine) {
        debounce.stop();
        if (inputField.getText().isBlank()) {inputField.requestFocusInWindow();return;}
        if (clearLine && currentCard!=null && inputField.getText().equals(displayedInput)) {
            cancelLookup();setInput("");inputField.requestFocusInWindow();return;
        }
        if (CardCodeParser.parse(inputField.getText().trim())==null) openNameSearch(clearLine);else lookupCode(clearLine);
    }

    private void lookupCode() { lookupCode(false); }

    private void lookupCode(boolean clearLine) {
        ParsedCode code=CardCodeParser.parse(inputField.getText().trim());
        if (code==null) return;
        cancelLookup();submittedLookup=clearLine;statusLabel.setText("Looking up card...");
        lookup.lookup(code,card -> {
            submittedLookup=false;
            if (clearLine) showCard(card,code.finish);else displayCard(card,code.finish);
        },failure -> {
            submittedLookup=false;
            if (currentCard==null) {
                clearPrices();nameLabel.setText("Card not found");printingLabel.setText("Check the code or use Search name (F2).");
            }
            statusLabel.setText("Lookup failed for "+CardCodeParser.format(code)+". "
                    +(currentCard==null ? "Check the code and try again." : "Showing the last card's prices."));
        });
        if (clearLine) {setInput("");inputField.requestFocusInWindow();}
    }

    private void openNameSearch() { openNameSearch(false); }

    private void openNameSearch(boolean clearLine) {
        cancelLookup();
        String text=inputField.getText().trim();
        Selection selected=nameSearch.apply(CardCodeParser.parse(text)==null ? text : "");
        if (selected!=null && selected.card()!=null) {
            displayCard(selected.card(),selected.finish()==null ? "" : selected.finish());
            if (clearLine) setInput("");
        }
        else if (currentCard==null) statusLabel.setText("Select a card or enter a code to check its price.");
        inputField.requestFocusInWindow();
    }

    void displayCard(Card card,String finish) {
        cancelLookup();
        setInput(CardCodeParser.format(new ParsedCode(card.getSetCode(),card.getCollectorNumber(),finish)));
        showCard(card,finish);
        displayedInput=inputField.getText();
    }

    private void setInput(String text) {
        settingInput=true;
        try {inputField.setText(text);}
        finally {settingInput=false;}
    }

    private void showCard(Card card,String finish) {
        displayedInput=null;
        currentCard=card;
        nameLabel.setText(card.getName());nameLabel.setToolTipText(card.getName());
        printingLabel.setText(card.getSetCode().toUpperCase(java.util.Locale.ROOT)+" #"+card.getCollectorNumber()
                +"  •  "+(card.getRarity()==null ? "" : card.getRarity()));
        for(int i=0;i<FINISH_CODES.length;i++)if(FINISH_CODES[i].equalsIgnoreCase(finish))finishCombo.setSelectedIndex(i);
        finishCombo.setEnabled(true);conditionCombo.setEnabled(true);updatePrices();
        scrollRectToVisible(new Rectangle(0,0,getWidth(),getHeight()));
    }

    private void updatePrices() {
        if (currentCard==null) return;
        String finish=FINISH_CODES[finishCombo.getSelectedIndex()];
        if (!TradeEntryPresenter.hasPrice(currentCard,finish)) {
            for(var label:new JLabel[]{marketLabel,priceLabel,creditLabel,checkLabel})label.setText("N/A");
            statusLabel.setText("No price available for this finish. Select another finish.");return;
        }
        BigDecimal market=switch(finish) {
            case "E" -> currentCard.getEtchedPriceAsBigDecimal();
            case "F","S" -> currentCard.getFoilPriceAsBigDecimal();
            default -> currentCard.getPriceAsBigDecimal();
        };
        BigDecimal value=pricing.applyConditionMultiplier(pricing.applyPricingRules(market,currentCard.getRarity()),
                (String)conditionCombo.getSelectedItem());
        rates.refreshFromPublished();
        var offer=rates.computePayout(currentCard.getSetCode(),currentCard.getCollectorNumber(),currentCard.getName(),value);
        marketLabel.setText(money(market));priceLabel.setText(money(value));creditLabel.setText(money(offer.creditPayout()));checkLabel.setText(money(offer.checkPayout()));
        statusLabel.setText("Per card • "+conditionCombo.getSelectedItem()+" • "+finishCombo.getSelectedItem());
    }

    private static String money(BigDecimal value) { return String.format(java.util.Locale.ROOT,"$%.2f",value); }
    private void clearPrices() {
        for(var label:new JLabel[]{marketLabel,priceLabel,creditLabel,checkLabel})label.setText("—");
        finishCombo.setEnabled(false);conditionCombo.setEnabled(false);
    }
    private void cancelLookup() {debounce.stop();lookup.cancel();submittedLookup=false;}
}
