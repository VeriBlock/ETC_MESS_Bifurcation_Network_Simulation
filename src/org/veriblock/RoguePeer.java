package org.veriblock;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public class RoguePeer implements Peer {
    private List<List<Block>> blockTree;
    private List<Message> messages;
    private Block currentTip;
    private Block originalAncestor;
    private boolean isPerformingAttack;
    private List<RoguePeer> allRoguePeers;
    private List<StandardPeer> allStandardPeers;
    private HashMap<Peer, Integer> peerLatencies;
    private HashMap<StandardPeer, Block> standardPeerTips;
    private int knownAttackImpossibleHeight = 0;
    private int highestNormalPeerTimestamp = 0;

    private AttackController attackController;

    private boolean peerTipsChanged = false;

    private List<Pair<Block, Integer>> blocksToProcess;
    private List<Block> processedBlocksToAddLater = new ArrayList<>();
    private HashSet<String> processedBlocksToAddLaterSet = new HashSet<>();
    private int peerId;

    private List<Pair<Block, Integer>> attackingBlocksIncoming = new ArrayList<>();
    private List<Pair<Block, Integer>> mainChainBlocksIncoming = new ArrayList<>();

    private boolean informedPeersOfAttack = false;

    public RoguePeer(Block genesisBlock, int peerId) {
        blockTree = new ArrayList<>();
        blockTree.add(new ArrayList<>());
        blockTree.get(0).add(genesisBlock);

        this.currentTip = genesisBlock;
        this.originalAncestor = genesisBlock;

        messages = new ArrayList<>();

        isPerformingAttack = false;
        standardPeerTips = new HashMap<>();
        blocksToProcess = new ArrayList<>();

        allRoguePeers = new ArrayList<>();
        allStandardPeers = new ArrayList<>();
        peerLatencies = new HashMap<>();

        this.peerId = peerId;
    }

    public void setAttackController(AttackController attackController) {
        this.attackController = attackController;
    }

    public void makeAwareOfAttackingBlock(Block block, int latency) {
        attackingBlocksIncoming.add(new Pair<>(block, latency));
    }

    public void makeAwareOfBlockBuildingOnMainChain(Block block, int latency) {
        mainChainBlocksIncoming.add(new Pair<>(block, latency));
    }

    public Block highestNonForkBlock() {
        for (int i = blockTree.size() - 1; i > 0; i--) {
            List<Block> slice = blockTree.get(i);
            for (Block b : slice) {
                if (!b.getIsAttackerReorg()) {
                    return b;
                }
            }
        }

        return null;
    }

    public Block highestForkingBlock() {
        for (int i = blockTree.size() - 1; i > 0; i--) {
            List<Block> slice = blockTree.get(i);
            for (Block b : slice) {
                if (b.getIsAttackerReorg()) {
                    return b;
                }
            }
        }

        return null;
    }

    @Override
    public Block getTip() {
        return currentTip;
    }

    @Override
    public int getPeerId() {
        return peerId;
    }

    private void checkForAttackPossibility() {
        if (standardPeerTips.size() == allStandardPeers.size()) {
            Block lowestPeerBlock = standardPeerTips.get(allStandardPeers.get(0));
            Block highestPeerBlock = standardPeerTips.get(allStandardPeers.get(0));

            for (int i = 1; i < allStandardPeers.size(); i++) {
                Block peerBlock = standardPeerTips.get(allStandardPeers.get(i));
                if (peerBlock.getHeight() < lowestPeerBlock.getHeight()) {
                    lowestPeerBlock = peerBlock;
                }
                if (peerBlock.getHeight() > highestPeerBlock.getHeight()) {
                    highestPeerBlock = peerBlock;
                }
            }

            if (!lowestPeerBlock.getBlockHash().equalsIgnoreCase(highestPeerBlock.getBlockHash())) {
                int peersAtLowest = 0;
                for (int i = 0; i < allStandardPeers.size(); i++) {
                    Block peerBlock = standardPeerTips.get(allStandardPeers.get(i));
                    if (peerBlock.getHeight() == lowestPeerBlock.getHeight()) {
                        peersAtLowest++;
                    }
                }

                double ratio = (double)peersAtLowest / allStandardPeers.size();
                if (ratio >= Variables.MINIMUM_THRESHOLD_OF_VULNERABLE_PEERS_ACTIVATION && ratio <= Variables.MAXIMUM_THRESHOLD_OF_VULNERABLE_PEERS_ACTIVATION) {
                    // It's possible to do an attack if we have the right chain.
                    BigInteger difficultyToReorganizeLowChain = BigDecimal.valueOf(
                            MESS.getMultiplier(lowestPeerBlock.getTimestamp()))
                            .multiply(new BigDecimal(
                                    BlockchainUtility.getCumulativeDifficultyForChainSegment(
                                            lowestPeerBlock,
                                            originalAncestor,
                                            blockTree))).toBigInteger();

                    BigInteger difficultyToNotReorganizeHighChain = BigDecimal.valueOf(
                            MESS.getMultiplier(highestPeerBlock.getTimestamp()))
                            .multiply(new BigDecimal(
                                    BlockchainUtility.getCumulativeDifficultyForChainSegment(
                                            highestPeerBlock,
                                            originalAncestor,
                                            blockTree))).toBigInteger();

                    Block cursor = highestForkingBlock();

                    try {
                    while (cursor.getHeight() > 0) {
                        BigInteger cumulativeDifficulty = BlockchainUtility.getCumulativeDifficultyForChainSegment(cursor, originalAncestor, blockTree);
                        if (cumulativeDifficulty.compareTo(difficultyToNotReorganizeHighChain) < 0) {
                            if (cumulativeDifficulty.compareTo(difficultyToReorganizeLowChain) > 0) {
                                System.out.println("Attacker has the correct block at height " + highestPeerBlock.getHeight() + "! Beginning attack...");
                                tellPeersToAttack(cursor);
                                attack(cursor, highestPeerBlock);
                                isPerformingAttack = true;
                                break;
                            } else {
                                System.out.println("Attacker does not have a block between " + difficultyToReorganizeLowChain + " and " + difficultyToNotReorganizeHighChain + "!");
                                break;
                            }
                            }
                            cursor = BlockchainUtility.getBlockInTree(cursor.getHeight() - 1, cursor.getPreviousBlockHash(), blockTree);
                    }
                    } catch (Exception e) {
                        System.out.println("Attacker failed to establish a long enough chain!");
                    }
                }
            }
        }
    }

    @Override
    public void tick() {
        // Tick all messages
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            m.tick();
            if (m.getRemainingLatencyDelay() == 0) {
                processMessage(m);
                messages.remove(i);
            }
        }

        // Tick all incoming attacker blocks
        for (int i = 0; i < attackingBlocksIncoming.size(); i++) {
            Pair<Block, Integer> pair = attackingBlocksIncoming.get(i);
            pair.setSecond(pair.getSecond() - 1);
            if (pair.getSecond() == 0) {
                if (blockTree.size() <= pair.getFirst().getHeight()) {
                    blockTree.add(new ArrayList<>());
                }

                blockTree.get(pair.getFirst().getHeight()).add(pair.getFirst());

                if (pair.getFirst().getPreviousBlockHash().equalsIgnoreCase(currentTip.getBlockHash())) {
                    setCurrentTip(pair.getFirst());
                }

                if (isPerformingAttack) {
                    for (StandardPeer sp : allStandardPeers) {
                        sp.sendMessage(new Message(peerLatencies.get(sp),
                                Message.Type.MAKE_AWARE_OF_BLOCK,
                                this,
                                pair.getFirst(),
                                true));
                    }

                    for (RoguePeer rp : allRoguePeers) {
                        rp.sendMessage(new Message(peerLatencies.get(rp),
                                Message.Type.MAKE_AWARE_OF_BLOCK,
                                this,
                                pair.getFirst(),
                                true));
                    }
                }
            }
        }

        // Tick all incoming main chain blocks (done post-attack to prevent stabilization)
        for (int i = 0; i < mainChainBlocksIncoming.size(); i++) {
            Pair<Block, Integer> pair = mainChainBlocksIncoming.get(i);
            pair.setSecond(pair.getSecond() - 1);
            if (pair.getSecond() == 0) {
                if (blockTree.size() <= pair.getFirst().getHeight()) {
                    blockTree.add(new ArrayList<>());
                }

                blockTree.get(pair.getFirst().getHeight()).add(pair.getFirst());
                setCurrentTip(pair.getFirst());

                for (StandardPeer sp : allStandardPeers) {
                    sp.sendMessage(new Message(peerLatencies.get(sp),
                            Message.Type.MAKE_AWARE_OF_BLOCK,
                            this,
                            pair.getFirst(),
                            true));
                }
            }
        }

        processLaterBlocks();
        processPendingBlocks();

        if (peerTipsChanged && highestNormalPeerTimestamp > Variables.FIRST_ELIGIBLE_FORK_TIMESTAMP && !isPerformingAttack) {
            checkForAttackPossibility();
        }
    }

    HashSet<String> allBlocks = new HashSet<>();

    private boolean hasReceivedBlockFromNetwork(Block b) {
        if (allBlocks.contains(b.getBlockHash())) {
            return true;
        }

        if (b.getHeight() < blockTree.size()) {
            List<Block> slice = blockTree.get(b.getHeight());
            for (Block block : slice) {
                if (block.getBlockHash().equalsIgnoreCase(b.getBlockHash())) {
                    return true;
                }
            }
        }

        for (Pair<Block, Integer> pair : blocksToProcess) {
            if (pair.getFirst().getBlockHash().equalsIgnoreCase(b.getBlockHash())) {
                return true;
            }
        }

        for (Block block : processedBlocksToAddLater) {
            if (block.getBlockHash().equalsIgnoreCase(b.getBlockHash())) {
                return true;
            }
        }

        return false;
    }


    private void processPendingBlocks() {
        if (blocksToProcess.size() > 0) {
            Pair<Block, Integer> blockToProcess = blocksToProcess.get(0);
            int remainingProcessingTime = blockToProcess.getSecond();

            remainingProcessingTime--;
            if (remainingProcessingTime == 0) {
                // Block is done being processed and can be applied to the chain
                Block newTip = BlockchainUtility.addBlockToTreeAndGetNewTip(blockToProcess.getFirst(), currentTip, blockTree);

                if (newTip != null) {
                    // Do nothing
                } else {
                    if (!processedBlocksToAddLaterSet.contains(blockToProcess.getFirst().getBlockHash())) {
                        processedBlocksToAddLater.add(blockToProcess.getFirst());
                        processedBlocksToAddLaterSet.add(blockToProcess.getFirst().getBlockHash());
                    }
                }

                blocksToProcess.remove(0);
            } else {
                blockToProcess.setSecond(remainingProcessingTime);
            }
        }
    }

    private void processLaterBlocks() {
        // See if any blocks that were shelved can now be processed
        if (processedBlocksToAddLater.size() > 0) {
            boolean madeProgress = false;

            while (true) {
                for (int i = processedBlocksToAddLater.size() - 1; i >= 0; i--) {
                    Block result = BlockchainUtility.addBlockToTreeAndGetNewTip(processedBlocksToAddLater.get(i), currentTip, blockTree);
                    if (result != null) {
                        madeProgress = true;
                        processedBlocksToAddLater.remove(i);
                    }
                }

                if (!madeProgress) {
                    break;
                }
            }
        }
    }

    private void processMessage(Message m) {
        Peer sendingPeer = m.getSendingPeer();
        if (m.getType() == Message.Type.START_ATTACK) {
            if (!isPerformingAttack) {
                isPerformingAttack = true;
                tellPeersToAttack(m.getBlockContent()); // flood-fill attack broadcast
                attack(m.getBlockContent(), originalAncestor);
            }
        } else if (m.getType() == Message.Type.REQUEST_BLOCK) {
            sendingPeer.sendMessage(new Message(peerLatencies.get(sendingPeer), Message.Type.GIVE_BLOCK, m.getSendingPeer(), m.getBlockContent(), true));
        } else if (m.getType() == Message.Type.GIVE_BLOCK) {
            // Rogue miner does not need to process blocks, and just trusts that other standard nodes are honest, so it can immediately add a block
            // Ignore result, the rogue peer will always keep the current tip of the attacking chain being built
            if (!hasReceivedBlockFromNetwork(m.getBlockContent())) {
                Block result = BlockchainUtility.addBlockToTreeAndGetNewTip(m.getBlockContent(), currentTip, blockTree);
                allBlocks.add(m.getBlockContent().getBlockHash());
                if (result == null) {
                    processedBlocksToAddLater.add(m.getBlockContent());
                }

                if (m.getBlockContent().getPreviousBlockHash().equalsIgnoreCase(currentTip.getBlockHash())) {
                    setCurrentTip(m.getBlockContent()); // Peer mined a block on the attacking chain
                }
            }

            // Technically, an attacker would not receive bool tip in the message, but would track the highest block
            // each peer has reported, and check if the block contained within the message is the highest reported
            // by that specific peer (meaning to the best of our knowledge, that block is currently that node's tip)
            if (m.isTip() && sendingPeer instanceof StandardPeer) {
                peerTipsChanged = true;
                standardPeerTips.put((StandardPeer) sendingPeer, m.getBlockContent());
                if (m.getBlockContent().getTimestamp() > highestNormalPeerTimestamp) {
                    highestNormalPeerTimestamp = m.getBlockContent().getTimestamp();
                }
            }
        } else if (m.getType() == Message.Type.MAKE_AWARE_OF_BLOCK) {
            if (!BlockchainUtility.knowsAboutBlock(m.getBlockContent(), blockTree)) {
                sendingPeer.sendMessage(new Message(peerLatencies.get(sendingPeer), Message.Type.REQUEST_BLOCK, this, m.getBlockContent(), true));
            }

            // Could add block to tree here instead as an optimization because our attacking nodes trust remote peers

            if (m.isTip() && sendingPeer instanceof StandardPeer) {
                peerTipsChanged = true;
                standardPeerTips.put((StandardPeer) sendingPeer, m.getBlockContent());
                if (m.getBlockContent().getTimestamp() > highestNormalPeerTimestamp) {
                    highestNormalPeerTimestamp = m.getBlockContent().getTimestamp();
                }
            }
        }
    }

    private void tellPeersToAttack(Block attackingTipToReveal) {
        for (RoguePeer r : allRoguePeers) {
            r.sendMessage(new Message(peerLatencies.get(r), Message.Type.START_ATTACK, this, attackingTipToReveal, true));
        }

        informedPeersOfAttack = true;
    }

    private void attack(Block attackingTipToReveal, Block oldPublicTipLeftInFracture) {
        if (peerId == 0) {
            return; // Bug workaround, peerId 0 has empty attacking tree
        }
        attackController.beginAttack(attackingTipToReveal, oldPublicTipLeftInFracture);

        List<Block> blocksToSend = new ArrayList<>();
        int cursorIndex = attackingTipToReveal.getHeight() - 1;
        blocksToSend.add(attackingTipToReveal);

        Block cursor = null;
        try {
            cursor = BlockchainUtility.getBlockInTree(cursorIndex, attackingTipToReveal.getPreviousBlockHash(), blockTree);
        } catch (Exception e) {
            // Workaround for occasional bug
            return;
        }

        while (!cursor.getBlockHash().equalsIgnoreCase(originalAncestor.getBlockHash())) {
            blocksToSend.add(cursor);
            cursor = BlockchainUtility.getBlockInTree(cursorIndex - 1, cursor.getPreviousBlockHash(), blockTree);
            cursorIndex--;
        }

        for (StandardPeer sp : allStandardPeers) {
            for (Block b : blocksToSend) {
                sp.sendMessage(new Message(peerLatencies.get(sp),
                        Message.Type.MAKE_AWARE_OF_BLOCK, // Attacker here is starting 3-way handshake, intentionally applying disadvantage to attacker
                        this,
                        b,
                        true));
            }
        }
    }

    @Override
    public void sendMessage(Message message) {
        messages.add(message);
    }

    @Override
    public void establishConnection(Peer peer, int latencyMS) {
        if (peer instanceof RoguePeer) {
            allRoguePeers.add((RoguePeer)peer);
        } else {
            allStandardPeers.add((StandardPeer)peer);
        }

        peerLatencies.put(peer, latencyMS);
    }

    @Override
    public boolean isConnectedToPeer(Peer peer) {
        return peerLatencies.containsKey(peer);
    }

    @Override
    public int getNumConnections() {
        return allRoguePeers.size() + allStandardPeers.size();
    }
    private void setCurrentTip(Block newCurrentTip) {
        this.currentTip = newCurrentTip;
    }
}
