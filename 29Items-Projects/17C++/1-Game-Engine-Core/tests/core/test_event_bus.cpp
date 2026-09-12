#include "engine/core/event/EventBus.hpp"

#include <gtest/gtest.h>

using engine::event::EventBus;

namespace {
struct DamageEvent {
    int amount = 0;
};
struct HealEvent {
    int amount = 0;
};
} // namespace

TEST(EventBus, PublishReachesAllSubscribers) {
    EventBus bus;
    int      total = 0;
    bus.subscribe<DamageEvent>([&total](const DamageEvent& e) { total += e.amount; });
    bus.subscribe<DamageEvent>([&total](const DamageEvent& e) { total += e.amount; });
    bus.publish(DamageEvent{5});
    EXPECT_EQ(total, 10);
}

TEST(EventBus, EventsAreTypeIsolated) {
    EventBus bus;
    int      damage = 0;
    bus.subscribe<DamageEvent>([&damage](const DamageEvent&) { ++damage; });
    bus.publish(HealEvent{3}); // different type — must not trigger
    EXPECT_EQ(damage, 0);
    bus.publish(DamageEvent{1});
    EXPECT_EQ(damage, 1);
}

TEST(EventBus, UnsubscribeStopsDelivery) {
    EventBus  bus;
    int       hits  = 0;
    const auto token = bus.subscribe<DamageEvent>([&hits](const DamageEvent&) { ++hits; });
    bus.unsubscribe<DamageEvent>(token);
    bus.publish(DamageEvent{1});
    EXPECT_EQ(hits, 0);
}
