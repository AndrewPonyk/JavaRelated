package models;

import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import play.db.jpa.Model;

@Entity
public class Post extends Model{

	
	public String title;
	public Date postedAt;
	
	@Lob
	public String content;
	
	@ManyToOne
	public User author;
	 
	@OneToMany(mappedBy="post",cascade=CascadeType.ALL)
	public List<Comment> comments;
	
	public Post(User autor, String title, String content){
		super();
		this.title = title;
		this.postedAt = new Date();
		this.content = content;
		this.author = autor;
	}
	
	public Post addComment(String author, String content) {
	    Comment newComment = new Comment(this, author, content).save();
	    this.comments.add(newComment);
	    this.save();
	    return this;
	}
	
}
