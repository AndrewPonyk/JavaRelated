package com.mprog.entity;

import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.SortNatural;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import javax.persistence.*;
import java.util.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"users", "locales"})
@EqualsAndHashCode(of = "name")
@Builder
@BatchSize(size = 3)
@Audited
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "Companies")
public class Company implements BaseEntity<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Builder.Default
    @OneToMany(mappedBy = "company", cascade = {CascadeType.ALL}, orphanRemoval = true)
//    @OneToMany(mappedBy = "company", cascade = {CascadeType.ALL})
//    @JoinColumn(name = "company_id")
//    @org.hibernate.annotations.OrderBy(clause = "username DESC, lastname ASC")
//    @OrderBy("username DESC, personalInfo.lastname ASC")
//    @OrderColumn
    @MapKey(name = "username")
    @SortNatural
    @NotAudited
    private Map<String, User> users = new TreeMap<>();


    @Builder.Default
    @ElementCollection
    @CollectionTable(
            name = "company_locale",
            joinColumns = @JoinColumn(name = "company_id")
    )
    @MapKeyColumn(name = "lang")
    @Column(name = "description")
    @NotAudited
    private Map<String, String> locales = new HashMap<>();


    public void addUser(User user) {
        users.put(user.getUsername(), user);
        user.setCompany(this);
    }
}
