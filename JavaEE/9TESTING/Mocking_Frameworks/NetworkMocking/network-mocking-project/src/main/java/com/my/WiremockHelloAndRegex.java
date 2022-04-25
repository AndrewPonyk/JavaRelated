package com.my;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class WiremockHelloAndRegex {
    public static void main(String[] args) throws IOException {
        System.out.println("Hello wiremock");
        WireMockServer wireMockServer =
                new WireMockServer(WireMockConfiguration.wireMockConfig()
                        .port(8081)
                .extensions(LibraryBookReturnDateTransformer.class)); //No-args constructor will start on port 8080, no HTTPS
        wireMockServer.start();

        // simple REGEX stub urlMatching
        wireMockServer.stubFor(get(urlMatching("/some/abc/[0-9]+/andrew9999"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Hello world!")));

        // simple stub2 urlMatching , look we ESCAPE '?' symbol,  so '[\d\w@]+' means any 'number, letter and @' one or more times
        // valid url http://localhost:8081/some2/12@a?number=9999
        // http://localhost:8081/some2/9?number=9999
        // not valid http://localhost:8081/some2/9-?number=9999
        wireMockServer.stubFor(get(urlMatching("/some2/[\\d\\w@]+\\?number=9999"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Hello world!")));


        // instead of get can be post or put or another
        wireMockServer.stubFor(get(urlMatching("/some"))
                .willReturn(aResponse().withStatus(200)));


        // press Enter to stop server
        System.in.read();
        // Finish doing stuff
        wireMockServer.stop();
    }
}
