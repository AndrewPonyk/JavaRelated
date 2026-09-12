package database

import (
	"context"
	"time"

	"github.com/your-org/cli-devops-tool/internal/config"
	"github.com/your-org/cli-devops-tool/internal/models"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

func Open(ctx context.Context, cfg *config.Config) (*gorm.DB, error) {
	db, err := gorm.Open(sqlite.Open(cfg.DBPath), &gorm.Config{})
	if err != nil {
		return nil, err
	}

	sqlDB, err := db.DB()
	if err != nil {
		return nil, err
	}

	sqlDB.SetMaxOpenConns(1)
	sqlDB.SetMaxIdleConns(1)
	sqlDB.SetConnMaxLifetime(30 * time.Minute)

	if err := sqlDB.PingContext(ctx); err != nil {
		return nil, err
	}

	return db, nil
}

func AutoMigrate(db *gorm.DB) error {
	// TODO: Replace AutoMigrate with versioned migration execution before GA.
	return db.AutoMigrate(&models.Task{})
}
