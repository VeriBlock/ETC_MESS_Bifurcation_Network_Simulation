package org.veriblock;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class Main extends Thread {
    public static void main(String[] args) {
        Pair<Integer, Integer> attack = runAttack();
        if (attack.getFirst() != 0 && attack.getSecond() != 0) {
            System.out.println("Success: " + attack.getSecond() + "/" + (attack.getFirst() + attack.getSecond()) + " peers were split from the network.");
        } else {
            if (attack.getSecond() == 0) {
                System.out.println("Failure: No legitimate nodes were reorganized onto the fork!");
            } else {
                System.out.println("Failure: All legitimate nodes were reorganized onto the fork!");
            }
        }
    }

    public static int tick = 0;
    public static Pair<Integer, Integer> runAttack() {
        Block genesisBlock = new Block(Variables.STARTING_NETWORK_DIFFICULTY,
                0,
                0,
                BlockchainUtility.randomBlockHash(),
                "0000000000000000000000000000000000000000000000000000000000000000",
                0, 0, false, Variables.STARTING_NETWORK_DIFFICULTY);

        Random r = new Random();

        // Set up all normal peers
        List<StandardPeer> standardPeers = new ArrayList<>();
        for (int i = 0; i < Variables.NUMBER_OF_STANDARD_PEERS; i++) {
            standardPeers.add(new StandardPeer(genesisBlock, i));
        }

        // Connect the peers
        for (int i = 0; i < standardPeers.size(); i++) {
            Peer toConnect = standardPeers.get(i);
            int numConnections = Variables.MINIMUM_NUMBER_OF_STANDARD_PEER_CONNECTIONS
                    + r.nextInt(Variables.NUMBER_OF_RANDOM_ADDITIONAL_STANDARD_PEER_CONNECTIONS);
            for (int j = 0; j < numConnections; j++) {
                Peer selected = standardPeers.get(r.nextInt(standardPeers.size()));
                if (!toConnect.isConnectedToPeer(selected) && toConnect != selected) {
                    int latency = r.nextInt(Variables.MAXIMUM_STANDARD_PEER_LATENCY_MS);
                    toConnect.establishConnection(selected, latency);
                    selected.establishConnection(toConnect, latency);
                } else {
                    j--;
                }
            }
        }

        if (Variables.ASSUME_POOLS) {
            ArrayList<StandardPeer> pools = new ArrayList<StandardPeer>();
            for (int i = 0; i < 3; i++) {
                int highest = 0;
                StandardPeer highestPeer = null;
                for (StandardPeer p : standardPeers) {
                    if (!pools.contains(p)) {
                        if (p.getNumConnections() > highest) {
                            highest = p.getNumConnections();
                            highestPeer = p;
                        }
                    }
                }

                highestPeer.isPool = true;
                pools.add(highestPeer);
            }
        }

        // Set up the attacking peers
        List<RoguePeer> roguePeersList = new ArrayList<>();
        for (int i = 0; i < Variables.NUMBER_OF_ATTACKING_PEERS; i++) {
            roguePeersList.add(new RoguePeer(genesisBlock, i));
        }

        // Peer them to each other
        for (int i = 0; i < roguePeersList.size(); i++) {
            RoguePeer rp = roguePeersList.get(i);
            for (int j = i + 1; j < roguePeersList.size(); j++) {
                int latency = r.nextInt(Variables.MAXIMUM_ROGUE_TO_ROGUE_PEER_LATENCY_MS);
                rp.establishConnection(roguePeersList.get(j), latency);
                roguePeersList.get(j).establishConnection(rp, latency);
            }
        }

        // Peer them to the regular network
        for (int i = 0; i < roguePeersList.size(); i++) {
            int numConnections = Variables.MINIMUM_NUMBER_OF_ROGUE_PEER_CONNECTIONS
                    + r.nextInt(Variables.NUMBER_OF_RANDOM_ADDITIONAL_ROGUE_PEER_CONNECTIONS);
            RoguePeer roguePeer = roguePeersList.get(i);

            for (int j = 0; j < numConnections; j++) {
                StandardPeer toPeerWith = standardPeers.get(r.nextInt(standardPeers.size()));

                if (!toPeerWith.isConnectedToPeer(roguePeer)) {
                    int latency = r.nextInt(Variables.MAXIMUM_ROGUE_TO_STANDARD_PEER_LATENCY_MS);
                    toPeerWith.establishConnection(roguePeer, latency);
                    roguePeer.establishConnection(toPeerWith, latency);
                } else {
                    j--;
                }
            }
        }

        HashMap<RoguePeer, Integer> roguePeerLatencies = new HashMap<>();
        roguePeerLatencies.put(roguePeersList.get(0), 0); // Attack controller has a local node
        for (int i = 1; i < roguePeersList.size(); i++) {
            roguePeerLatencies.put(roguePeersList.get(i), r.nextInt(Variables.MAXIMUM_ROGUE_TO_ROGUE_PEER_LATENCY_MS));
        }

        NormalNetworkController controller = new NormalNetworkController(Variables.STARTING_NETWORK_HASHRATE, standardPeers);
        AttackController attackController = new AttackController(Variables.ATTACKER_HASHRATE, genesisBlock, roguePeersList, roguePeerLatencies);

        for (tick = 0; tick < 1000 * 3600 * 6 /* 6 hours, note program will exist at 1000 seconds as currently configured */; tick++) {
            if (tick > 1000001) {
                return new Pair<>(100, 0);
            }

            for (Peer sp : standardPeers) {
                sp.tick();
            }

            for (RoguePeer rp : roguePeersList) {
                rp.tick();
            }

            controller.tick(tick);
            attackController.tick(tick);

            if (tick % 1000 == 0) {
                Block lowestRogueBlock = roguePeersList.get(0).getTip();
                for (int i = 0; i < roguePeersList.size(); i++) {
                    if (roguePeersList.get(i).getTip().getHeight() < lowestRogueBlock.getHeight()) {
                        lowestRogueBlock = roguePeersList.get(i).getTip();
                    }
                }

                Block oldestPublicBlock = standardPeers.get(0).getTip();
                BigInteger lowestPublicChainCumulativeDifficulty = standardPeers.get(0).getCumulativeDifficultyOfChain();

                Block newestPublicBlock = standardPeers.get(0).getTip();
                BigInteger highestPublicChainCumulativeDifficulty = standardPeers.get(0).getCumulativeDifficultyOfChain();

                for (int i = 1; i < standardPeers.size(); i++) {
                    Block peerTip = standardPeers.get(i).getTip();
                    BigInteger cumulativeDifficulty = standardPeers.get(i).getCumulativeDifficultyOfChain();
                    if (peerTip.getHeight() < oldestPublicBlock.getHeight()) {
                        oldestPublicBlock = peerTip;
                    }

                    if (cumulativeDifficulty.compareTo(lowestPublicChainCumulativeDifficulty) < 0) {
                        lowestPublicChainCumulativeDifficulty = cumulativeDifficulty;
                    }

                    if (peerTip.getHeight() > newestPublicBlock.getHeight()) {
                        newestPublicBlock = peerTip;
                    }

                    if (cumulativeDifficulty.compareTo(highestPublicChainCumulativeDifficulty) > 0) {
                        highestPublicChainCumulativeDifficulty = cumulativeDifficulty;
                    }
                }

                if (!attackController.wasAttackSent()) {
                    String timecode = Utility.encodeTimestamp(tick);
                    System.out.println("[Time: " + timecode + "]: Lowest standard peer block: " + oldestPublicBlock.getBlockHash() + " @ " + oldestPublicBlock.getHeight() + " with cumulative difficulty " + lowestPublicChainCumulativeDifficulty + " and required difficulty to fracture " + new BigDecimal(lowestPublicChainCumulativeDifficulty).multiply(BigDecimal.valueOf(MESS.getMultiplier(oldestPublicBlock.getTimestamp()))));
                    System.out.println("[Time: " + timecode + "]: Highest standard peer block: " + newestPublicBlock.getBlockHash() + " @ " + newestPublicBlock.getHeight() + " with cumulative difficulty " + highestPublicChainCumulativeDifficulty);
                    System.out.println("[Time: " + timecode + "]: Attacker's chain tip: " + attackController.getCurrentTip().getBlockHash() + " @ " + attackController.getCurrentTip().getHeight() + " with cumulative difficulty " + attackController.getCumulativeDifficultyOfAttackingChain());
                } else {
                    HashMap<Block, List<StandardPeer>> peerHeights = new HashMap<>();
                    for (StandardPeer sp : standardPeers) {
                        Block peerTip = sp.getTip();
                        if (!peerHeights.containsKey(peerTip)) {
                            peerHeights.put(peerTip, new ArrayList<>());
                        }

                        peerHeights.get(peerTip).add(sp);
                    }

                    int peersOnLegitimate = 0;
                    int peersOnFork = 0;
                    for (Block b : peerHeights.keySet()) {
                        List<StandardPeer> peersAtHeight = peerHeights.get(b);
                        BigInteger cumulativeDifficultyOfChain = peersAtHeight.get(0).getCumulativeDifficultyOfChain();
                        double MESSMultiplier = MESS.getMultiplier(peersAtHeight.get(0).getTip().getTimestamp());
                        System.out.print("Peers at block " + b.getBlockHash() + " @ " +
                                b.getHeight() + (b.getIsAttackerReorg() ? "[FORK]" : "[MAIN]") +
                                " {cd: " + cumulativeDifficultyOfChain +
                                ", MESS_mp: " + MESSMultiplier +
                                ", reorgNeeds: " + (new BigDecimal(cumulativeDifficultyOfChain).multiply(BigDecimal.valueOf(MESSMultiplier)).toBigInteger()) + "} [");
                        for (int i = 0; i < peersAtHeight.size(); i++) {
                            if (b.getIsAttackerReorg()) {
                                peersOnFork++;
                            } else {
                                peersOnLegitimate++;
                            }
                            System.out.print(peersAtHeight.get(i).getPeerId());

                            if (i != peersAtHeight.size() - 1) {
                                System.out.print(", ");
                            } else {
                                System.out.println("]");
                            }
                        }
                    }


                    if (tick >= 1000000) {
                        return new Pair<>(peersOnLegitimate, peersOnFork);
                    }
                }
            }
        }

        return null;
    }
}
