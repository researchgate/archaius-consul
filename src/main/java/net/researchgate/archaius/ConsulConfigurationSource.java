package net.researchgate.archaius;

import com.ecwid.consul.transport.TransportException;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.netflix.archaius.config.polling.PollingResponse;
import org.apache.commons.codec.binary.Base64;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;

public class ConsulConfigurationSource implements Callable<PollingResponse> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ConsulConfigurationSource.class.getName());

    ConsulClientFactory clientFactory;

    private ConsulClient client;
    private String facility;
    private int length;
    private List<String> hostsList;

    public ConsulConfigurationSource(String hosts, String facility, ConsulClientFactory clientFactory) {
        this.clientFactory = clientFactory;
        init(hosts, facility);
    }

    public ConsulConfigurationSource(String hosts, String facility) {
        this.clientFactory = new ConsulClientFactory();
        init(hosts, facility);
    }

    public ConsulConfigurationSource(String facility) {
        this.clientFactory = new ConsulClientFactory();
        init(null, facility);
    }

    private void init(String hosts, String facility) {
        if (hosts != null) {
            String[] splitHosts = hosts.split(",");
            hostsList = Arrays.asList(splitHosts);
        } else {
            hostsList = null;
        }
        this.facility = facility;
        this.length = facility.length() + (facility.charAt(0) == '/' ? 0 : 1) - (facility.endsWith("/") ? 1 : 0);
    }

    @Override
    public PollingResponse call() throws Exception {
        Map<String, String> properties = new HashMap<>();
        if (client != null || setClient()) {
            try {
                Response<List<GetValue>> kvValues = client.getKVValues(facility);
                if (kvValues.getValue() != null && !kvValues.getValue().isEmpty()) {
                    for (GetValue value : kvValues.getValue()) {
                        if (!value.getKey().endsWith("/")) {
                            byte[] decoded = Base64.decodeBase64(value.getValue());
                            properties.put(value.getKey().substring(length), new String(decoded));
                        }
                    }
                }
                return PollingResponse.forSnapshot(properties);
            } catch (TransportException tex) {
                LOGGER.error("Error while polling configuration.", tex);
                client = null;
            } catch (Exception ex) {
                LOGGER.error("Error while polling configuration.", ex);
                client = null;
            }
        }
        return PollingResponse.noop();
    }

    private boolean setClient() {
        if (hostsList != null) {
            ConsulClient newClient;
            Collections.shuffle(hostsList);
            for (String hostWithPort : hostsList) {
                String[] host = hostWithPort.split(":");
                if (host.length == 2) {
                    newClient = clientFactory.getClient(host[0], Integer.valueOf(host[1]));
                } else {
                    newClient = clientFactory.getClient(host[0]);
                }
                try {
                    if (newClient.getKVValues(facility) != null) {
                        client = newClient;
                        return true;
                    }
                } catch (TransportException tex) {
                    LOGGER.error("Consul service not reachable. " + hostWithPort, tex);
                }
            }
        } else {
            try {
                client = clientFactory.getClient();
            } catch (Exception ex) {
                return false;
            }
            return true;
        }
        return false;
    }

}
