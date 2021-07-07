package org.veriblock;

public class Message {
    enum Type {
        MAKE_AWARE_OF_BLOCK, // Covers NewBlockHashes (0x01) and BlockHeaders (0x04)
        REQUEST_BLOCK, // Covers GetBlockHeaders (0x03) and GetBlockBodies (0x05)
        GIVE_BLOCK, // Covers BlockBodies (0x06)
        START_ATTACK // This is part of the attacker's protocol and doesn't correspond to anything in ETH P2P
    }

    private int remainingLatencyDelay;
    private Block blockContent;
    private Type type;
    private Peer sendingPeer;
    private boolean isTip;

    public Message(int latencyDelay, Type type, Peer sendingPeer, Block blockContent, boolean isTip) {
        this.remainingLatencyDelay = latencyDelay;
        this.type = type;
        this.blockContent = blockContent;
        this.sendingPeer = sendingPeer;
        this.isTip = isTip;
    }

    public Block getBlockContent() {
        return blockContent;
    }

    public void tick() {
        remainingLatencyDelay--;
    }

    public int getRemainingLatencyDelay() {
        return remainingLatencyDelay;
    }

    public Type getType() {
        return type;
    }

    public Peer getSendingPeer() {
        return sendingPeer;
    }

    public boolean isTip() {
        return isTip;
    }
}
