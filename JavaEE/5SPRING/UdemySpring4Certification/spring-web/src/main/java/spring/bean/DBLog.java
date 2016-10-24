package spring.bean;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class DBLog implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int IDLOG;
	private String LOGSTRING;
	
	public int getIDLOG() {
		return IDLOG;
	}
	
	@XmlElement
	public void setIDLOG(int iDLOG) {
		IDLOG = iDLOG;
	}	
	
	public String getLOGSTRING() {
		return LOGSTRING;
	}
	
	@XmlElement
	public void setLOGSTRING(String lOGSTRING) {
		LOGSTRING = lOGSTRING;
	}
	
}
