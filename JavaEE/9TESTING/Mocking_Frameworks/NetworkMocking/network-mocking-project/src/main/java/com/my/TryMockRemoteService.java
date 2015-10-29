package com.my;


import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import java.io.IOException;
import java.net.InetAddress;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class TryMockRemoteService {
    private static WireMockServer defaultServer;
    private static WireMockServer altServer;

    public static void init() {
        defaultServer = new WireMockServer(8282);
        defaultServer.start();
       /* altServer = new WireMockServer(0);
        altServer.start();*/
    }

    public static void stopServer() {
        defaultServer.stop();
        altServer.stop();
    }



    public static void main(String[] args) throws IOException {
       System.out.println("Try to mock remote service");
       // WireMockTestClient defaultTestClient = new WireMockTestClient(defaultServer.port());
       // WireMockTestClient altTestClient = new WireMockTestClient(altServer.port());

        //String thisHostName = InetAddress.getLocalHost().getHostName();
        WireMock.configureFor("localhost", 8282);

        givenThat(get(urlEqualTo("/resource/on/other/address"))
                .willReturn(aResponse()
                        .withStatus(206)));

        System.in.read();
    }
}
