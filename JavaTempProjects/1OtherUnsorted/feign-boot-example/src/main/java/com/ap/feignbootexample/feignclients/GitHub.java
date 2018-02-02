package com.ap.feignbootexample.feignclients;

import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

@FeignClient(name = "flexRestApiClient", url = "https://api.github.com")
public interface GitHub {
    @RequestMapping(value = "/repos/owner/repo/contributors", method = RequestMethod.GET)
    List<Contributor> contributors(@PathVariable("owner") String owner, @PathVariable("repo") String repo);

    @RequestMapping(value = "/users/{owner}/repos", method = RequestMethod.GET)
    List<Repo> userRepos(@PathVariable("owner") String owner);
}


