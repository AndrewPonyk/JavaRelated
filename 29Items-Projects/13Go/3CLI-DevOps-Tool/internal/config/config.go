package config

import (
	"strings"

	"github.com/spf13/viper"
)

// Config is the effective runtime configuration after flags, environment
// variables, and config files have been merged.
type Config struct {
	AppName    string
	Output     string
	DBPath     string
	LogLevel   string
	AWSRegion  string
	AWSProfile string
	DockerHost string
	APIBind    string
	APIToken   string
}

func Load(configFile string) (*Config, error) {
	v := viper.New()
	v.SetConfigType("yaml")
	v.SetEnvPrefix("DEVOPSCTL")
	v.SetEnvKeyReplacer(strings.NewReplacer(".", "_"))
	v.AutomaticEnv()

	v.SetDefault("app.name", "devopsctl")
	v.SetDefault("app.output", "table")
	v.SetDefault("database.path", "./devopsctl.sqlite")
	v.SetDefault("logging.level", "info")
	v.SetDefault("aws.region", "us-east-1")
	v.SetDefault("aws.profile", "")
	v.SetDefault("docker.host", "")
	v.SetDefault("api.bind_address", "127.0.0.1:8080")
	v.SetDefault("api.token", "")

	for key, env := range map[string]string{
		"app.output":       "DEVOPSCTL_OUTPUT",
		"database.path":    "DEVOPSCTL_DB_PATH",
		"logging.level":    "DEVOPSCTL_LOG_LEVEL",
		"aws.region":       "DEVOPSCTL_AWS_REGION",
		"aws.profile":      "DEVOPSCTL_AWS_PROFILE",
		"docker.host":      "DEVOPSCTL_DOCKER_HOST",
		"api.bind_address": "DEVOPSCTL_API_BIND_ADDRESS",
		"api.token":        "DEVOPSCTL_API_TOKEN",
	} {
		if err := v.BindEnv(key, env); err != nil {
			return nil, err
		}
	}

	if configFile != "" {
		v.SetConfigFile(configFile)
		if err := v.ReadInConfig(); err != nil {
			return nil, err
		}
	}

	return &Config{
		AppName:    v.GetString("app.name"),
		Output:     v.GetString("app.output"),
		DBPath:     v.GetString("database.path"),
		LogLevel:   v.GetString("logging.level"),
		AWSRegion:  v.GetString("aws.region"),
		AWSProfile: v.GetString("aws.profile"),
		DockerHost: v.GetString("docker.host"),
		APIBind:    v.GetString("api.bind_address"),
		APIToken:   v.GetString("api.token"),
	}, nil
}
