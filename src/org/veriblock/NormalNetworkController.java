package org.veriblock;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;

public class NormalNetworkController {
    private BigInteger legitimateNetworkHashrate;
    private List<StandardPeer> standardPeers;
    private Random random = new Random();
    int highestBlockMined = 0;
    int totalBlocksMined = 0;
    int orphanBlocksMined = 0;

    public NormalNetworkController(BigInteger hashrate, List<StandardPeer> standardPeers) {
        this.legitimateNetworkHashrate = hashrate;
        this.standardPeers = standardPeers;
    }

    public void tick(int tick) {
        StandardPeer selectedMinerPeer = null;
        while (selectedMinerPeer == null) {
            int peerIndex = random.nextInt(standardPeers.size());
            StandardPeer toTest = standardPeers.get(peerIndex);
            if (Variables.ASSUME_POOLS) {
                if (toTest.isPool) {
                    selectedMinerPeer = toTest;
                }
            } else {
                selectedMinerPeer = toTest;
            }
        }

        Block minerTip = selectedMinerPeer.getTip();

        int timestamp = tick / 1000;
        BigInteger nextDifficulty = DifficultyCalculator.calculateNextDifficulty(timestamp, minerTip.getTimestamp(), minerTip.getDifficulty());
        if (DifficultyCalculator.mineChance(nextDifficulty, legitimateNetworkHashrate)) {
            Block minedBlock = new Block(nextDifficulty,
                    timestamp,
                    minerTip.getHeight() + 1,
                    BlockchainUtility.randomBlockHash(),
                    minerTip.getBlockHash(),
                    1.0, tick, (minerTip.getIsAttackerReorg()), minerTip.getCumulativeDifficulty().add(nextDifficulty)); // For simulation purposes, assume processing weight is 0.0 (worst case for attacker)

            if (minedBlock.getHeight() <= highestBlockMined) {
                orphanBlocksMined++;
            } else {
                highestBlockMined = minedBlock.getHeight();
            }

            totalBlocksMined++;
            selectedMinerPeer.mineBlock(minedBlock);
        }
    }
}
