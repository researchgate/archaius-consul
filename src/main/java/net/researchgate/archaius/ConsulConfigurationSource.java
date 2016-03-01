package net.researchgate.archaius;

import com.ecwid.consul.transport.TransportException;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.model.CatalogService;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.google.common.io.BaseEncoding;
import com.netflix.config.PollResult;
import com.netflix.config.PolledConfigurationSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsulConfigurationSource implements PolledConfigurationSource {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ConsulConfigurationSource.class.getName());
    static final String CONSUL_SERVICE_NAME = "consul";

    ConsulClientFactory clientFactory;

    private ConsulClient client;
    private String facility;
    private int length;
    private String hostsList;
    private List<CatalogService> consulServices;

    public ConsulConfigurationSource(String hostsList, String facility, ConsulClientFactory clientFactory) {
        this.clientFactory = clientFactory;
        init(hostsList, facility);
    }

    public ConsulConfigurationSource(String hostsList, String facility) {
        this.clientFactory = new ConsulClientFactory();
        init(hostsList, facility);
    }

    private void init(String hostsList, String facility) {
        this.hostsList = hostsList;
        this.facility = facility;
        this.length = facility.length() + (facility.charAt(0) == '/' ? 0 : 1) - (facility.endsWith("/") ? 1 : 0);
    }

    @Override
    public PollResult poll(boolean initial, Object checkPoint) {
        Map<String, Object> properties = new HashMap<>();
        if (client != null || setClient()) {
            try {
                Response<List<GetValue>> kvValues = client.getKVValues(facility);
                if (kvValues.getValue() != null && !kvValues.getValue().isEmpty()) {
                    consulServices = client.getCatalogService(CONSUL_SERVICE_NAME, QueryParams.DEFAULT).getValue();
                    for (GetValue value : kvValues.getValue()) {
                        if (!value.getKey().endsWith("/")) {
                            byte[] decoded = BaseEncoding.base64().decode(value.getValue());
                            properties.put(value.getKey().substring(length), new String(decoded));
                        }
                    }
                }
                return PollResult.createFull(properties);
            } catch (TransportException tex) {
                LOGGER.error("Error while polling configuration. Try another server if available.", tex);
                if (setClient()) {
                    return poll(initial, checkPoint);
                }
            } catch (Exception ex) {
                LOGGER.error("Error while polling configuration.", ex);
            }
        }
        return null;
    }

    private boolean setClient() {
        if (consulServices != null) {
            for (CatalogService consulService : consulServices) {
                ConsulClient newClient;
                try {
                    newClient = clientFactory.getClient(consulService.getAddress(), consulService.getServicePort());
                    Response<List<CatalogService>> catalogService = newClient.getCatalogService(CONSUL_SERVICE_NAME, QueryParams.DEFAULT);
                    if (catalogService.getValue() != null) {
                        consulServices = catalogService.getValue();
                        return true;
                    }
                } catch (TransportException tex) {
                    LOGGER.error("Consul service not reachable. " + consulService.getNode(), tex);
                }
            }
        } else {
            String[] hostsWithPort = hostsList.split(",");
            ConsulClient newClient;
            for (String hostWithPort : hostsWithPort) {
                String[] host = hostWithPort.split(":");
                if (host.length == 2) {
                    newClient = clientFactory.getClient(host[0], Integer.valueOf(host[1]));
                } else {
                    newClient = clientFactory.getClient(host[0]);
                }
                try {
                    if (newClient.getHealthChecksForService(CONSUL_SERVICE_NAME, QueryParams.DEFAULT).getValue() != null) {
                        consulServices = newClient.getCatalogService(CONSUL_SERVICE_NAME, QueryParams.DEFAULT).getValue();
                        client = newClient;
                        return true;
                    }
                } catch (TransportException tex) {
                    LOGGER.error("Consul service not reachable. " + hostWithPort, tex);
                }
            }
        }
        return false;
    }
}
