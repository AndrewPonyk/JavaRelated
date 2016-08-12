package com.ap;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Created by andrii on 08.08.16.
 */
public class MathServiceTest {
    MathService mathService = new MathService();

    @Test
    public void positiveNumbersSum(){
        assertEquals(mathService.sum(4,2), 6);
    }

    @Test
    public void negativeNumbersSum(){
        assertEquals(mathService.sum(-14,-2), -16);
    }
}
