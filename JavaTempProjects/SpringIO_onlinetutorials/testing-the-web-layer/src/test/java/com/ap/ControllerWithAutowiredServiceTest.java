package com.ap;


import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import services.GreetingService;

@RunWith(SpringRunner.class)
@WebMvcTest
public class ControllerWithAutowiredServiceTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    private GreetingService service;

    @Test
    public void greetingShouldReturnValueFromService() throws Exception {
        String mockedServiceValue = "mock value";
        Mockito.when(service.greet()).thenReturn(mockedServiceValue);
        mockMvc.perform(MockMvcRequestBuilders.get("/greeting")).
                andDo(MockMvcResultHandlers.print()).
                andExpect(MockMvcResultMatchers.status().isOk()).
                andExpect(MockMvcResultMatchers.content().
                        string(CoreMatchers.containsString(mockedServiceValue)));
    }
}
