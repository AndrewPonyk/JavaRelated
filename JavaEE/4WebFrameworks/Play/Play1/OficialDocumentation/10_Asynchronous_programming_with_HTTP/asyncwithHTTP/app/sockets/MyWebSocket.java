package sockets;

import java.util.logging.Logger;

import org.hibernate.transaction.WeblogicTransactionManagerLookup;

import play.mvc.Http.WebSocketClose;
import play.mvc.Http.WebSocketFrame;
import play.mvc.WebSocketController;
import play.mvc.Http.WebSocketEvent;

public class MyWebSocket  extends WebSocketController {
	
	public static void echo(){
		
		while (inbound.isOpen()) {
			WebSocketEvent e=await(inbound.nextEvent());
			if(e instanceof WebSocketFrame){
				WebSocketFrame frame=(WebSocketFrame)e;
				if(!frame.isBinary){
					if(frame.textData.equals("quit")){
						outbound.send("Bye!");
						disconnect();
					}else{
						outbound.send("Echo %s",frame.textData);
					}
				}
			}
			if(e instanceof WebSocketClose){
				play.Logger.info("Socket Closed");
			}
		}
		
	}
}
