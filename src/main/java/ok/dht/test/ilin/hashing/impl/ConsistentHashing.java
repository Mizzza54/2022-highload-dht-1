package ok.dht.test.ilin.hashing.impl;

import ok.dht.test.ilin.config.ConsistentHashingConfig;
import ok.dht.test.ilin.hashing.HashEvaluator;

import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

public class ConsistentHashing {
    private final NavigableMap<Integer, VirtualNode> virtualNodes;
    private final HashEvaluator hashEvaluator;

    public ConsistentHashing(List<String> topology, ConsistentHashingConfig config) {
        this.virtualNodes = initVirtualNodes(topology, config.virtualNodeCount, config.hashEvaluator);
        this.hashEvaluator = config.hashEvaluator;
    }

    public String getServerAddressFromKey(String key) {
        int hash = hashEvaluator.hash(key);
        Integer consistentKey = virtualNodes.ceilingKey(hash);
        VirtualNode virtualNode;
        if (consistentKey == null) {
            virtualNode = virtualNodes.firstEntry().getValue();
        } else {
            virtualNode = virtualNodes.get(consistentKey);
        }
        if (virtualNode != null) {
            return virtualNode.address;
        }
        return null;
    }

    public Set<String> getServerAddressesForKey(String key, int size) {
        int hash = hashEvaluator.hash(key);
        var currentNode = virtualNodes.ceilingEntry(hash);
        Set<String> result = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            if (currentNode == null) {
                currentNode = virtualNodes.firstEntry();
            }
            result.add(currentNode.getValue().address);
            if (i < size - 1) {
                while (result.contains(currentNode.getValue().address)) {
                    currentNode = virtualNodes.higherEntry(currentNode.getKey());
                    if (currentNode == null) {
                        currentNode = virtualNodes.firstEntry();
                    }
                }
            }
        }
        assert result.size() == size;
        return result;
    }

    private NavigableMap<Integer, VirtualNode> initVirtualNodes(
        List<String> topology,
        int virtualNodeCount,
        HashEvaluator hashEvaluator
    ) {
        NavigableMap<Integer, VirtualNode> virtualNodesForInit = new TreeMap<>();
        for (String nodeAddress : topology) {
            for (int i = 0; i < virtualNodeCount; i++) {
                VirtualNode node = new VirtualNode(nodeAddress, i);
                virtualNodesForInit.put(hashEvaluator.hash(node.name), node);
            }
        }
        return virtualNodesForInit;
    }

    private static class VirtualNode {
        private static final String VIRTUAL_NODE_NAME_PREFIX = "VN";
        String name;
        String address;

        public VirtualNode(String address, int num) {
            this.name = VIRTUAL_NODE_NAME_PREFIX + address + num;
            this.address = address;
        }
    }
}
