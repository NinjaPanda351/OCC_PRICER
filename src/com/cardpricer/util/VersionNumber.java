package com.cardpricer.util;
import java.math.BigInteger;
import java.util.regex.Pattern;
public record VersionNumber(BigInteger major, BigInteger minor, BigInteger patch, String suffix) implements Comparable<VersionNumber> {
    public static VersionNumber parse(String input) {
        var matcher=Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$").matcher(input);
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid version: "+input);
        return new VersionNumber(new BigInteger(matcher.group(1)),new BigInteger(matcher.group(2)),new BigInteger(matcher.group(3)),matcher.group(4));
    }
    public int compareTo(VersionNumber other) {
        int comparison=major.compareTo(other.major); if (comparison!=0) return comparison;
        comparison=minor.compareTo(other.minor); if (comparison!=0) return comparison;
        comparison=patch.compareTo(other.patch); if (comparison!=0) return comparison;
        if (suffix==null) return other.suffix==null ? 0 : 1;
        if (other.suffix==null) return -1;
        String[] left=suffix.split("\\."),right=other.suffix.split("\\.");
        for (int i=0;i<Math.min(left.length,right.length);i++) {
            boolean a=left[i].matches("\\d+"), b=right[i].matches("\\d+");
            comparison=a&&b ? new BigInteger(left[i]).compareTo(new BigInteger(right[i])) : a!=b ? (a?-1:1) : left[i].compareTo(right[i]);
            if (comparison!=0) return comparison;
        }
        return Integer.compare(left.length,right.length);
    }
}
