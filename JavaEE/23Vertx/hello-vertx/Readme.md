RUN app:
in intellij idea:
main class: io.vertx.core.Launcher
program arguments: run com.ap.hello_vertx.MainVerticle


Another way to start app is to create main
public class RunApp {
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
  }
}
