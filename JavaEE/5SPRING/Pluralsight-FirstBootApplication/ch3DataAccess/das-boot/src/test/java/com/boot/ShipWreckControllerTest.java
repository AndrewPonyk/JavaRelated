package com.boot;

import com.boot.controller.HomeController;
import com.boot.controller.ShipwreckController;
import com.boot.model.Shipwreck;
import com.boot.repository.ShipwreckRepository;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for Shipwreck controller.
 */
@RunWith(MockitoJUnitRunner.class)
public class ShipWreckControllerTest {

    @InjectMocks
    ShipwreckController sc;

    @Mock
    ShipwreckRepository shipwreckRepository;

    @Test
    public void testShipWreckGet(){
        Shipwreck sw = new Shipwreck();
        sw.setId(1L);
        sw.setName("SomeName1");

        when(shipwreckRepository.findOne(1L)).thenReturn(sw);

        Shipwreck wreck = sc.get(1L);
        verify(shipwreckRepository, times(1)).findOne(1L);

        // junit assert
        assertEquals(new Long(1), wreck.getId());

        // hamcrest assert
        assertThat(wreck.getId(), is(1L));
        assertThat(wreck.getName(), CoreMatchers.containsString("Name"));
    }
}
