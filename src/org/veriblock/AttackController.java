package org.veriblock;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;

public class AttackController {
    private BigInteger attackerHashrate;
    private Block currentTip;
    private List<RoguePeer> roguePeers;
    private BigInteger cumulativeAttackChainDifficulty = BigInteger.ZERO;
    private HashMap<RoguePeer, Integer> roguePeerLatencies;
    private RoguePeer localNode;

    private static boolean wasAttackSent = false;

    private Block attackTipUsed;
    private Block oldPublicTipLeftInFracture;

    private Block lastPublicTipMined;

    private int attackStage = 0;

    public AttackController(BigInteger hashrate, Block blockToStartAttackFrom, List<RoguePeer> roguePeers, HashMap<RoguePeer, Integer> roguePeerLatencies) {
        this.attackerHashrate = hashrate;
        this.currentTip = blockToStartAttackFrom;
        this.roguePeers = roguePeers;
        this.roguePeerLatencies = roguePeerLatencies;
        wasAttackSent = false;

        for (RoguePeer rp : roguePeers) {
            rp.setAttackController(this);
        }

        localNode = roguePeers.get(0);
    }

    public void tick(int tick) {
        if (!wasAttackSent) {
            BigInteger nextDifficulty = DifficultyCalculator.calculateNextDifficulty(currentTip.getTimestamp() + 1, currentTip.getTimestamp(), currentTip.getDifficulty());
            if (DifficultyCalculator.mineChance(nextDifficulty, attackerHashrate)) {
                Block newBlock = new Block(nextDifficulty,
                        currentTip.getTimestamp() + 1,
                        currentTip.getHeight() + 1,
                        BlockchainUtility.randomBlockHash(),
                        currentTip.getBlockHash(), 0, tick, true, currentTip.getCumulativeDifficulty().add(nextDifficulty)); // Attacker blocks are empty
                currentTip = newBlock;

                cumulativeAttackChainDifficulty = cumulativeAttackChainDifficulty.add(newBlock.getDifficulty());

                for (RoguePeer rp : roguePeers) {
                    rp.makeAwareOfAttackingBlock(newBlock, roguePeerLatencies.get(rp));
                }
            }
        } else {
            // Mine on whatever chain is lowest
            Block originalChainTip = localNode.highestNonForkBlock();
            Block highestForkBlock = localNode.highestForkingBlock();

            if (lastPublicTipMined.getHeight() > originalChainTip.getHeight()) {
                originalChainTip = lastPublicTipMined;
            }

            if (highestForkBlock == null) {
                highestForkBlock = currentTip;
            } else if (currentTip.getHeight() < highestForkBlock.getHeight()) {
                highestForkBlock = currentTip;
            }

            boolean mineOnOriginalChain = true;

            if (originalChainTip.getCumulativeDifficulty().compareTo(highestForkBlock.getCumulativeDifficulty()) > 0) {
                    mineOnOriginalChain = false;
            }

            if (attackStage == 0) {
                // Need to mine block on non-attack chain first
                mineOnOriginalChain = true;
            } else if (attackStage == 1) {
                // Need to mine block on attack chain next, to bring it's multiplier up because its tip has an old timestamp
                mineOnOriginalChain = false;
            }

            if (mineOnOriginalChain) {
                // Mine on top of old public chain to prevent complete reorg onto attacking chain
                BigInteger nextDifficulty = DifficultyCalculator.calculateNextDifficulty(originalChainTip.getTimestamp() + 1, originalChainTip.getTimestamp(), originalChainTip.getDifficulty());
                if (DifficultyCalculator.mineChance(nextDifficulty, attackerHashrate)) {
                    int timestamp = tick / 1000;
                    Block newBlock = new Block(nextDifficulty,
                            timestamp,
                            originalChainTip.getHeight() + 1,
                            BlockchainUtility.randomBlockHash(),
                            originalChainTip.getBlockHash(), 0, tick, false, originalChainTip.getCumulativeDifficulty().add(nextDifficulty)); // Attacker blocks are empty
                    lastPublicTipMined = newBlock;

                    if (attackStage == 0) {
                        attackStage++;
                    }

                    for (RoguePeer rp : roguePeers) {
                        rp.makeAwareOfBlockBuildingOnMainChain(newBlock, roguePeerLatencies.get(rp));
                    }
                }
            } else {
                // Mine on top of now-public chain to prevent complete reorg onto attacking chain
                // The first block re-mined on this chain is important because it updates the timestamp on nodes
                // which accepted the fork, allowing both forks to have a high MESS multiplier
                BigInteger nextDifficulty = DifficultyCalculator.calculateNextDifficulty(currentTip.getTimestamp() + 1, currentTip.getTimestamp(), currentTip.getDifficulty());
                if (DifficultyCalculator.mineChance(nextDifficulty, attackerHashrate)) {
                    int timestamp = tick / 1000;
                    Block newBlock = new Block(nextDifficulty,
                            timestamp,
                            currentTip.getHeight() + 1,
                            BlockchainUtility.randomBlockHash(),
                            currentTip.getBlockHash(), 0, tick, true, currentTip.getCumulativeDifficulty().add(nextDifficulty)); // Attacker blocks are empty
                    currentTip = newBlock;

                    if (attackStage == 1) {
                        attackStage++;
                    }

                    for (RoguePeer rp : roguePeers) {
                        rp.makeAwareOfAttackingBlock(newBlock, roguePeerLatencies.get(rp));
                    }
                }
            }
        }
    }

    public void beginAttack(Block attackTipUsed, Block oldPublicTipLeftInFracture) {
        this.attackTipUsed = attackTipUsed;
        this.currentTip = attackTipUsed;
        this.oldPublicTipLeftInFracture = oldPublicTipLeftInFracture;
        this.lastPublicTipMined = oldPublicTipLeftInFracture;
        this.wasAttackSent = true;
    }

    public static boolean wasAttackSent() {
        return wasAttackSent;
    }

    public BigInteger getCumulativeDifficultyOfAttackingChain() {
        return cumulativeAttackChainDifficulty;
    }

    public Block getCurrentTip() {
        return currentTip;
    }
}
