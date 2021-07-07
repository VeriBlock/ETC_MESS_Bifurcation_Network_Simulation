package org.veriblock;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class DifficultyCalculator {
    public static void main(String[] args) {
        BigInteger normalHashrate = BigInteger.valueOf(1_000_000_000L);
        BigInteger normalDifficulty = BigInteger.valueOf(13_000_000_000L);
        BigInteger attackerHashrate = BigInteger.valueOf(1_250_000_000L);

        int total = 0;

        for (int trial = 1; trial < 1000; trial++) {
            int blocksMinedInRound = 0;
            BigInteger difficulty = normalDifficulty;
            for (int tick = 0; tick < 15 * 60 * 1000; tick++) {
                if (mineChance(difficulty, attackerHashrate)) {
                    blocksMinedInRound++;
                }
            }
            total += blocksMinedInRound;
        }

        System.out.println(total / 1000);
    }

    public static BigInteger calculateNextDifficulty(int blockTime, int parentBlockTime, BigInteger parentDifficulty) {
        BigInteger bigTime = BigInteger.valueOf(blockTime);
        BigInteger bigParentTime = BigInteger.valueOf(parentBlockTime);

        // holds intermediate values to make the algo easier to read & audit
        BigInteger x;
        BigInteger y;

        // (2 if len(parent_uncles) else 1) - (block_timestamp - parent_timestamp) // 9
        x = bigTime.subtract(bigParentTime);
        x = x.divide(BigInteger.valueOf(9));

        // if (parent has uncle hashes) { x.Sub(big1, x) } else { x.Sub(big2, x) }

        x = BigInteger.valueOf(1).subtract(x);

        // max((2 if len(parent_uncles) else 1) - (block_timestamp - parent_timestamp) // 9, -99)
        if (x.compareTo(BigInteger.valueOf(-99)) < 0) {
            x = BigInteger.valueOf(-99);
        }

        // parent_diff + (parent_diff / 2048 * max((2 if len(parent.uncles) else 1) - ((timestamp - parent.timestamp) // 9), -99))
        y = parentDifficulty.divide(BigInteger.valueOf(2048));
        x = y.multiply(x);
        x = x.add(parentDifficulty);

        return x;
    }

    public static boolean mineChance(BigInteger difficulty, BigInteger hashrate) {
        // Hashrate is per second, but ticks are milliseconds
        hashrate = hashrate.divide(BigInteger.valueOf(1000));

        BigDecimal ratio = new BigDecimal(hashrate).divide(new BigDecimal(difficulty), 10, RoundingMode.CEILING);
        Random r = new Random();
        if (r.nextDouble() < ratio.doubleValue()) {
            return true;
        }

        return false;
    }
}
