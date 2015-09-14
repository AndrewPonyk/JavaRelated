import static play.mvc.Results.notFound;
import play.GlobalSettings;
import play.libs.F.Promise;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;

public class Global extends GlobalSettings {

	@Override
	public Promise<Result> onHandlerNotFound(RequestHeader arg0) {
		System.out.println("404 404 404");
		return Promise.<Result>pure(notFound(views.html.notFound.render()));
	}
}
