package ch4;

import javax.persistence.*;
import java.util.Set;

@Entity
public class TestM {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;

    @ManyToOne
    public  TestO father;
}
