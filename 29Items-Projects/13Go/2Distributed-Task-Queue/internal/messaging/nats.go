package messaging

import (
	"context"
	"time"

	"github.com/nats-io/nats.go"
)

type EventBus interface {
	Publish(ctx context.Context, subject string, payload []byte) error
	Close()
}

type NATSBus struct {
	conn *nats.Conn
}

func Connect(url string) (*NATSBus, error) {
	conn, err := nats.Connect(
		url,
		nats.Name("distributed-task-queue"),
		nats.MaxReconnects(-1),
		nats.ReconnectWait(2*time.Second),
		nats.Timeout(5*time.Second),
	)
	if err != nil {
		return nil, err
	}
	return &NATSBus{conn: conn}, nil
}

func (b *NATSBus) Publish(ctx context.Context, subject string, payload []byte) error {
	return b.conn.Publish(subject, payload)
}

func (b *NATSBus) Close() {
	if b == nil || b.conn == nil {
		return
	}
	b.conn.Drain()
	b.conn.Close()
}
