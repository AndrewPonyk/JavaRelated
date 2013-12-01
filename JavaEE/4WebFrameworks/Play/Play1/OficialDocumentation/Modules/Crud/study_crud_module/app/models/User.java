package models;
 
import play.*;
import play.data.validation.Required;
import play.db.jpa.*;

import javax.persistence.*;

import java.util.*;
 
@Entity
public class User extends Model {
 	@Required
	public String name;
	public String email;
	public String address;
 	public Date createDate;
 	
 	
 	@Override
 	public String toString() {
 		return name;
 	}
}