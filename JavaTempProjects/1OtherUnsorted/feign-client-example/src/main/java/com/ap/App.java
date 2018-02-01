package com.ap;

import feign.Feign;
import feign.Param;
import feign.RequestLine;
import feign.gson.GsonDecoder;

import java.util.List;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        System.out.println( "Feign client example!" );
        GitHub gitHub = Feign.builder().decoder(new GsonDecoder())
                .target(GitHub.class, "https://api.github.com");

        List<Contributor> contributors = gitHub.contributors("OpenFeign", "feign");
        System.out.println(contributors);

        List<Repo> myRepos = gitHub.userRepos("AndrewPonyk");
        System.out.println(myRepos);
    }
}

interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);

    @RequestLine("GET /users/{owner}/repos")
    List<Repo> userRepos(@Param("owner") String owner);

}

class Repo{
    @Override
    public String toString() {
        return "Repo{" +
                "name='" + name + '\'' +
                '}';
    }

    String name;
}

class Contributor {
    String login;
    int contributions;

    @Override
    public String toString() {
        return "Contributor{" +
                "login='" + login + '\'' +
                ", contributions=" + contributions +
                '}';
    }
}
