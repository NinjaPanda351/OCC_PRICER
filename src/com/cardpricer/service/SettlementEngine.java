package com.cardpricer.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure settlement calculation. Quote amounts are rounded per unit before quantity.
 * Split tender uses one proportion for the entire quote, including MISC lines.
 * Each tender's residual cents go to the largest remainders, then input order. */
public final class SettlementEngine {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    public record Line(String id, int quantity, BigDecimal market, BigDecimal credit,
                       BigDecimal check, boolean stock) {
        public Line {
            Objects.requireNonNull(id);
            if (quantity < 1) throw new IllegalArgumentException("Quantity must be positive");
            market = money(market); credit = money(credit); check = money(check);
            if (credit.compareTo(market) > 0 || check.compareTo(market) > 0)
                throw new IllegalArgumentException("Payout cannot exceed market value");
        }
    }
    public record Allocation(Line line, BigDecimal credit, BigDecimal check) {
        public BigDecimal cost() { return credit.add(check); }
    }
    public record Settlement(String payment, List<Allocation> lines, BigDecimal credit,
                             BigDecimal check, BigDecimal market) {
        public Settlement { lines = List.copyOf(lines); }
        public BigDecimal total() { return credit.add(check); }
        public BigDecimal stockCost() {
            return lines.stream().filter(a -> a.line().stock()).map(Allocation::cost)
                    .reduce(ZERO, BigDecimal::add);
        }
    }
    public Settlement settle(List<Line> input, String payment, BigDecimal credit, BigDecimal check) {
        List<Line> lines = List.copyOf(input);
        BigDecimal allCredit = lines.stream().map(Line::credit).reduce(ZERO, BigDecimal::add);
        BigDecimal allCheck = lines.stream().map(Line::check).reduce(ZERO, BigDecimal::add);
        BigDecimal market = lines.stream().map(Line::market).reduce(ZERO, BigDecimal::add);
        switch (payment) {
            case "credit" -> { credit = allCredit; check = ZERO; }
            case "check" -> { credit = ZERO; check = allCheck; }
            case "inventory" -> { credit = ZERO; check = ZERO; }
            case "partial" -> {
                credit = money(credit); check = money(check);
                if (credit.compareTo(allCredit) > 0 || check.compareTo(allCheck) > 0)
                    throw new IllegalArgumentException("Split exceeds the quoted payout");
                if (allCredit.signum() == 0 || allCheck.signum() == 0) {
                    if (credit.compareTo(allCredit) != 0 || check.compareTo(allCheck) != 0)
                        throw new IllegalArgumentException("Split is unavailable for this zero-valued quote");
                } else {
                    if (credit.add(check).signum() == 0)
                        throw new IllegalArgumentException("Enter a payout for this trade");
                    BigDecimal expectedCheck = remainingCheck(allCredit, allCheck, credit);
                    // Exactly the cent-rounded complementary tender, with no dollar tolerance.
                    if (check.compareTo(expectedCheck) != 0)
                        throw new IllegalArgumentException("Split no longer matches the quote; check must be $"
                                + expectedCheck.toPlainString() + " for this credit amount");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported payment mode: " + payment);
        }
        List<BigDecimal> credits = allocate(lines.stream().map(Line::credit).toList(), credit);
        List<BigDecimal> checks = allocate(lines.stream().map(Line::check).toList(), check);
        List<Allocation> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) result.add(new Allocation(lines.get(i), credits.get(i), checks.get(i)));
        return new Settlement(payment, result, credit, check, market);
    }
    public static BigDecimal remainingCheck(BigDecimal allCredit, BigDecimal allCheck, BigDecimal credit) {
        return allCheck.multiply(allCredit.subtract(credit)).divide(allCredit, 2, RoundingMode.HALF_UP);
    }
    private static List<BigDecimal> allocate(List<BigDecimal> weights, BigDecimal target) {
        BigDecimal sum = weights.stream().reduce(ZERO, BigDecimal::add);
        List<BigDecimal> result = new ArrayList<>();
        List<BigDecimal> fractions = new ArrayList<>();
        BigDecimal used = ZERO;
        for (BigDecimal weight : weights) {
            BigDecimal exact = sum.signum() == 0 ? ZERO : target.multiply(weight).divide(sum, 24, RoundingMode.DOWN);
            BigDecimal floor = exact.setScale(2, RoundingMode.DOWN);
            result.add(floor); fractions.add(exact.subtract(floor)); used = used.add(floor);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) order.add(i);
        order.sort(Comparator.<Integer, BigDecimal>comparing(fractions::get).reversed().thenComparingInt(i -> i));
        int cents = target.subtract(used).movePointRight(2).intValueExact();
        for (int i = 0; i < cents; i++) {
            int at = order.get(i);
            result.set(at, result.get(at).add(new BigDecimal("0.01")));
        }
        return List.copyOf(result);
    }
    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Amounts must be non-negative");
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Amounts must have at most two decimal places", e); }
    }
}
