package config

import (
	"os"
	"strconv"
	"time"

	"github.com/joho/godotenv"
)

func ConfigENV() error {
	err := godotenv.Load()

	return err
}

// EnvInt reads an integer from the environment, falling back to the given
// default when the variable is missing or not a valid number.
func EnvInt(key string, fallback int) int {
	raw := os.Getenv(key)
	if raw == "" {
		return fallback
	}

	value, err := strconv.Atoi(raw)
	if err != nil || value < 0 {
		return fallback
	}

	return value
}

// EnvDuration reads a duration such as "500ms" or "7s" from the environment.
func EnvDuration(key string, fallback time.Duration) time.Duration {
	raw := os.Getenv(key)
	if raw == "" {
		return fallback
	}

	value, err := time.ParseDuration(raw)
	if err != nil {
		return fallback
	}

	return value
}
