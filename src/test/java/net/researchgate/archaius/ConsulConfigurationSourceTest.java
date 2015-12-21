package net.researchgate.archaius;

import static net.researchgate.archaius.ConsulConfigurationSource.CONSUL_SERVICE_NAME;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecwid.consul.transport.TransportException;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.model.CatalogService;
import com.ecwid.consul.v1.health.model.Check;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.google.common.io.BaseEncoding;
import com.netflix.config.DynamicConfiguration;
import com.netflix.config.FixedDelayPollingScheduler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ConsulClient.class)
public class ConsulConfigurationSourceTest {

    private static final String FACILITY = "testFacility";
    private static final String HOSTS = "localhost:8500";

    private ConsulConfigurationSource consulConfigurationSource;

    @Mock
    private ConsulClient consulClient;

    @Mock
    private ConsulClientFactory clientFactory;

    private Response<List<GetValue>> response;
    private DynamicConfiguration dc;

    @Before
    public void setUp() throws Exception {
        consulConfigurationSource = new ConsulConfigurationSource(HOSTS, FACILITY, clientFactory);
        when(clientFactory.getClient(any(String.class))).thenReturn(consulClient);
        when(clientFactory.getClient(any(String.class), any(Integer.class))).thenReturn(consulClient);

        ArrayList<CatalogService> serviceCatalog = new ArrayList<>();
        CatalogService catalogService = new CatalogService();
        catalogService.setAddress("second");
        catalogService.setServicePort(8100);
        serviceCatalog.add(catalogService);
        when(consulClient.getHealthChecksForService(CONSUL_SERVICE_NAME, QueryParams.DEFAULT)).thenReturn(new Response<>((List<Check>) new ArrayList<Check>(), 0l, true, 0l));
        when(consulClient.getCatalogService(CONSUL_SERVICE_NAME, QueryParams.DEFAULT)).thenReturn(new Response<>((List<CatalogService>) serviceCatalog, 0l, true, 0l));
    }

    @Test
    public void testClientCreation() throws InterruptedException {
        List<GetValue> arrayList = new ArrayList<>();
        GetValue intValue = new GetValue();
        intValue.setKey(FACILITY + "/" + "key.int");
        intValue.setValue(BaseEncoding.base64().encode("10".getBytes()));
        arrayList.add(intValue);
        response = new Response<>(arrayList, 0l, false, 0l);
        when(consulClient.getKVValues(FACILITY)).thenReturn(response);

        dc = new DynamicConfiguration(consulConfigurationSource, new FixedDelayPollingScheduler(-1, 1, false));
        Thread.sleep(10);

        verify(clientFactory).getClient("localhost", 8500);
        verify(consulClient).getHealthChecksForService(CONSUL_SERVICE_NAME, QueryParams.DEFAULT);
        verify(consulClient, atLeastOnce()).getCatalogService(CONSUL_SERVICE_NAME, QueryParams.DEFAULT);
        Assert.assertEquals(10, dc.getInt("key.int"));

        ConsulClient newClient = PowerMockito.mock(ConsulClient.class);
        when(clientFactory.getClient("second", 8100)).thenReturn(newClient);
        when(consulClient.getKVValues(FACILITY)).thenThrow(new TransportException(null));
        when(newClient.getCatalogService(CONSUL_SERVICE_NAME, QueryParams.DEFAULT)).thenReturn(new Response<>((List<CatalogService>) new ArrayList<CatalogService>(), 0l, true, 0l));

        consulConfigurationSource.poll(false, null);

        verify(clientFactory, atLeastOnce()).getClient("second", 8100);
        verify(newClient, atMost(2)).getCatalogService(CONSUL_SERVICE_NAME, QueryParams.DEFAULT);
        Thread.sleep(10);

        Assert.assertEquals(10, dc.getInt("key.int"));
    }

    @Test
    public void testPoll() throws Exception {
        List<GetValue> arrayList = new ArrayList<>();
        GetValue intValue = new GetValue();
        intValue.setKey(FACILITY + "/" + "key.int");
        intValue.setValue(BaseEncoding.base64().encode("10".getBytes()));
        arrayList.add(intValue);
        GetValue stringValue = new GetValue();
        stringValue.setKey(FACILITY + "/" + "key.string");
        stringValue.setValue(BaseEncoding.base64().encode("Some String".getBytes()));
        arrayList.add(stringValue);
        GetValue boolValue = new GetValue();
        boolValue.setKey(FACILITY + "/" + "key.bool");
        boolValue.setValue(BaseEncoding.base64().encode(Boolean.FALSE.toString().getBytes()));
        arrayList.add(boolValue);

        response = new Response<>(arrayList, 0l, false, 0l);
        when(consulClient.getKVValues(FACILITY)).thenReturn(response);

        consulConfigurationSource.poll(true, null);
        dc = new DynamicConfiguration(consulConfigurationSource, new FixedDelayPollingScheduler(-1, 1000000, false));

        Thread.sleep(10);
        Assert.assertEquals(10, dc.getInt("key.int"));
        Assert.assertEquals("Some String", dc.getString("key.string"));
        Assert.assertEquals(false, dc.getBoolean("key.bool"));
    }

}
