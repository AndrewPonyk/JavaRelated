package shutdown

import (
	"context"
	"os"
	"os/signal"
	"syscall"
)

func Context(parent context.Context) context.Context {
	ctx, stop := signal.NotifyContext(parent, os.Interrupt, syscall.SIGTERM)
	go func() {
		<-ctx.Done()
		stop()
	}()
	return ctx
}
