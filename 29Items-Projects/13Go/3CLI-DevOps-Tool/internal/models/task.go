package models

import "time"

const (
	TaskStatusOpen   = "open"
	TaskStatusDone   = "done"
	TaskStatusFailed = "failed"
)

// Task is a local automation task or recommendation tracked by the CLI.
type Task struct {
	ID          uint      `gorm:"primaryKey" json:"id"`
	Title       string    `gorm:"size:200;not null;index" json:"title"`
	Description string    `gorm:"type:text" json:"description"`
	Status      string    `gorm:"size:32;not null;index" json:"status"`
	Source      string    `gorm:"size:64;index" json:"source"`
	Suggestion  string    `gorm:"type:text" json:"suggestion"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}
