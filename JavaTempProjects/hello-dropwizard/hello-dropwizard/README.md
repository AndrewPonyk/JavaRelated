# true

How to start the true application
---

1. Run `mvn clean install` to build your application
1. Start application with `java -jar target/hello-dropwizard-1.0-SNAPSHOT.jar server config.yml`
1. To check that your application is running enter url `http://localhost:8080`


Run in Intellij (program parameters):
server config.yml

Health Check
---

To see your applications health enter url `http://localhost:8081/healthcheck`


HERE (example of full dropwizard app): h2, rest, tests , ...
https://github.com/dropwizard/dropwizard/tree/master/dropwizard-example


Running app:
server config.yml (THIS SHOULD BE IN Intellij idea parameters, config.yml - must be in root not in resources)
