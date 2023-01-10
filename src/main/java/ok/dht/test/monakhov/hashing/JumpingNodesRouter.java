package ok.dht.test.monakhov.hashing;

import one.nio.util.Hash;

import java.util.List;
import java.util.Random;

public class JumpingNodesRouter implements NodesRouter {
    private final List<String> urls;

    public JumpingNodesRouter(List<String> urls) {
        this.urls = urls;
    }

    @Override
    public String getNodeUrl(String key) {
        return urls.get(getNodeIndex(Hash.murmur3(key)));
    }

    @Override
    public String[] getNodeUrls(String key, int number) {
        String[] replicateUrls = new String[number];
        int start = getNodeIndex(Hash.murmur3(key));

        for (int i = 0; i < number; i++) {
            replicateUrls[i] = urls.get((start + i) % urls.size());
        }

        return replicateUrls;
    }

    private int getNodeIndex(int key) {
        var random = new Random();
        random.setSeed(key);
        int b = -1;
        int j = 0;
        while (j < urls.size()) {
            b = j;
            double r = random.nextDouble();
            j = (int) Math.floor((b + 1) / r);
        }
        return b;
    }
}
