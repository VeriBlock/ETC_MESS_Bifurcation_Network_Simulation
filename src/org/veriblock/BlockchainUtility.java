package org.veriblock;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class BlockchainUtility {
    private static Random r = new Random();
    private static final String hexAlphabet = "0123456789ABCDEF";

    private static ConcurrentHashMap<String, Block> commonAncestorCache = new ConcurrentHashMap<>();

    public static Block getCommonAncestor(Block tipA, Block tipB, List<List<Block>> blockTree) {
        String cacheKey = tipA.getBlockHash() + ":" + tipB.getBlockHash();
        if (commonAncestorCache.containsKey(cacheKey)) {
            return commonAncestorCache.get(cacheKey);
        }

        Block tipACursor = tipA;
        Block tipBCursor = tipB;

        int lowestTipHeight = tipACursor.getHeight();
        if (tipBCursor.getHeight() < lowestTipHeight) {
            lowestTipHeight = tipBCursor.getHeight();
        }

        while (tipACursor.getHeight() > lowestTipHeight) {
            tipACursor = getBlockInTree(tipACursor.getHeight() - 1, tipACursor.getPreviousBlockHash(), blockTree);
            if (tipACursor == null) {
                return null;
            }
        }

        while (tipBCursor.getHeight() > lowestTipHeight) {
            tipBCursor = getBlockInTree(tipBCursor.getHeight() - 1, tipBCursor.getPreviousBlockHash(), blockTree);
            if (tipBCursor == null) {
                return null;
            }
        }

        while (!tipACursor.getBlockHash().equalsIgnoreCase(tipBCursor.getBlockHash())) {
            tipACursor = getBlockInTree(tipACursor.getHeight() - 1, tipACursor.getPreviousBlockHash(), blockTree);
            tipBCursor = getBlockInTree(tipBCursor.getHeight() - 1, tipBCursor.getPreviousBlockHash(), blockTree);

            if (tipACursor == null || tipBCursor == null) {
                return null;
            }
        }

        commonAncestorCache.put(cacheKey, tipACursor);
        return tipACursor;
    }

    private static ConcurrentHashMap<String, BigInteger> cumulativeDifficultyCache = new ConcurrentHashMap<>();

    public static BigInteger getCumulativeDifficultyForChainSegment(Block tip, Block ancestor, List<List<Block>> blockTree) {
        return tip.getCumulativeDifficulty();
    }

    public static Block getBlockInTree(int index, String hash, List<List<Block>> blockTree) {
        List<Block> blocksAtHeight = blockTree.get(index);

        for (Block b : blocksAtHeight) {
            if (b.getBlockHash().equalsIgnoreCase(hash)) {
                return b;
            }
        }

        return null;
    }

    public static Block addBlockToTreeAndGetNewTip(Block blockToAdd, Block currentTip, List<List<Block>> blockTree) {
        if (blockTree.size() < blockToAdd.getHeight()) {
            return null;
        }

        if (blockTree.size() == blockToAdd.getHeight()) {
            blockTree.add(new ArrayList<>());
        }

        boolean fits = false;
        for (Block b : blockTree.get(blockToAdd.getHeight() - 1)) {
            if (b.getBlockHash().equalsIgnoreCase(blockToAdd.getPreviousBlockHash())) {
                fits = true;
                break;
            }
        }

        if (!fits) {
            return null;
        }

        blockTree.get(blockToAdd.getHeight()).add(blockToAdd);

        if (blockToAdd.getPreviousBlockHash().equalsIgnoreCase(currentTip.getBlockHash())) {
            return blockToAdd;
        }

        Block commonAncestor = getCommonAncestor(currentTip, blockToAdd, blockTree);

        if (commonAncestor == null) {
            return null;
        }

        BigInteger currentChainCumulativeDifficulty = getCumulativeDifficultyForChainSegment(currentTip, commonAncestor, blockTree);
        BigInteger proposedForkChainCumulativeDifficulty = getCumulativeDifficultyForChainSegment(blockToAdd, commonAncestor, blockTree);

        if (MESS.acceptReorg(commonAncestor.getTimestamp(),
                currentTip.getTimestamp(),
                currentChainCumulativeDifficulty,
                proposedForkChainCumulativeDifficulty
                )) {
            return blockToAdd;
        } else {
            return currentTip;
        }
    }

    public static boolean knowsAboutBlock(Block blockToFind, List<List<Block>> blockTree) {
        if (blockToFind.getHeight() >= blockTree.size()) {
            return false;
        }

        List<Block> slice = blockTree.get(blockToFind.getHeight());
        for (Block b : slice) {
            if (b.getBlockHash().equalsIgnoreCase(blockToFind.getBlockHash())) {
                return true;
            }
        }

        return false;
    }

    // Hash doesn't actually reflect difficulty, just for easy visual identification
    public static String randomBlockHash() {
        String blockHash = "0000000000000000";
        for (int i = 0; i < 48; i++) {
            int rand = r.nextInt(hexAlphabet.length());
            blockHash += hexAlphabet.substring(rand, rand+1);
        }

        return blockHash;
    }
}
