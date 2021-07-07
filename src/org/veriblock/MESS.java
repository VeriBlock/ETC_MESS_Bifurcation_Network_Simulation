package org.veriblock;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;

public class MESS {
    private static ConcurrentHashMap<Integer, BigDecimal> messMultiplierCache = new ConcurrentHashMap<>();
    static {
        for (int i = 0; i <= 25132; i++) {
            messMultiplierCache.put(i, BigDecimal.valueOf(getMultiplier(i)));
        }
    }

    public static boolean acceptReorg(int ancestorTimestamp, int currentTipTimestamp, BigInteger localSubchainTD, BigInteger proposedSubchainTD) {
        int timestampDifference = currentTipTimestamp - ancestorTimestamp;

        BigDecimal multiplier = messMultiplierCache.get(timestampDifference);
        BigDecimal want = new BigDecimal(localSubchainTD).multiply(multiplier);
        BigDecimal got = new BigDecimal(proposedSubchainTD);

        if (got.compareTo(want) < 0) {
            return false;
        }

        return true;
    }

    public static double getMultiplier(int timestampDifference) {
        return 15 * Math.sin((timestampDifference + 12000 * 3.14159265)/8000) + 15 + 1;
    }
}
