package com.mprog.entity;

import com.mprog.listener.UserChatListener;
import lombok.*;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Data
@EqualsAndHashCode(of = "id", callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users_chat")
@EntityListeners(UserChatListener.class)
public class UserChat extends AuditableEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // not need when variable name is user
    private User user;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id") // not need when variable name is chat
    private Chat chat;



    public void setUser(User user){
        this.user = user;
        this.user.getUserChats().add(this);
    }

    public void setChat(Chat chat){
        this.chat = chat;
        this.chat.getUserChats().add(this);
    }

}
