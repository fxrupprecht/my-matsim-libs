package org.matsim.contrib.bicycle.run;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.api.internal.NetworkRunnable;

import java.util.*;
import java.util.stream.Collectors;

public final class NetworkCleanerModeSpecific implements NetworkRunnable {

    private final Set<String> modesToClean; // null/empty -> alle vorhandenen Modi im Netz

    public NetworkCleanerModeSpecific() {
        this.modesToClean = null;
    }

    public NetworkCleanerModeSpecific(Set<String> modesToClean) {
        this.modesToClean = (modesToClean == null || modesToClean.isEmpty()) ? null : new HashSet<>(modesToClean);
    }

    @Override
    public void run(Network network) {
        // Get all modes
        Set<String> allModes = new HashSet<>();
        for (Link l : network.getLinks().values()) {
            if (l.getAllowedModes() != null) allModes.addAll(l.getAllowedModes());
        }
        Set<String> targetModes = (modesToClean == null) ? allModes : modesToClean;

        // Get biggest cluster per mode
        Map<String, Set<Id<Node>>> biggestClusterPerMode = new HashMap<>();
        for (String mode : targetModes) {
            Set<Id<Node>> cluster = findBiggestClusterForMode(network, mode);
            biggestClusterPerMode.put(mode, cluster);
        }

        // Remove mode from link if link is not in the modes biggest cluster
        List<Id<Link>> linksToRemove = new ArrayList<>();
        for (Link l : network.getLinks().values()) {
            Set<String> allowedModes = new HashSet<>(l.getAllowedModes());
            for (String mode : new ArrayList<>(allowedModes)) {
                Set<Id<Node>> cluster = biggestClusterPerMode.get(mode);
                // Link is in the cluster if fromNode and toNode are in the cluster
                if (cluster == null) continue;
                if (!(cluster.contains(l.getFromNode().getId()) && cluster.contains(l.getToNode().getId()))) {
                    allowedModes.remove(mode);
                }
            }
            if (allowedModes.isEmpty()) {
                linksToRemove.add(l.getId());
            } else {
                l.setAllowedModes(allowedModes);
            }
        }
        for (Id<Link> id : linksToRemove) {
            network.removeLink(id);
        }

        // 4) Verwaiste Knoten entfernen (keine in- und out-links)
        List<Id<Node>> nodesToRemove = network.getNodes().values().stream()
                .filter(n -> n.getInLinks().isEmpty() && n.getOutLinks().isEmpty())
                .map(Node::getId)
                .collect(Collectors.toList());
        for (Id<Node> id : nodesToRemove) {
            network.removeNode(id);
        }
    }


    // Finds mode specific biggest mode specific cluster
    // 
    private Set<Id<Node>> findBiggestClusterForMode(Network network, String mode) {
        Set<Id<Node>> visited = new HashSet<>();
        Set<Id<Node>> best = new HashSet<>();

        for (Node start : network.getNodes().values()) {
            if (visited.contains(start.getId())) continue;

            // Forward 
            Set<Id<Node>> forward = bfsForward(network, start, mode);
            // Backward
            Set<Id<Node>> backward = bfsBackward(network, start, mode);

            Set<Id<Node>> scc = new HashSet<>(forward);
            scc.retainAll(backward);

            visited.addAll(forward);
            visited.addAll(backward);

            if (scc.size() > best.size()) best = scc;

            if (best.size() >= network.getNodes().size() - visited.size()) {
            }
        }
        return best;
    }

    private Set<Id<Node>> bfsForward(Network network, Node start, String mode) {
        Set<Id<Node>> reached = new HashSet<>();
        ArrayDeque<Node> q = new ArrayDeque<>();
        reached.add(start.getId());
        q.add(start);
        while (!q.isEmpty()) {
            Node n = q.removeFirst();
            for (Link l : n.getOutLinks().values()) {
                if (l.getAllowedModes() == null || !l.getAllowedModes().contains(mode)) continue;
                Node to = l.getToNode();
                if (reached.add(to.getId())) q.add(to);
            }
        }
        return reached;
        }

    private Set<Id<Node>> bfsBackward(Network network, Node start, String mode) {
        Set<Id<Node>> reached = new HashSet<>();
        ArrayDeque<Node> q = new ArrayDeque<>();
        reached.add(start.getId());
        q.add(start);
        while (!q.isEmpty()) {
            Node n = q.removeFirst();
            for (Link l : n.getInLinks().values()) {
                if (l.getAllowedModes() == null || !l.getAllowedModes().contains(mode)) continue;
                Node from = l.getFromNode();
                if (reached.add(from.getId())) q.add(from);
            }
        }
        return reached;
    }
}