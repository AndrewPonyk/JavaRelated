package docker

import (
	"context"

	dockertypes "github.com/docker/docker/api/types"
	"github.com/docker/docker/client"
)

type Client struct {
	api *client.Client
}

func NewClient(host string) (*Client, error) {
	opts := []client.Opt{client.FromEnv, client.WithAPIVersionNegotiation()}
	if host != "" {
		opts = append(opts, client.WithHost(host))
	}

	api, err := client.NewClientWithOpts(opts...)
	if err != nil {
		return nil, err
	}
	return &Client{api: api}, nil
}

func (c *Client) Ping(ctx context.Context) (dockertypes.Ping, error) {
	return c.api.Ping(ctx)
}
