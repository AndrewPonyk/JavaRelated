package com.ap.powermockHello;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ProcessNetworkResponceService.class)
public class ProcessNetworkResponceServiceTest {

    private RestClient client;

    @Before
    public void setUp() throws Exception {
        client = Mockito.mock(RestClient.class);
        Mockito.when(client.getSiteContent("google.com")).thenReturn("Mock google content!!!");

       // PowerMockito.whenNew(RestClient.class).withAnyArguments().thenReturn(client);
        PowerMockito.whenNew(RestClient.class).withArguments(Matchers.eq(10), Matchers.anyObject()).thenReturn(client);

    }

    @Test
    public void testContentWrappedInBrackets(){
        ProcessNetworkResponceService service = new ProcessNetworkResponceService();

        String dataFromGoogle = service.getDataFromGoogle(); // mock rest client response (but inside this method RestClient is created, so we mocked its constructor, which now will return mock object)
        assertEquals("(Mock google content!!!)", dataFromGoogle);
    }
}
