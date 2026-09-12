use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};

use chat_shared::ServerEvent;
use dashmap::DashMap;
use tokio::sync::broadcast;
use uuid::Uuid;

use crate::error::AppError;

#[derive(Debug, Clone)]
pub struct ChatHub {
    rooms: Arc<DashMap<Uuid, Arc<RoomChannel>>>,
    channel_capacity: usize,
    max_connections_per_room: usize,
}

#[derive(Debug)]
struct RoomChannel {
    sender: broadcast::Sender<ServerEvent>,
    online: AtomicUsize,
}

#[derive(Debug)]
pub struct RoomSubscription {
    room: Arc<RoomChannel>,
    receiver: broadcast::Receiver<ServerEvent>,
    user_id: Uuid,
    username: String,
}

impl ChatHub {
    pub fn new(channel_capacity: usize, max_connections_per_room: usize) -> Self {
        Self {
            rooms: Arc::new(DashMap::new()),
            channel_capacity,
            max_connections_per_room,
        }
    }

    pub fn join(
        &self,
        room_id: Uuid,
        user_id: Uuid,
        username: String,
    ) -> Result<RoomSubscription, AppError> {
        let room = self.room(room_id);
        increment_if_below(&room.online, self.max_connections_per_room)?;
        let receiver = room.sender.subscribe();
        let online_count = room.online.load(Ordering::Acquire);
        let _ = room.sender.send(ServerEvent::UserJoined {
            user_id,
            username: username.clone(),
            online_count,
        });

        Ok(RoomSubscription {
            room,
            receiver,
            user_id,
            username,
        })
    }

    pub fn publish(&self, room_id: Uuid, event: ServerEvent) {
        if let Some(room) = self.rooms.get(&room_id) {
            let _ = room.sender.send(event);
        }
    }

    pub fn online_count(&self, room_id: Uuid) -> usize {
        self.rooms
            .get(&room_id)
            .map_or(0, |room| room.online.load(Ordering::Acquire))
    }

    pub fn remove_room(&self, room_id: Uuid) {
        if let Some((_, room)) = self.rooms.remove(&room_id) {
            let _ = room.sender.send(ServerEvent::error(
                "room_deleted",
                "this room was deleted",
                false,
            ));
        }
    }

    fn room(&self, room_id: Uuid) -> Arc<RoomChannel> {
        self.rooms
            .entry(room_id)
            .or_insert_with(|| {
                let (sender, _) = broadcast::channel(self.channel_capacity);
                Arc::new(RoomChannel {
                    sender,
                    online: AtomicUsize::new(0),
                })
            })
            .clone()
    }
}

impl RoomSubscription {
    pub async fn recv(&mut self) -> Result<ServerEvent, broadcast::error::RecvError> {
        self.receiver.recv().await
    }
}

impl Drop for RoomSubscription {
    fn drop(&mut self) {
        let previous = self.room.online.fetch_sub(1, Ordering::AcqRel);
        let online_count = previous.saturating_sub(1);
        let _ = self.room.sender.send(ServerEvent::UserLeft {
            user_id: self.user_id,
            username: self.username.clone(),
            online_count,
        });
    }
}

fn increment_if_below(counter: &AtomicUsize, limit: usize) -> Result<(), AppError> {
    let mut current = counter.load(Ordering::Acquire);
    loop {
        if current >= limit {
            return Err(AppError::Conflict("the room is at capacity".to_owned()));
        }
        match counter.compare_exchange_weak(
            current,
            current + 1,
            Ordering::AcqRel,
            Ordering::Acquire,
        ) {
            Ok(_) => return Ok(()),
            Err(observed) => current = observed,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn presence_is_released_when_subscription_drops() {
        let hub = ChatHub::new(8, 2);
        let room_id = Uuid::new_v4();
        let subscription = hub
            .join(room_id, Uuid::new_v4(), "Ada".to_owned())
            .expect("join room");
        assert_eq!(hub.online_count(room_id), 1);

        drop(subscription);
        assert_eq!(hub.online_count(room_id), 0);
    }

    #[test]
    fn room_capacity_is_enforced_atomically() {
        let hub = ChatHub::new(8, 1);
        let room_id = Uuid::new_v4();
        let _first = hub
            .join(room_id, Uuid::new_v4(), "Ada".to_owned())
            .expect("first join");

        assert!(
            hub.join(room_id, Uuid::new_v4(), "Grace".to_owned())
                .is_err()
        );
    }
}
