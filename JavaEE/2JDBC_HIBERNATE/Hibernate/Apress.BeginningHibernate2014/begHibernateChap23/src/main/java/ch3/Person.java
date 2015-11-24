package ch3;

import javax.persistence.*;

@Entity
@NamedQueries({@NamedQuery(name = "selectAuthorByName", query = "from Person p where name=:name"),
        @NamedQuery(name = "selectAuthorByNameAndId", query = "from Person p where name=:name and id=:id")})
public class Person {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Column
    private
    String name;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}