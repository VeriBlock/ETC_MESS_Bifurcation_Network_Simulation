package org.veriblock;

import java.math.BigDecimal;
import java.math.BigInteger;

public class Variables {
    public static final double MINIMUM_THRESHOLD_OF_VULNERABLE_PEERS_ACTIVATION = 0.05;
    public static final double MAXIMUM_THRESHOLD_OF_VULNERABLE_PEERS_ACTIVATION = 0.65;
    public static final int FIRST_ELIGIBLE_FORK_TIMESTAMP = 854;

    public static final int NUMBER_OF_STANDARD_PEERS = 100;
    public static final int NUMBER_OF_ATTACKING_PEERS = 100;
    public static final int MINIMUM_NUMBER_OF_STANDARD_PEER_CONNECTIONS = 10;
    public static final int NUMBER_OF_RANDOM_ADDITIONAL_STANDARD_PEER_CONNECTIONS = 10;
    public static final int MINIMUM_NUMBER_OF_ROGUE_PEER_CONNECTIONS = 5;
    public static final int NUMBER_OF_RANDOM_ADDITIONAL_ROGUE_PEER_CONNECTIONS = 5;
    public static final int MAXIMUM_STANDARD_PEER_LATENCY_MS = 400;
    public static final int MAXIMUM_ROGUE_TO_STANDARD_PEER_LATENCY_MS = 400;
    public static final int MAXIMUM_ROGUE_TO_ROGUE_PEER_LATENCY_MS = 400;

    public static final boolean ASSUME_POOLS = false;

    public static final BigInteger STARTING_NETWORK_HASHRATE = BigInteger.valueOf(1000000000000L);
    public static final BigInteger STARTING_NETWORK_BLOCK_TIME_AVERAGE = BigInteger.valueOf(13);
    public static final BigInteger STARTING_NETWORK_DIFFICULTY = STARTING_NETWORK_HASHRATE.multiply(STARTING_NETWORK_BLOCK_TIME_AVERAGE);
    public static final BigDecimal ATTACKER_HASHRATE_MULTIPLE = BigDecimal.valueOf(1.25d); // 125% of legitimate network hashrate
    public static final BigInteger ATTACKER_HASHRATE = ATTACKER_HASHRATE_MULTIPLE.multiply(new BigDecimal(STARTING_NETWORK_HASHRATE)).toBigInteger();
}
