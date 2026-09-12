package commands

import (
	"context"
	"fmt"
	"os"
	"time"

	"github.com/spf13/cobra"
	"github.com/your-org/cli-devops-tool/internal/config"
	"github.com/your-org/cli-devops-tool/internal/database"
	"github.com/your-org/cli-devops-tool/internal/service"
	"github.com/your-org/cli-devops-tool/internal/ui"
)

type commandContext struct {
	ConfigPath string
	Config     *config.Config
	Tasks      *service.TaskService
	View       *ui.TaskView
}

func NewRootCommand() *cobra.Command {
	ctx := &commandContext{}

	root := &cobra.Command{
		Use:           "devopsctl",
		Short:         "Developer productivity CLI for DevOps automation",
		SilenceUsage:  true,
		SilenceErrors: true,
	}

	root.PersistentFlags().StringVar(&ctx.ConfigPath, "config", os.Getenv("DEVOPSCTL_CONFIG"), "config file path")
	root.PersistentFlags().String("output", "", "output format: table or json")

	root.AddCommand(newTasksCommand(ctx))
	root.AddCommand(newVersionCommand())

	return root
}

func (c *commandContext) bootstrap(parent context.Context) error {
	if c.Config != nil {
		return nil
	}

	cfg, err := config.Load(c.ConfigPath)
	if err != nil {
		return fmt.Errorf("load config: %w", err)
	}
	c.Config = cfg

	ctx, cancel := context.WithTimeout(parent, 5*time.Second)
	defer cancel()

	db, err := database.Open(ctx, cfg)
	if err != nil {
		return fmt.Errorf("open database: %w", err)
	}
	if err := database.AutoMigrate(db); err != nil {
		return fmt.Errorf("migrate database: %w", err)
	}

	c.Tasks = service.NewTaskService(db)
	c.View = ui.NewTaskView(os.Stdout)
	return nil
}

func (c *commandContext) outputFormat(cmd *cobra.Command) string {
	value, _ := cmd.Flags().GetString("output")
	if value == "" {
		value, _ = cmd.Root().PersistentFlags().GetString("output")
	}
	if value != "" {
		return value
	}
	if c.Config != nil {
		return c.Config.Output
	}
	return "table"
}

func newVersionCommand() *cobra.Command {
	return &cobra.Command{
		Use:   "version",
		Short: "Print version information",
		Run: func(cmd *cobra.Command, args []string) {
			fmt.Fprintln(cmd.OutOrStdout(), "devopsctl development")
		},
	}
}
