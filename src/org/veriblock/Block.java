package org.veriblock;

import java.math.BigInteger;

public class Block {
    private BigInteger difficulty;
    private int timestamp;
    private int height;
    private String previousBlockHash;
    private String blockHash;
    private double processingWeight;
    private int minedAtTick;
    private boolean isAttackerReorg;
    private BigInteger cumulativeDifficulty;

    public Block(BigInteger difficulty, int timestamp, int height, String blockHash, String previousBlockHash, double processingWeight, int minedAtTick, boolean isAttackerReorg, BigInteger cumulativeDifficulty) {
        this.difficulty = difficulty;
        this.timestamp = timestamp;
        this.height = height;
        this.blockHash = blockHash;
        this.previousBlockHash = previousBlockHash;
        this.processingWeight = processingWeight;
        this.minedAtTick = minedAtTick;
        this.isAttackerReorg = isAttackerReorg;
        this.cumulativeDifficulty = cumulativeDifficulty;
    }

    public BigInteger getCumulativeDifficulty() {
        return cumulativeDifficulty;
    }

    public boolean getIsAttackerReorg() {
        return isAttackerReorg;
    }

    public int getMinedAtTick() {
        return minedAtTick;
    }

    public BigInteger getDifficulty() {
        return difficulty;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public int getHeight() {
        return height;
    }

    public String getPreviousBlockHash() {
        return previousBlockHash;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public double getProcessingWeight() {
        return processingWeight;
    }
}
