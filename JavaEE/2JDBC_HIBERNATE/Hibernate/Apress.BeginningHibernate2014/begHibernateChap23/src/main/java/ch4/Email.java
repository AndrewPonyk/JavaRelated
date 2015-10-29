package ch4;

import javax.persistence.*;

@Entity
public class Email {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private
    Long id;

    @Column
    private
    String subject;

    @OneToOne(mappedBy = "email", cascade = CascadeType.ALL)
    private Messagech4 message;

    public Email() {
    }

    public Email(String subject) {
        setSubject(subject);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Messagech4 getMessage() {
        return message;
    }

    public void setMessage(Messagech4 message) {
        this.message = message;
    }
}
