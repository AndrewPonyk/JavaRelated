package ui

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/your-org/cli-devops-tool/internal/models"
)

type TaskFetcher interface {
	List(ctx context.Context, status string, limit int) ([]models.Task, error)
}

// TaskView is the CLI presentation component. It owns loading, error, and
// display states so command handlers can stay focused on orchestration.
type TaskView struct {
	out io.Writer
}

func NewTaskView(out io.Writer) *TaskView {
	return &TaskView{out: out}
}

func (v *TaskView) Loading(message string) {
	fmt.Fprintf(v.out, "%s...\n", message)
}

func (v *TaskView) Error(err error) {
	fmt.Fprintf(v.out, "error: %v\n", err)
}

func (v *TaskView) RenderTaskListFromSource(ctx context.Context, fetcher TaskFetcher, status string, limit int, format string) error {
	if format != "json" {
		v.Loading("Loading tasks")
	}

	tasks, err := fetcher.List(ctx, status, limit)
	if err != nil {
		v.Error(err)
		return err
	}

	return v.RenderTasks(tasks, format)
}

func (v *TaskView) RenderTask(task *models.Task, format string) error {
	if format == "json" {
		return json.NewEncoder(v.out).Encode(task)
	}

	fmt.Fprintf(v.out, "Task #%d: %s [%s]\n", task.ID, task.Title, task.Status)
	if task.Suggestion != "" {
		fmt.Fprintf(v.out, "Suggestion: %s\n", task.Suggestion)
	}
	return nil
}

func (v *TaskView) RenderTasks(tasks []models.Task, format string) error {
	if format == "json" {
		return json.NewEncoder(v.out).Encode(tasks)
	}

	if len(tasks) == 0 {
		fmt.Fprintln(v.out, "No tasks found.")
		return nil
	}

	tw := tabwriter.NewWriter(v.out, 0, 0, 2, ' ', 0)
	fmt.Fprintln(tw, "ID\tSTATUS\tTITLE\tSOURCE")
	for _, task := range tasks {
		fmt.Fprintf(tw, "%d\t%s\t%s\t%s\n", task.ID, task.Status, task.Title, task.Source)
	}
	return tw.Flush()
}
