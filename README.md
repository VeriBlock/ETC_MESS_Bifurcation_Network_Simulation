# ETC_MESS_Bifurcation_Network_Simulation

## Introduction
Please see https://www.veriblock.org/wp-content/uploads/2021/07/ETC-MESS-Network-Bifurcation-Exploit-Public-Disclosure-v1.pdf for a detailed description of the theory behind the attack, more exensive details about the simulation setup, attack cost calculation methodology, and additional optimizations an attacker can use to selectively split portions of the network, create more symmetrical bifurcations, and potentially save money with dyanmic hashrate allocation.

This simulation simulates a live blockchain network with MESS's subjective scoring implemented, and demonstrates how an attacker can successfully coordinate an attack on the network by building an attack chain and propagating (a specific part of) their attacking chain at a moment where a block is propagating across the legitimate network to bifurcate the network. Post-bifurcation, the attacker can point hashrate in a clever fashion to stabilize the split, which post-stabalization can be perpetually maintained at a maximum cost of 3.226% of the total network hashrate.

The simulation is preconfigured to run with the real-world assumptions explained in the MESS disclosure for the simulation configuration.

## Running Simulation
In order to run the simulation yourself, clone this repo and open it in IntelliJ, and run "Main.java".

You should start seeing output like this:
```
[Time: 00:00:00]: Lowest standard peer block: 00000000000000001499513B160DA8D310D4A56E9573F5D9CEBA843848A893E7 @ 0 with cumulative difficulty 13000000000000 and required difficulty to fracture 13000000000000.0
[Time: 00:00:00]: Highest standard peer block: 00000000000000001499513B160DA8D310D4A56E9573F5D9CEBA843848A893E7 @ 0 with cumulative difficulty 13000000000000
```

After approximately 14 minutes of simulation time (Time: 00:14:00), you will see lines like:

```
[Time: 00:14:20]: Highest standard peer block: 00000000000000005C5DD89E8E0F26009E4F80F09EC436F70CAD4D8EA3C356F3 @ 66 with cumulative difficulty 871113464319632
[Time: 00:14:20]: Attacker's chain tip: 000000000000000053D254E6803392A21841CB016EC37E84BEA139E94912E228 @ 80 with cumulative difficulty 1060833387553531
Attacker has the correct block at height 67! Beginning attack...
```

At which point the attacker's network of nodes are coordinating to propagate a subchain (in this case, up to block 67 [out of 80] of the attacker's generated chain) while a block on the legitimate network is propagating which created the attack vulnerability.

After a few more minutes of simulation time, the simulation will end, with lines like:

```
Peers at block 000000000000000014BD8E970A3B014527D29938EC1012BFC0585145F3C7C89E @ 70[FORK] {cd: 938952554505365, MESS_mp: 1.1124061933000124, reorgNeeds: 1044496636846635} [1, 8, 10, 12, 13, 19, 21, 23, 27, 47, 56, 58, 68, 83, 85, 88, 93, 96]
Peers at block 0000000000000000AE5ABDCF1CC6A49B77129D92E3D96EB363B7B52FDC06C3ED @ 70[MAIN] {cd: 920187505642527, MESS_mp: 1.1124061933000124, reorgNeeds: 1023622280274037} [0, 2, 3, 4, 5, 6, 7, 9, 11, 14, 15, 16, 17, 18, 20, 22, 24, 25, 26, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 48, 49, 50, 51, 52, 53, 54, 55, 57, 59, 60, 61, 62, 63, 64, 65, 66, 67, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 84, 86, 87, 89, 90, 91, 92, 94, 95, 97, 98, 99]
Success: 18/100 peers were split from the network.
```
Meaning the simulation has completed, and in this instace 18% of the peers on the network were bifurcated. Note you can also see how the attack was stabalized; peres on the fork would need the main chain to progress to a cumulative difficulty of 1044496636846635 but the main chain is only at 920187505642527, and peers on the main chain would need the fork to progress to a cumulative difficulty of 1023622280274037 but the fork chain is only at 938952554505365.

If the simulation were to run longer, MESS_mp would continue to increase up to a maximum of 31.

## Simulation Modification
Inside "Variables.java" are a number of parameters that can be modified to observe how the attack behaves in different network configurations.

Of particular interest are:
* The number of standard versus attacking peers
* How tightly connected the legitimate network is
* How tightly connected the attacker network is to the legitimate network
* The latency between legitimate nodes, legitimate and attacker nodes, and attacker nodes
* Whether legitimate mining is fully decentralized (any legitimate node can origin a block), or simulates pool behavior (blocks origined at 3-most connected nodes)
* How much hashrate the attacker has relative to the legitimate network

Additionally if you have a specific network topology you want to test, you can replace the legitimate-to-legitimate and attacker-to-legitimate peering with another peering scheme.

## Known Bugs
Occasionally, an edge-case in how the simulation handles message propagation will result in an infinite loop, causing the simulation to freeze. This has nothing to do with the attack's actual viability, just a simulation edge-case. To proceed, just stop and re-run the simulation.

Additionally some assumptions are made for implementation ease, and certain abnormal simulation configurations may cause unexpected bugs.

## Additional Notes
This simulation uses a pure flood-fill message dissemination method (ensuring geodesic propagation), which is actually worse for the attacker than the sqrt(n) optimistic and 1-sqrt(n) pessimistic (with a 5-way communication) block dissemination.
