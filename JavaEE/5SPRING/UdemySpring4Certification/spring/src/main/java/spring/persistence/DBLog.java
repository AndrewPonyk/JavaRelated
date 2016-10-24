package spring.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;


@Entity
@Table(name="LOG")
public class DBLog {
	
	@Id
	@GeneratedValue
	@Column(name="IDLOG")
	private int IDLOG;
	
	@Column(name="LOGSTRING")
	private String LOGSTRING;
	
	public int getIDLOG() {
		return IDLOG;
	}
	public void setIDLOG(int iDLOG) {
		IDLOG = iDLOG;
	}
	public String getLOGSTRING() {
		return LOGSTRING;
	}
	public void setLOGSTRING(String lOGSTRING) {
		LOGSTRING = lOGSTRING;
	}

	
}
