package qrcode

import (
	"fmt"

	qr "github.com/skip2/go-qrcode"
)

type Generator struct {
	size int
}

func NewGenerator() *Generator {
	return &Generator{size: 256}
}

func (g *Generator) PNG(shortURL string) ([]byte, error) {
	if shortURL == "" {
		return nil, fmt.Errorf("short URL is required")
	}
	return qr.Encode(shortURL, qr.Medium, g.size)
}
