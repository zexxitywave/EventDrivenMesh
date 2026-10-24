package db

import (
	"fmt"
	"os"
	"time"

	"github.com/SatvikG7/Scalable-Notification-System/config"
	"github.com/SatvikG7/Scalable-Notification-System/internal/models"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

var DB *gorm.DB

func Init() error {
	dsn := fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%s sslmode=disable TimeZone=Asia/Shanghai", os.Getenv("DB_HOST"), os.Getenv("DB_USER"), os.Getenv("DB_PASSWORD"), os.Getenv("DB_NAME"), os.Getenv("DB_PORT"))
	var err error
	DB, err = gorm.Open(postgres.Open(dsn), &gorm.Config{})

	if err != nil {
		return err
	}

	// A pool that reuses connections instead of opening a new one for every
	// request keeps ingestion throughput high under load.
	sqlDB, err := DB.DB()
	if err != nil {
		return err
	}
	sqlDB.SetMaxOpenConns(config.EnvInt("DB_MAX_OPEN_CONNS", 50))
	sqlDB.SetMaxIdleConns(config.EnvInt("DB_MAX_IDLE_CONNS", 25))
	sqlDB.SetConnMaxLifetime(config.EnvDuration("DB_CONN_MAX_LIFETIME", 30*time.Minute))

	// Create or update the tables the application needs.
	if err := DB.AutoMigrate(&models.User{}); err != nil {
		return err
	}

	return nil
}
