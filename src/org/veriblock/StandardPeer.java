package org.veriblock;


import java.math.BigInteger;
import java.util.*;

public class StandardPeer implements Peer {
    private List<List<Block>> blockTree;
    private List<Message> messages;
    private Block currentTip;
    private List<Peer> allPeers;
    private HashMap<Peer, Integer> peerLatencies;
    private List<Pair<Block, Integer>> blocksToProcess;
    private List<Block> processedBlocksToAddLater = new ArrayList<>();
    private HashSet<String> processedBlocksToAddLaterSet = new HashSet<>();
    private int peerId;

    private HashMap<Block, Integer> firstHeardOfBlock = new HashMap<>();

    private int peerProcessingSpeed = new Random().nextInt(50) + 25;

    // REMOVE ME
    public boolean isPool = false;

    public StandardPeer(Block genesisBlock, int peerId) {
        blockTree = new ArrayList<>();
        blockTree.add(new ArrayList<>());
        blockTree.get(0).add(genesisBlock);
        setCurrentTip(genesisBlock);

        messages = new ArrayList<>();
        blocksToProcess = new ArrayList<>();

        allPeers = new ArrayList<>();
        peerLatencies = new HashMap<>();

        this.peerId = peerId;
    }

    @Override
    public Block getTip() {
        return currentTip;
    }

