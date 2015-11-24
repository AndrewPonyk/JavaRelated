package ch11;

import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
@FilterDefs( {@FilterDef(name="endsWith1"),
        @FilterDef(name = "endsWithParam",
                parameters = {@ParamDef(name = "para", type = "string")})
})
@Filters({
        @Filter(name = "endsWith1", condition = "name like '%1'"),
        @Filter(name = "endsWithParam", condition = "name like :para")
})
public class UserWithFilter {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    private String name;
    private Boolean active;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean isActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
