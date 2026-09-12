package aws

import (
	"context"

	awscfg "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/sts"
)

type Client struct {
	sts *sts.Client
}

func NewClient(ctx context.Context, region string) (*Client, error) {
	cfg, err := awscfg.LoadDefaultConfig(ctx, awscfg.WithRegion(region))
	if err != nil {
		return nil, err
	}
	return &Client{sts: sts.NewFromConfig(cfg)}, nil
}

func (c *Client) CallerIdentity(ctx context.Context) (string, error) {
	out, err := c.sts.GetCallerIdentity(ctx, &sts.GetCallerIdentityInput{})
	if err != nil {
		return "", err
	}
	if out.Arn == nil {
		return "", nil
	}
	return *out.Arn, nil
}
