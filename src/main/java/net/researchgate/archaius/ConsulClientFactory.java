package net.researchgate.archaius;

import com.ecwid.consul.v1.ConsulClient;

public class ConsulClientFactory {

    public ConsulClient getClient(String host) {
        return new ConsulClient(host);
    }

    public ConsulClient getClient(String host, int port) {
        return new ConsulClient(host, port);
    }
}
