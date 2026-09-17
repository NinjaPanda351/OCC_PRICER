import com.cardpricer.model.*;
import com.cardpricer.service.*;
import com.cardpricer.gui.panel.TradePanel;
import com.cardpricer.gui.panel.FileManagerPanel;
import com.cardpricer.gui.panel.trade.PaymentTypePanel;
import com.cardpricer.util.*;
import org.json.JSONObject;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.prefs.*;

// Baseline defect reproductions, not regression tests for repaired behavior.
// All files live in a unique audit sandbox. Preferences are in-memory.
// No email, browser, printer, or live API calls are made.
public class AuditProbes {
    static int confirmed;
    static BigDecimal bd(String s) { return new BigDecimal(s); }
    static void confirm(boolean condition, String message) {
        if (!condition) throw new AssertionError("NOT REPRODUCED: " + message);
        confirmed++;
        System.out.println("CONFIRMED " + confirmed + ": " + message);
    }
    static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> clazz = target instanceof Class<?> c ? c : target.getClass();
        Method m = clazz.getDeclaredMethod(name, types); m.setAccessible(true);
        return m.invoke(target instanceof Class<?> ? null : target, args);
    }
    static Card card(String name, String price) {
        Card c = new Card(); c.setName(name); c.setSetCode("TST"); c.setCollectorNumber("1");
        c.setRarity("rare"); c.setPrice(price); c.setFoilPrice(price); c.setEtchedPrice(price);
        c.setArtist("Audit Artist"); c.setImageUrl("audit://image");
        return c;
    }
    static JSONObject json(String set, String coll, String name) {
        return new JSONObject().put("set",set).put("collector_number",coll).put("name",name)
            .put("rarity","rare").put("artist","Audit Artist")
            .put("prices",new JSONObject().put("usd","100.00").put("usd_foil","100.00").put("usd_etched","100.00"));
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            Path sandbox = Files.createTempDirectory(Path.of("out"), "probe-data-").toAbsolutePath();
            ProcessBuilder child = new ProcessBuilder(
                Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                "-Djava.awt.headless=true", "-Djava.util.prefs.PreferencesFactory=AuditProbes$MemoryFactory",
                "-cp", System.getProperty("java.class.path"), "AuditProbes", "isolated");
            child.environment().put("APPDATA", sandbox.toString());
            System.out.println("Isolated audit data: " + sandbox);
            System.exit(child.inheritIO().start().waitFor());
        }
        try {
            Locale.setDefault(Locale.US);
            Card c = card("Audit Card","100.00");
            Preferences legacyPrefs = Preferences.userNodeForPackage(com.cardpricer.gui.panel.PreferencesPanel.class);
            legacyPrefs.put("buy.rate.rules","[{\"thresholdMin\":\"0\",\"creditRate\":\"0.9\",\"checkRate\":\"0.7\"}]");
            legacyPrefs.put("buy.rate.bounties","[{\"cardName\":\"Legacy Bounty\",\"creditRate\":\"0.9\",\"checkRate\":\"0.7\"}]");
            BuyRateService rates = new BuyRateService();
            confirm(rates.getBounties().isEmpty() && rates.computePayout("TST","1","Legacy Bounty",bd("100")).checkPayout().equals(bd("40.00")),
                "Legacy preferences with 90/70 rules and a bounty are ignored when local JSON is absent");
            TradeReceivingExportService exporter = new TradeReceivingExportService();
            String csv = exporter.exportToPOSFormat(List.of(new TradeItem(c,false)), "Audit", "Schema",
                List.of(bd("100")),List.of(1),"check");
            List<String> lines = Files.readAllLines(Path.of(csv));
            String[] headers=lines.get(0).split(",",-1), row=lines.get(1).split(",",-1);
            confirm(headers.length == 19 && row.length == 18,
                "POS header has 19 columns, row has 18; COST value lands under QTY ON HAND");
            confirm(row[12].equals("33.33") && rates.computePayout("TST","1","Audit Card",bd("100")).checkPayout().equals(bd("40.00")),
                "$100 default check: UI/service payout $40.00; export cost $33.33");
            rates.saveBounties(List.of(new BountyCard("Audit Card",bd("0.8"),bd("0.6"))));
            csv=exporter.exportToPOSFormat(List.of(new TradeItem(c,false)), "Audit", "Bounty",
                List.of(bd("100")),List.of(1),"credit");
            row=Files.readAllLines(Path.of(csv)).get(1).split(",",-1);
            confirm(row[12].equals("50.00") && rates.computePayout("TST","1","Audit Card",bd("100")).creditPayout().equals(bd("80.00")),
                "$100 bounty credit: service payout $80.00; export cost $50.00");
            csv=exporter.exportToPOSFormat(List.of(new TradeItem(c,true,1,"E")), "Audit", "Finish",
                List.of(bd("100")),List.of(1),"credit");
            row=Files.readAllLines(Path.of(csv)).get(1).split(",",-1);
            confirm(row[4].equals("TST 1F") && row[7].contains("(Foil)"),
                "Etched trade exports as generic F / Foil");
            confirm(!new CardEntry(c,true).isFoil(), "CardEntry creates lowercase f, isFoil checks uppercase F");

            CsvExportService csvService = new CsvExportService();
            csvService.exportCardsToCsv(List.of(c),"zero.csv",CsvExportService.ExportFormat.ITEM_WIZARD_CHANGE_QTY_ZERO);
            String zeroRow=Files.readAllLines(AppDataDirectory.prices().toPath().resolve("zero.csv")).get(0);
            confirm(zeroRow.split(",",-1).length==9 && new CardEntry(c,false).toZeroOutItems().split(",",-1).length==5,
                "Per-set zero-quantity export uses 9-column price format; combined path uses 5-column quantity format");
            Card etchedOnly=card("Etched Only","5"); etchedOnly.setPrice(null); etchedOnly.setFoilPrice(null);
            csvService.exportCardsToCsv(List.of(etchedOnly),"etched-only.csv");
            confirm(Files.readAllLines(AppDataDirectory.prices().toPath().resolve("etched-only.csv")).size()==1,
                "Etched-only card omitted from set price export");

            Path bountyCsv=AppDataDirectory.root().toPath().resolve("quoted-bounty.csv");
            Files.writeString(bountyCsv,"CARD NAME,CREDIT PERCENT,CHECK PERCENT\n\"Name, With Comma\",80,60\n");
            boolean rejected=false;
            try { rates.parseBountyCsv(bountyCsv.toFile()); } catch(IOException expected) { rejected=true; }
            confirm(rejected,"Valid quoted bounty CSV name with comma is rejected");
            BountyCard invalid=new BountyCard("Invalid",bd("-0.5"),bd("2"));
            confirm(invalid.creditRate.signum()<0 && invalid.checkRate.compareTo(BigDecimal.ONE)>0,
                "BountyCard accepts negative and over-100-percent payout rates");
            confirm(CardCodeParser.parse("CHR143B")==null, "Documented compact code CHR143B returns null");
            ParsedCode nameAsCode=CardCodeParser.parse("Black Lotus");
            confirm(nameAsCode!=null && nameAsCode.setCode.equals("black") && nameAsCode.collectorNumber.equals("lotu\u2605"),
                "Quick-check input Black Lotus is classified as a set/collector code; name lookup branch is bypassed");
            Card mapped=new ScryfallApiService().parseCardFromJson(json("chk","1","Mapping Card"));
            confirm(mapped.getSetCode().equals("CHK") && SetList.fromScryfallCode("chk").equals("COK"),
                "API parser emits CHK despite the declared legacy output mapping CHK to COK");
            PricingService pricing=new PricingService();
            confirm(pricing.applyPricingRules(bd("0.1"),"common").compareTo(bd("0.1"))==0 &&
                pricing.applyConditionMultiplier(bd("0.1"),"NM").signum()==0,
                "Applying NM condition turns the common minimum $0.10 into $0.00");
            BigDecimal rawOffer=rates.computePayout("TST","1","Offer Card",bd("20.26")).creditPayout();
            BigDecimal tradeOffer=rates.computePayout("TST","1","Offer Card",pricing.applyPricingRules(bd("20.26"),"rare")).creditPayout();
            confirm(rawOffer.equals(bd("10.13")) && tradeOffer.equals(bd("10.00")),
                "Quick-check raw-price offer is $10.13; trade rounding changes the same offer to $10.00");
            confirm((boolean)call(UpdateCheckService.class,"isNewer",new Class<?>[]{String.class,String.class},"4.2.0","4.1.6-1")==false,
                "Updater fails to detect 4.2.0 as newer than 4.1.6-1");

            Map<String,Card> index=new HashMap<>();
            StringWriter cache=new StringWriter();
            Method process=ScryfallCatalogService.class.getDeclaredMethod("processCardJson",JSONObject.class,Map.class,PrintWriter.class);
            process.setAccessible(true);
            process.invoke(null,json("tst","73","Normal Printing"),index,new PrintWriter(cache));
            process.invoke(null,json("tst","73\u2605","Surge Printing"),index,new PrintWriter(cache));
            confirm(index.size()==1 && index.get("TST:73").getName().equals("Surge Printing"),
                "Catalog merges collector numbers 73 and 73-star into one key; last printing wins");
            confirm(index.get("TST:73").getArtist()==null, "Catalog drops artist present in source JSON");
            ScryfallCatalogService catalog=ScryfallCatalogService.getInstance();
            set(catalog,"index",Collections.unmodifiableMap(index));
            catalog.lookup("TST","73").orElseThrow().setPrice("999");
            confirm(catalog.lookup("TST","73").orElseThrow().getPrice().equals("999"),
                "Catalog lookup exposes a mutable shared Card; price edits alter future lookups");
            JSONObject noArtist=json("tst","1","No Artist"); noArtist.remove("artist");
            boolean apiRejected=false;
            try { new ScryfallApiService().parseCardFromJson(noArtist); } catch(RuntimeException expected) { apiRejected=true; }
            process.invoke(null,noArtist,index,new PrintWriter(cache));
            confirm(apiRejected && index.containsKey("TST:1"),
                "API and catalog parsers disagree: missing artist aborts API parser but catalog accepts card");

            SwingUtilities.invokeAndWait(()-> {
                try {
                    PaymentTypePanel payment=new PaymentTypePanel(()->{});
                    payment.setTotal(bd("100"),bd("50"),bd("40"));
                    ((JRadioButton)field(payment,"partialRadio")).doClick();
                    ((JTextField)field(payment,"partialCreditField")).setText("25.00");
                    ((JTextField)field(payment,"partialCheckField")).setText("20.00");
                    payment.setTotal(bd("100"),bd("50"),bd("40"));
                    confirm(payment.getPartialCreditPayout().signum()==0 && payment.getPartialCheckPayout().signum()==0,
                        "Summary refresh resets an entered split from 25/20 to 0/0");
                    ((JTextField)field(payment,"partialCreditField")).setText("-10");
                    ((JTextField)field(payment,"partialCheckField")).setText("48");
                    BigDecimal covered=payment.getPartialCreditPayout().multiply(bd("100")).divide(bd("50"))
                        .add(payment.getPartialCheckPayout().multiply(bd("100")).divide(bd("40")));
                    confirm(covered.equals(bd("100")),
                        "Negative split (-10 credit, 48 check) passes the current $100 value equation");

                    TradePanel panel=new TradePanel();
                    ((javax.swing.Timer)field(panel,"autosaveTimer")).stop();
                    panel.addFetchedCard(card("Audit Card","10"),"E","TST");
                    call(panel,"duplicateSelectedCard",new Class<?>[]{});
                    List<TradeItem> items=(List<TradeItem>)field(panel,"receivedCards");
                    confirm(items.get(0).getFinishType().equals("E") && items.get(1).getFinishType().equals("F"),
                        "Duplicating an etched row changes model finish E to F while table still says etched");

                    TradePanel conditionPanel=new TradePanel();
                    ((javax.swing.Timer)field(conditionPanel,"autosaveTimer")).stop();
                    conditionPanel.addFetchedCard(card("Condition Card","10"),"","TST");
                    DefaultTableModel model=(DefaultTableModel)field(conditionPanel,"tableModel");
                    model.setValueAt("LP",0,3);
                    call(conditionPanel,"updatePriceForCondition",new Class<?>[]{int.class},0);
                    call(conditionPanel,"duplicateSelectedCard",new Class<?>[]{});
                    model.setValueAt("LP",1,3);
                    call(conditionPanel,"updatePriceForCondition",new Class<?>[]{int.class},1);
                    confirm(model.getValueAt(0,5).equals("$8.00") && model.getValueAt(1,5).equals("$6.50"),
                        "Duplicated LP card discounts again: original $8.00, duplicate after LP update $6.50");

                    TradePanel restorePanel=new TradePanel();
                    ((javax.swing.Timer)field(restorePanel,"autosaveTimer")).stop();
                    Card restoreHit=card("Audit Card","10"); index.put("TST:1E",restoreHit);
                    TradeSessionService.SavedSession saved=new TradeSessionService.SavedSession("Audit","Customer",
                        List.of(new TradeSessionService.SessionRow("TST 1e","Audit Card (Etched)","LP",1,bd("8"))));
                    call(restorePanel,"restoreSession",new Class<?>[]{TradeSessionService.SavedSession.class},saved);
                    TradeItem restored=((List<TradeItem>)field(restorePanel,"receivedCards")).get(0);
                    confirm(!restored.isFoil() && restored.getCard().getCollectorNumber().equals("1e"),
                        "Session restore embeds finish in collector number and creates a normal-finish item");
                    confirm(!rates.computePayout("TST","1",restored.getCard().getName(),bd("8")).isBounty(),
                        "Restored display name 'Audit Card (Etched)' no longer matches 'Audit Card' bounty");
                    DefaultTableModel restoredModel=(DefaultTableModel)field(restorePanel,"tableModel");
                    restoredModel.setValueAt("NM",0,3);
                    call(restorePanel,"updatePriceForCondition",new Class<?>[]{int.class},0);
                    confirm(restoredModel.getValueAt(0,5).equals("$8.00"),
                        "Recovered LP row cannot recover original $10 NM base; changing NM keeps $8");

                    FileManagerPanel files=new FileManagerPanel();
                    LocalDateTime t=LocalDateTime.of(2026,9,15,10,30,1);
                    TradeRecord first=new TradeRecord("first",t,"Same Customer","A","Check",bd("10"),1);
                    TradeRecord second=new TradeRecord("second",t.plusSeconds(1),"Same Customer","B","Check",bd("20"),2);
                    set(files,"allRecords",new ArrayList<>(List.of(first,second)));
                    DefaultTableModel history=(DefaultTableModel)field(files,"historyTableModel");
                    history.setRowCount(0);
                    history.addRow(new Object[]{"2026-09-15 10:30","Same Customer","Check","$10.00",1});
                    history.addRow(new Object[]{"2026-09-15 10:30","Same Customer","Check","$20.00",2});
                    ((JTable)field(files,"historyTable")).setRowSelectionInterval(1,1);
                    confirm(call(files,"selectedRecord",new Class<?>[]{})==first,
                        "Selecting second same-customer receipt in one minute resolves to first receipt");
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            SwingUtilities.invokeAndWait(()->{}); // drain queued table callbacks
            Locale.setDefault(Locale.GERMANY);
            String formatted=String.format("$%.2f",bd("12.50"));
            BigDecimal reparsed=new BigDecimal(formatted.replace("$","").replace(",",""));
            confirm(reparsed.equals(bd("1250")), "Comma-decimal locale turns displayed $12,50 into parsed value 1250");
            Locale.setDefault(Locale.US);
            System.out.println("All " + confirmed + " baseline defect probes confirmed.");
            System.exit(0);
        } catch(Throwable t) { t.printStackTrace(); System.exit(1); }
    }

    public static class MemoryFactory implements PreferencesFactory {
        private final Preferences root=new MemoryNode(null,"");
        public Preferences userRoot(){ return root; }
        public Preferences systemRoot(){ return root; }
    }
    static class MemoryNode extends AbstractPreferences {
        final Map<String,String> values=new HashMap<>();
        MemoryNode(AbstractPreferences parent,String name){super(parent,name);}
        protected void putSpi(String key,String value){values.put(key,value);}
        protected String getSpi(String key){return values.get(key);}
        protected void removeSpi(String key){values.remove(key);}
        protected void removeNodeSpi(){values.clear();}
        protected String[] keysSpi(){return values.keySet().toArray(String[]::new);}
        protected String[] childrenNamesSpi(){return new String[0];}
        protected AbstractPreferences childSpi(String name){return new MemoryNode(this,name);}
        protected void syncSpi(){}
        protected void flushSpi(){}
    }
}
