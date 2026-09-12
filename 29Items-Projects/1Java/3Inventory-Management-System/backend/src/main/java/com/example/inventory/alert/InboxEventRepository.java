package com.example.inventory.alert;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxEventRepository extends JpaRepository<InboxEvent, InboxEventId> {
}
