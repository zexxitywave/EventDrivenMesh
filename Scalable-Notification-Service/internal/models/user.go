package models

import "github.com/google/uuid"

type User struct {
	Id         uuid.UUID  `json:"id" gorm:"type:uuid;primaryKey"`        // Unique identifier
	Username   string     `json:"username" gorm:"size:255;not null"`     // Username of the user
	Email      string     `json:"email" gorm:"size:255;unique;not null"` // Email of the user
	Phone      string     `json:"phone" gorm:"size:20;unique"`           // Phone number of the user
	Status     string     `json:"status" gorm:"size:50;not null"`        // Status of the user (active, inactive)
	Preference Preference `json:"preference" gorm:"embedded"`            // Notification preference of the user
}

// get channel
func (p *Preference) GetChannel(priority string) string {
	switch priority {
	case "low":
		return p.Low
	case "medium":
		return p.Medium
	case "high":
		return p.High
	default:
		return ""
	}
}
