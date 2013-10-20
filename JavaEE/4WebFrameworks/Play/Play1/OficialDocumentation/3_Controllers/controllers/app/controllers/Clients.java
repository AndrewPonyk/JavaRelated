package controllers;

import models.Client;
import play.mvc.Controller;

public class Clients extends Controller {
	public static void saveClient(Client client){
		System.out.println(client);
		render();

	}
}
