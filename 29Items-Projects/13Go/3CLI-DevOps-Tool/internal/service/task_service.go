package service

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"github.com/your-org/cli-devops-tool/internal/models"
	"gorm.io/gorm"
)

var ErrValidation = errors.New("validation failed")

type TaskService struct {
	db *gorm.DB
}

type CreateTaskInput struct {
	Title       string `json:"title"`
	Description string `json:"description"`
	Source      string `json:"source"`
	Suggestion  string `json:"suggestion"`
}

type UpdateTaskInput struct {
	Title       *string `json:"title"`
	Description *string `json:"description"`
	Status      *string `json:"status"`
	Suggestion  *string `json:"suggestion"`
}

func NewTaskService(db *gorm.DB) *TaskService {
	return &TaskService{db: db}
}

func (s *TaskService) Create(ctx context.Context, input CreateTaskInput) (*models.Task, error) {
	title := strings.TrimSpace(input.Title)
	if title == "" {
		return nil, fmt.Errorf("%w: title is required", ErrValidation)
	}

	task := &models.Task{
		Title:       title,
		Description: strings.TrimSpace(input.Description),
		Status:      models.TaskStatusOpen,
		Source:      strings.TrimSpace(input.Source),
		Suggestion:  strings.TrimSpace(input.Suggestion),
	}

	if err := s.db.WithContext(ctx).Create(task).Error; err != nil {
		return nil, err
	}

	return task, nil
}

func (s *TaskService) List(ctx context.Context, status string, limit int) ([]models.Task, error) {
	if limit <= 0 || limit > 500 {
		limit = 100
	}

	query := s.db.WithContext(ctx).Order("created_at DESC").Limit(limit)
	if strings.TrimSpace(status) != "" {
		query = query.Where("status = ?", status)
	}

	var tasks []models.Task
	if err := query.Find(&tasks).Error; err != nil {
		return nil, err
	}

	return tasks, nil
}

func (s *TaskService) Get(ctx context.Context, id uint) (*models.Task, error) {
	var task models.Task
	if err := s.db.WithContext(ctx).First(&task, id).Error; err != nil {
		return nil, err
	}

	return &task, nil
}

func (s *TaskService) Update(ctx context.Context, id uint, input UpdateTaskInput) (*models.Task, error) {
	task, err := s.Get(ctx, id)
	if err != nil {
		return nil, err
	}

	if input.Title != nil {
		title := strings.TrimSpace(*input.Title)
		if title == "" {
			return nil, fmt.Errorf("%w: title cannot be empty", ErrValidation)
		}
		task.Title = title
	}
	if input.Description != nil {
		task.Description = strings.TrimSpace(*input.Description)
	}
	if input.Status != nil {
		status := strings.TrimSpace(*input.Status)
		if !validStatus(status) {
			return nil, fmt.Errorf("%w: invalid status %q", ErrValidation, status)
		}
		task.Status = status
	}
	if input.Suggestion != nil {
		task.Suggestion = strings.TrimSpace(*input.Suggestion)
	}

	if err := s.db.WithContext(ctx).Save(task).Error; err != nil {
		return nil, err
	}

	return task, nil
}

func (s *TaskService) Delete(ctx context.Context, id uint) error {
	return s.db.WithContext(ctx).Delete(&models.Task{}, id).Error
}

func validStatus(status string) bool {
	switch status {
	case models.TaskStatusOpen, models.TaskStatusDone, models.TaskStatusFailed:
		return true
	default:
		return false
	}
}
