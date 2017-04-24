package com.ap.model;

import java.io.Serializable;
import javax.persistence.*;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


/**
 * The persistent class for the MYUSER database table.
 * 
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@NamedQuery(name="Myuser.findAll", query="SELECT m FROM Myuser m")
public class Myuser implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	private String name;

	private String password;

	public Myuser() {
	}

	public String getName() {
		return this.name;
	}

	@XmlElement
	public void setName(String name) {
		this.name = name;
	}

	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

}