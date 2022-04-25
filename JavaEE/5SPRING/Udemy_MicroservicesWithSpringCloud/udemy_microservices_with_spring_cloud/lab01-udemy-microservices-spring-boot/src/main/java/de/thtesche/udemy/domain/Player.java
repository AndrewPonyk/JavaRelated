package de.thtesche.udemy.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Version;
import javax.xml.bind.annotation.XmlRootElement;
import org.springframework.data.rest.core.annotation.RestResource;

/**
 *
 * @author thtesche
 */
@XmlRootElement
@Entity
@RestResource(path = "players", rel = "players")
public class Player {

  @Id
  @GeneratedValue
  private long id;
  private String name;
  private String position;
  @Version
  private long version;

  public Player() {
    super();
  }

  public Player(String name, String position) {
    this();
    this.name = name;
    this.position = position;
  }

  /**
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * @return the position
   */
  public String getPosition() {
    return position;
  }

  /**
   * @param position the position to set
   */
  public void setPosition(String position) {
    this.position = position;
  }

}
