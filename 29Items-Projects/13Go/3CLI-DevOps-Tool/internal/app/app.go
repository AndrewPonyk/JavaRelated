package app

import (
	"context"
	"net/http"
	"time"

	"github.com/your-org/cli-devops-tool/internal/api"
	"github.com/your-org/cli-devops-tool/internal/config"
	"github.com/your-org/cli-devops-tool/internal/database"
	"github.com/your-org/cli-devops-tool/internal/service"
)

// NewAPIServer wires the optional local API mode. Cobra commands can call this
// when an "api serve" command is implemented.
func NewAPIServer(ctx context.Context, cfg *config.Config) (*http.Server, error) {
	db, err := database.Open(ctx, cfg)
	if err != nil {
		return nil, err
	}
	if err := database.AutoMigrate(db); err != nil {
		return nil, err
	}

	tasks := service.NewTaskService(db)
	mux := http.NewServeMux()
	mux.Handle("/tasks", api.NewTasksHandler(tasks, cfg.APIToken))
	mux.Handle("/tasks/", api.NewTasksHandler(tasks, cfg.APIToken))

	return &http.Server{
		Addr:              cfg.APIBind,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
	}, nil
}
