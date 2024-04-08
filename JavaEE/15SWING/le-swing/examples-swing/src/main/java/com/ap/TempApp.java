package com.ap;

public class TempApp {
    public static void main(String[] args) {
        int temp =0x90d369ff;
        int temp2 = (int)Long.parseLong("0xeac4e1ff".substring(2), 16);

        System.out.println(temp);
        System.out.println(temp2);
    }
}
