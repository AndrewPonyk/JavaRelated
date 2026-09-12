package grpc

import (
	"context"
	"errors"
	"time"

	googlegrpc "google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

type RoutingClient struct {
	conn *googlegrpc.ClientConn
}

func NewRoutingClient(ctx context.Context, target string) (*RoutingClient, error) {
	ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()

	conn, err := googlegrpc.DialContext(ctx, target, googlegrpc.WithTransportCredentials(insecure.NewCredentials()), googlegrpc.WithBlock())
	if err != nil {
		return nil, err
	}

	return &RoutingClient{conn: conn}, nil
}

func (c *RoutingClient) Close() error {
	return c.conn.Close()
}

func (c *RoutingClient) ResolveRoute(ctx context.Context, host string, path string) (string, error) {
	if host == "" || path == "" {
		return "", errors.New("host and path are required")
	}
	return host + path, nil
}
