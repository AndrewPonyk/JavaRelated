package ch4;

import javax.persistence.*;
import java.util.Set;

@Entity
public class TestO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;

    @OneToMany(mappedBy = "father", cascade = CascadeType.ALL)
    public Set<TestM> manys;
}
