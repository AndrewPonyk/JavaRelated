# CDN Edge Server Demonstration

I have run the application and tested the core functionality via the browser. Below is a step-by-step visual walkthrough of the server in action!

````carousel
![Initial Dashboard State - The frontend successfully fetches from the backend API. Showing 13 total requests from previous manual tests.](./dashboard_initial_1784158518727.png)
<!-- slide -->
![Adding a Routing Rule - We dynamically created a new rule to intercept `/browser-demo` and proxy it to an upstream JSON API.](./dashboard_rule_added_1784158582987.png)
<!-- slide -->
![Proxy Response - Navigating directly to `http://localhost:8080/browser-demo`. The Edge server intercepts this, fetches the data from the upstream origin, caches it in Redis, and serves it back to the client!](./proxy_response_1784158593994.png)
<!-- slide -->
![Metrics Updated - After refreshing the proxy response a few times, we return to the dashboard. The Cache Hit Rate has jumped to 12.5% because the subsequent requests were served blazingly fast from Redis!](./dashboard_metrics_1784158610267.png)
````

### What happened under the hood?
1. The **React UI** successfully parsed your rule and sent it to the Rust Backend via the Nginx reverse proxy.
2. The **Actix-Web Server** saved the `/browser-demo` routing rule in the **PostgreSQL** database.
3. Upon hitting the route, the backend fetched the payload from the origin (`jsonplaceholder`) and cached it securely in **Redis**.
4. Subsequent hits to the route bypassed the network request and loaded straight from memory, bumping the telemetry **Cache Hit Rate** metrics!
