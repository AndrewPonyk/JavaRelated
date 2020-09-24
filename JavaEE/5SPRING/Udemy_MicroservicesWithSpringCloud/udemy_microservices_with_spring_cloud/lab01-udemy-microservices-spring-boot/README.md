# Lab01 udemy course Microservices with Spring Cloud

## LAB-01

Run the application for lab01 with:

`mvn spring-boot:run`

and call with curl or a browser the url http://localhost:xxxxx/teams where xxxxx is a random port number (see console output)

## LAB-04

Run the three applications with

`java -jar -Dspring.profiles.active=lab-04-inst-01 lab01-udemy-microservices-spring-boot-0.0.1-SNAPSHOT.jar`

`java -jar -Dspring.profiles.active=lab-04-inst-02 lab01-udemy-microservices-spring-boot-0.0.1-SNAPSHOT.jar`

`java -jar -Dspring.profiles.active=lab-04-inst-03 lab01-udemy-microservices-spring-boot-0.0.1-SNAPSHOT.jar`

or 

`mvn spring-boot:run -Drun.jvmArguments="-Dspring.profiles.active=lab-04-inst-01"`

`mvn spring-boot:run -Drun.jvmArguments="-Dspring.profiles.active=lab-04-inst-02"`

`mvn spring-boot:run -Drun.jvmArguments="-Dspring.profiles.active=lab-04-inst-03"`

Select one of the three application from the Eureka server and call
`http://your-host-name:the-port/aggregate`

The result should be: `Content from lab-04-inst-02 - Content from lab-04-inst-03 - Content from lab-04-inst-01`. The order depends on the 
time the service registered at Eureka.