#!!!  A lot of TROUBLES WHITE WORKING
## In realm-export.json file  - CORRECT JSON FOR KEYCLOAK 24
## In docker-compose.yml file  - CORRECT KEYCLOAK IMAGE
## Users in keyloack created manually
## IN HOSTS FILE ADD 
127.0.0.1 keycloak 
!!!!!!!!!!!!!!

## Its important, if you geneate token using localhost:8080 - it WILL NOT WORK IN APPLICATION because in config of app we use keycloak:8080

http://localhost:8072/license/v1/organization/d898a142-de44-466c-8c88-9ceb2c2429d3/license/f2a9c9d4-d2c0-44fa-97fe-724d77173c62 - FINALLY AFTER 15 Hours of Troubles

## token generate url: http://keycloak:8080/realms/spmia-realm/protocol/openid-connect/token

# Keycloak jboss/keycloak - original from repo - is NOT SUPPORTED


## Spring Microservices in Action - Second Edition. Chapter 9

# Introduction
Welcome to Spring Microservices in Action, Chapter 9.  Chapter 9 demonstrates how to build security with your services using Spring Cloud Security and KeyCloak.  In this chapter we build an Authentication and authorization server using KeyCloak. 

1. A KeyCloak Authentication and Authorization server.
2. A Spring Cloud Config server that is deployed as Docker container and can manage a services configuration information using a file system or GitHub-based repository.
3. A Eureka server running as a Spring-Cloud based service. This service will allow multiple service instances to register with it. Clients that need to call a service will use Eureka to lookup the physical location of the target service.
4. A API Gateway. All of our microservices can be routed through the gateway and have pre, response and post policies enforced on the calls.
5. A organization service that will manage organization data used within Ostock.
6. A licensing service that will manage licensing data used within Ostock.
7. A Postgres SQL database used to hold the data.

## Initial Configuration
1.	Apache Maven (http://maven.apache.org)  All of the code examples in this book have been compiled with Java version 11.
2.	Git Client (http://git-scm.com)
3.  Docker(https://www.docker.com/products/docker-desktop)


## How To Use

To clone and run this application, you'll need [Git](https://git-scm.com), [Maven](https://maven.apache.org/), [Java 11](https://www.oracle.com/technetwork/java/javase/downloads/jdk11-downloads-5066655.html). From your command line:

```bash
# Clone this repository
$ git clone https://github.com/ihuaylupo/manning-smia

# Go into the repository, by changing to the directory where you have downloaded the 
# chapter 9 source code and select whether you want the initial or final configuration
$ cd chapter9

# To build the code examples for Chapter 8 as a docker image, open a command-line 
# window and execute the following command:
$ mvn clean package dockerfile:build

# Now we are going to use docker-compose to start the actual image.  To start the docker image, stay in the directory containing  your chapter 8 source code and  Run the following command: 
$ docker-compose -f docker/docker-compose.yml up
```

# The build command

Will execute the [Spotify dockerfile plugin](https://github.com/spotify/dockerfile-maven) defined in the pom.xml file.  

 Running the above command at the root of the project directory will build all of the projects.  If everything builds successfully you should see a message indicating that the build was successful.

# The Run command

This command will run our services using the docker-compose.yml file located in the /docker directory. 

If everything starts correctly you should see a bunch of Spring Boot information fly by on standard out.  At this point all of the services needed for the chapter code examples will be running.

# Database
You can find the database script as well in the docker directory.

## Contact

I'd like you to send me an email on <illaryhs@gmail.com> about anything you'd want to say about this software.

### Contributing
Feel free to file an issue if it doesn't work for your code sample. Thanks.