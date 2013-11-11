package models;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;

import play.db.jpa.Model;

@Entity
public class Comment extends Model{
	
	
	public String author;
	public Date postedAt;
	
	@Lob
	public String content;
	
	@ManyToOne
	public Post post;

	public Comment(Post post,String autor, String contend) {
		super();
		this.author = autor;
		this.content = contend;
		this.post = post;
		this.postedAt=new Date();
	}
	
	
}
