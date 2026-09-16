package com.cardpricer.service;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SettlementEngineTest {
    private final SettlementEngine engine = new SettlementEngine();
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static SettlementEngine.Line line(String id, String market, String credit, String check, boolean stock) {
        return new SettlementEngine.Line(id, 1, d(market), d(credit), d(check), stock);
    }
    private final List<SettlementEngine.Line> ordinary = List.of(line("1", "100", "50", "40", true));
    @Test void defaultCheckAndBountyUseQuote() {
        assertEquals(d("40.00"), engine.settle(ordinary, "check", null, null).total());
        var bounty = List.of(line("bounty", "100", "80", "60", true));
        assertEquals(d("80.00"), engine.settle(bounty, "credit", null, null).stockCost());
    }
    @Test void invalidSplitsAreRejected() {
        for (String[] amounts : List.of(new String[]{"-10", "48"}, new String[]{"0", "0"},
                new String[]{"25", "19.99"}, new String[]{"25.001", "20"}, new String[]{"51", "0"}))
            assertThrows(IllegalArgumentException.class, () -> engine.settle(ordinary, "partial", d(amounts[0]), d(amounts[1])));
        assertThrows(IllegalArgumentException.class, () -> engine.settle(ordinary, "typo", null, null));
    }
    @Test void splitIncludesMiscWithoutSpreadingItsCost() {
        var lines = List.of(line("stock", "100", "80", "40", true), line("misc", "100", "50", "40", false));
        var result = engine.settle(lines, "partial", d("65"), d("40"));
        assertEquals(d("60.00"), result.stockCost());
        assertEquals(d("105.00"), result.total());
        assertEquals(d("45.00"), result.lines().get(1).cost());
    }
    @Test void centsReconcileAndAllocationIsDeterministic() {
        var lines = List.of(line("a", "0.10", "0.05", "0.04", true), line("b", "0.10", "0.05", "0.04", true),
                line("c", "0.10", "0.05", "0.04", true));
        var result = engine.settle(lines, "partial", d("0.07"), d("0.06"));
        assertEquals(d("0.13"), result.stockCost());
        assertEquals(d("0.03"), result.lines().getFirst().credit());
        assertEquals(result, engine.settle(lines, "partial", d("0.07"), d("0.06")));
    }
    @Test void splitEndpointsAndZeroValues() {
        assertEquals(d("50.00"), engine.settle(ordinary, "partial", d("50"), d("0")).total());
        assertEquals(d("40.00"), engine.settle(ordinary, "partial", d("0"), d("40")).total());
        assertEquals(d("0.00"), engine.settle(List.of(line("zero", "0", "0", "0", false)), "partial", d("0"), d("0")).total());
    }
}
