package ch7;

import javax.persistence.*;

@Entity(name = "Thing")
@Table(name="Thing")
@EntityListeners(value = {ThingEntityListenerExternal.class})
public class Thing {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    private String name;

    public Thing(){

    }

    @PrePersist
    public void beforePersist(){
        System.out.println("Before persist...");
    }

    @PostPersist
    public void afterPersist(){
        System.out.println("Persisted !!!");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
