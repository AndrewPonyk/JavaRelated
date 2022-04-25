package de.thtesche.udemy.domain;

import org.springframework.data.rest.core.annotation.RestResource;

import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Version;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author thtesche
 */
@XmlRootElement
@Entity
@RestResource(path = "teams", rel = "teams")
public class Team {

  @Id
  @GeneratedValue
  private long id;
  private String name;
  private String location;
  private String mascote;
  @OneToMany(cascade = CascadeType.ALL)
  @JoinColumn(name = "teamId")
  private Set<Player> players;
  @Version
  private long version;

  public Team() {
    super();
  }

  public Team(String name, String location, Set<Player> players) {
    this();
    this.name = name;
    this.location = location;
    this.players = players;
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
   * @return the location
   */
  public String getLocation() {
    return location;
  }

  /**
   * @param location the location to set
   */
  public void setLocation(String location) {
    this.location = location;
  }

  /**
   * @return the mascote
   */
  public String getMascote() {
    return mascote;
  }

  /**
   * @param mascote the mascote to set
   */
  public void setMascote(String mascote) {
    this.mascote = mascote;
  }

  /**
   * @return the players
   */
  public Set<Player> getPlayers() {
    return players;
  }

  /**
   * @param players the players to set
   */
  public void setPlayers(Set<Player> players) {
    this.players = players;
  }

}