    private void processLaterBlocks() {
        // See if any blocks that were shelved can now be processed
        if (processedBlocksToAddLater.size() > 0) {
            boolean madeProgress = false;

            while (true) {
                for (int i = processedBlocksToAddLater.size() - 1; i >= 0; i--) {
                    if (processedBlocksToAddLater.get(i).getIsAttackerReorg()) {
                        System.out.println("Peer " + peerId + " heard about attacker block when current local tip is " + currentTip.getHeight());
                    }
                    Block result = BlockchainUtility.addBlockToTreeAndGetNewTip(processedBlocksToAddLater.get(i), currentTip, blockTree);
                    if (result != null) {
                        madeProgress = true;
                        setCurrentTip(result);

                        // Send blocks to all peers
                        for (Peer p : allPeers) {
                            p.sendMessage(new Message(
                                    peerLatencies.get(p),
                                    Message.Type.MAKE_AWARE_OF_BLOCK,
                                    this,
                                    processedBlocksToAddLater.get(i),
                                    (processedBlocksToAddLater.get(i).getBlockHash().equalsIgnoreCase(currentTip.getBlockHash()))));
                        }

                        processedBlocksToAddLater.remove(i);
                    }
                }

                if (!madeProgress) {
                    break;
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

        processLaterBlocks();
        processPendingBlocks();
    }

    private void processPendingBlocks() {
        if (blocksToProcess.size() > 0) {
            Pair<Block, Integer> blockToProcess = blocksToProcess.get(0);
            int remainingProcessingTime = blockToProcess.getSecond();

            remainingProcessingTime--;
            if (remainingProcessingTime <= 0) {
                // Block is done being processed and can be applied to the chain
                Block newTip = BlockchainUtility.addBlockToTreeAndGetNewTip(blockToProcess.getFirst(), currentTip, blockTree);

                if (blockToProcess.getFirst().getIsAttackerReorg()) {
                    System.out.println("Peer " + peerId + " heard about attacker block when current local tip is " + currentTip.getHeight());
                }

                if (newTip != null) {
                    Block commonAncestor = BlockchainUtility.getCommonAncestor(newTip, currentTip, blockTree);
                    if (commonAncestor.getHeight() < currentTip.getHeight() - 10) {
                        System.out.println("[Tick " + Main.tick + "]: Peer " + peerId + " experienced a deep reorg. " +
                                "Now on tip: " + newTip.getBlockHash() + " @ " +
                                newTip.getHeight() + " with " +
                                BlockchainUtility.getCumulativeDifficultyForChainSegment(
                                        newTip,
                                        commonAncestor,
                                        blockTree) + " cumulative difficulty.");
                    }

                    setCurrentTip(newTip);

                    // Send blocks to all peers
                    for (Peer p : allPeers) {
                        p.sendMessage(new Message(peerLatencies.get(p),
                                Message.Type.MAKE_AWARE_OF_BLOCK,
                                this,
                                blockToProcess.getFirst(),
                                (blockToProcess.getFirst().getBlockHash().equalsIgnoreCase(currentTip.getBlockHash()))));
                    }
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

    HashSet<String> allBlocks = new HashSet<>();

    @Override
    public int getPeerId() {
        return peerId;
    }

    public BigInteger getCumulativeDifficultyOfChain() {
        return BlockchainUtility.getCumulativeDifficultyForChainSegment(currentTip, blockTree.get(0).get(0), blockTree);
    }

    private void processMessage(Message m) {
        Peer sendingPeer = m.getSendingPeer();
        if (m.getType() == Message.Type.REQUEST_BLOCK) {
            sendingPeer.sendMessage(new Message(peerLatencies.get(sendingPeer),
                    Message.Type.GIVE_BLOCK,
                    m.getSendingPeer(), m.getBlockContent(),
                    (m.getBlockContent().getBlockHash().equalsIgnoreCase(currentTip.getBlockHash()))));

        } else if (m.getType() == Message.Type.GIVE_BLOCK) {
            // Block takes time to process
            Block receivedBlock = m.getBlockContent();


            if (!hasReceivedBlockFromNetwork(receivedBlock)) {
                int processingTime = (int)(peerProcessingSpeed * receivedBlock.getProcessingWeight());
                blocksToProcess.add(new Pair<>(receivedBlock, processingTime));
                allBlocks.add(receivedBlock.getBlockHash());
                firstHeardOfBlock.put(receivedBlock, Main.tick);
            }

        } else if (m.getType() == Message.Type.MAKE_AWARE_OF_BLOCK) {
            if (!BlockchainUtility.knowsAboutBlock(m.getBlockContent(), blockTree)) {
                sendingPeer.sendMessage(new Message(
                        peerLatencies.get(sendingPeer),
                        Message.Type.REQUEST_BLOCK,
                        this,
                        m.getBlockContent(),
                        false));
            }
        }
    }

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

    @Override
    public void sendMessage(Message message) {
        messages.add(message);
    }

    @Override
    public void establishConnection(Peer peer, int latencyMS) {
        allPeers.add(peer);
        peerLatencies.put(peer, latencyMS);
    }

    @Override
    public boolean isConnectedToPeer(Peer peer) {
        return peerLatencies.containsKey(peer);
    }

    @Override
    public int getNumConnections() {
        return allPeers.size();
    }

    public void mineBlock(Block mined) {
        // Because the miner mined their block, they don't need to spend any time processing it. They immediately send out to peers
        Block newTip = BlockchainUtility.addBlockToTreeAndGetNewTip(mined, currentTip, blockTree);
        setCurrentTip(newTip);

        allBlocks.add(mined.getBlockHash());

        firstHeardOfBlock.put(mined, Main.tick);

        for (Peer p : allPeers) {
            p.sendMessage(new Message(peerLatencies.get(p),
                    Message.Type.MAKE_AWARE_OF_BLOCK,
                    this,
                    mined,
                    true));
        }
    }

    private void setCurrentTip(Block newCurrentTip) {
        if ((Main.tick - newCurrentTip.getMinedAtTick()) > 2000) {
            String timecode = Utility.encodeTimestamp(Main.tick);
            System.out.println("[Time " + timecode + "]: Peer " + peerId + " updated current tip to " +
                    newCurrentTip.getHeight() + " " + (Main.tick - newCurrentTip.getMinedAtTick()) +
                    " ticks after it was made. Originally heard about that block " + (firstHeardOfBlock.get(newCurrentTip) - newCurrentTip.getMinedAtTick()) + " ticks after it was made.");
        }
        this.currentTip = newCurrentTip;
    }
}
