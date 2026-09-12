package commands

import (
	"fmt"
	"strconv"

	"github.com/spf13/cobra"
	"github.com/your-org/cli-devops-tool/internal/service"
)

func newTasksCommand(ctx *commandContext) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "tasks",
		Short: "Manage local DevOps automation tasks",
		PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
			return ctx.bootstrap(cmd.Context())
		},
	}

	cmd.AddCommand(newTaskCreateCommand(ctx))
	cmd.AddCommand(newTaskListCommand(ctx))
	cmd.AddCommand(newTaskDoneCommand(ctx))
	cmd.AddCommand(newTaskDeleteCommand(ctx))

	return cmd
}

func newTaskCreateCommand(ctx *commandContext) *cobra.Command {
	var input service.CreateTaskInput

	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create a task",
		RunE: func(cmd *cobra.Command, args []string) error {
			task, err := ctx.Tasks.Create(cmd.Context(), input)
			if err != nil {
				return err
			}
			return ctx.View.RenderTask(task, ctx.outputFormat(cmd))
		},
	}

	cmd.Flags().StringVar(&input.Title, "title", "", "task title")
	cmd.Flags().StringVar(&input.Description, "description", "", "task description")
	cmd.Flags().StringVar(&input.Source, "source", "manual", "task source")
	cmd.Flags().StringVar(&input.Suggestion, "suggestion", "", "suggested remediation")
	_ = cmd.MarkFlagRequired("title")

	return cmd
}

func newTaskListCommand(ctx *commandContext) *cobra.Command {
	var status string
	var limit int

	cmd := &cobra.Command{
		Use:   "list",
		Short: "List tasks",
		RunE: func(cmd *cobra.Command, args []string) error {
			return ctx.View.RenderTaskListFromSource(cmd.Context(), ctx.Tasks, status, limit, ctx.outputFormat(cmd))
		},
	}

	cmd.Flags().StringVar(&status, "status", "", "filter by status")
	cmd.Flags().IntVar(&limit, "limit", 100, "maximum rows to return")

	return cmd
}

func newTaskDoneCommand(ctx *commandContext) *cobra.Command {
	return &cobra.Command{
		Use:   "done TASK_ID",
		Short: "Mark a task as done",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := parseTaskID(args[0])
			if err != nil {
				return err
			}
			status := "done"
			task, err := ctx.Tasks.Update(cmd.Context(), id, service.UpdateTaskInput{Status: &status})
			if err != nil {
				return err
			}
			return ctx.View.RenderTask(task, ctx.outputFormat(cmd))
		},
	}
}

func newTaskDeleteCommand(ctx *commandContext) *cobra.Command {
	return &cobra.Command{
		Use:   "delete TASK_ID",
		Short: "Delete a task",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := parseTaskID(args[0])
			if err != nil {
				return err
			}
			if err := ctx.Tasks.Delete(cmd.Context(), id); err != nil {
				return err
			}
			fmt.Fprintf(cmd.OutOrStdout(), "Deleted task %d\n", id)
			return nil
		},
	}
}

func parseTaskID(value string) (uint, error) {
	id, err := strconv.ParseUint(value, 10, 64)
	if err != nil || id == 0 {
		return 0, fmt.Errorf("task id must be a positive integer")
	}
	return uint(id), nil
}
