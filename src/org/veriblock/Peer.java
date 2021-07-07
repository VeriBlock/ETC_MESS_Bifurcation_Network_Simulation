package org.veriblock;

public interface Peer {
    public Block getTip();
    public void tick();
    public void sendMessage(Message message);
    public void establishConnection(Peer peer, int latencyMS);
    public boolean isConnectedToPeer(Peer peer);
    public int getNumConnections();
    public int getPeerId();
}
