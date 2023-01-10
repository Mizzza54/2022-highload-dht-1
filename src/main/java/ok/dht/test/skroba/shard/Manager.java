package ok.dht.test.skroba.shard;

import java.util.List;

public interface Manager {
    Node getUrlById(String id);
    
    int clusterSize();
    
    String selfUrl();
    
    List<String> getUrls(String id, int size);
}
