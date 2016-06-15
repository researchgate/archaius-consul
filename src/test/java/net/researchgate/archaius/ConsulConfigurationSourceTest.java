package net.researchgate.archaius;

import static net.researchgate.archaius.ConsulConfigurationSource.CONSUL_SERVICE_NAME;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
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
import com.netflix.config.PollResult;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

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
    }

    @Test
    public void testAllClientsFail() throws InterruptedException, Exception {
        ConsulClient brokenClient = PowerMockito.mock(ConsulClient.class);
        when(brokenClient.getKVValues(FACILITY)).thenThrow(new TransportException(new Exception("unavailable.")));
        consulConfigurationSource = new ConsulConfigurationSource("localhost:8500,second:8100", FACILITY, clientFactory);
        when(clientFactory.getClient(any(String.class), any(Integer.class))).thenReturn(brokenClient);

        PollResult pollResult = consulConfigurationSource.poll(false, null);
        verify(clientFactory, times(2)).getClient(any(String.class), any(Integer.class));

        Assert.assertNull(pollResult);

        dc = new DynamicConfiguration(consulConfigurationSource, new FixedDelayPollingScheduler(-1, 1000000, false));
        Thread.sleep(10);

        Assert.assertFalse(dc.getKeys().hasNext());
    }

    @Test
    public void testClientCreation() throws InterruptedException, Exception {
        ConsulClient brokenClient = PowerMockito.mock(ConsulClient.class);
        when(brokenClient.getKVValues(FACILITY)).thenThrow(new TransportException(new Exception("unavailable.")));
        consulConfigurationSource = new ConsulConfigurationSource("localhost:8500,second:8100", FACILITY, clientFactory);
        when(clientFactory.getClient(any(String.class), any(Integer.class))).thenReturn(brokenClient, consulClient);
        List<GetValue> arrayList = new ArrayList<>();
        GetValue intValue = new GetValue();
        intValue.setKey(FACILITY + "/" + "key.int");
        intValue.setValue(Base64.getEncoder().encodeToString("10".getBytes()));
        arrayList.add(intValue);
        response = new Response<>(arrayList, 0L, false, 0L);
        when(consulClient.getKVValues(FACILITY)).thenReturn(response);

        dc = new DynamicConfiguration(consulConfigurationSource, new FixedDelayPollingScheduler(-1, 1000000, false));
        Thread.sleep(10);

        Assert.assertEquals(10, dc.getInt("key.int"));

        when(consulClient.getKVValues(FACILITY)).thenThrow(new TransportException(null));

        consulConfigurationSource.poll(false, null);

        verify(clientFactory, atLeastOnce()).getClient("localhost", 8500);
        verify(clientFactory, atLeastOnce()).getClient("second", 8100);
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

    @Test
    public void testWithoutFacility() throws InterruptedException {
        consulConfigurationSource = new ConsulConfigurationSource(HOSTS, "/", clientFactory);
        List<GetValue> arrayList = new ArrayList<>();
        GetValue intValue = new GetValue();
        intValue.setKey("key.int");
        intValue.setValue(BaseEncoding.base64().encode("10".getBytes()));
        arrayList.add(intValue);
        GetValue stringValue = new GetValue();
        stringValue.setKey("user-service.lab.config");
        stringValue.setValue(BaseEncoding.base64().encode("Some String".getBytes()));
        arrayList.add(stringValue);
        GetValue boolValue = new GetValue();
        boolValue.setKey("key.bool");
        boolValue.setValue(BaseEncoding.base64().encode(Boolean.FALSE.toString().getBytes()));
        arrayList.add(boolValue);

        response = new Response<>(arrayList, 0l, false, 0l);
        when(consulClient.getKVValues("/")).thenReturn(response);

        consulConfigurationSource.poll(true, null);
        dc = new DynamicConfiguration(consulConfigurationSource, new FixedDelayPollingScheduler(-1, 1000000, false));

        Thread.sleep(10);
        Assert.assertEquals(10, dc.getInt("key.int"));
        Assert.assertEquals("Some String", dc.getString("user-service.lab.config"));
        Assert.assertEquals(false, dc.getBoolean("key.bool"));
    }

    @Test
    public void testWithFacilityWithDots() throws InterruptedException {
        final String FACILITY_DOTS = "/user-service.lab.config/";
        int length = FACILITY_DOTS.length();
        length = FACILITY_DOTS.length() + (FACILITY_DOTS.charAt(0) == '/' ? 0 : 1) - (FACILITY_DOTS.endsWith("/") ? 1 : 0);
        String sub = ("user-service.lab.config/" + "something").substring(length);
        System.out.println(sub);
    }

}
