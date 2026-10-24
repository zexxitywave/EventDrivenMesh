package utils

import (
	"errors"

	"github.com/SatvikG7/Scalable-Notification-System/internal/db"
	"github.com/SatvikG7/Scalable-Notification-System/internal/models"
	"github.com/google/uuid"
)

// ErrUserNotFound is returned when no user matches the requested id.
var ErrUserNotFound = errors.New("user not found")

func GetUser(id string) (models.User, error) {
	var user models.User

	if _, err := uuid.Parse(id); err != nil {
		return user, ErrUserNotFound
	}

	query := `
	SELECT id, username, email, phone, status, preference_low_channel, preference_medium_channel, preference_high_channel 
	FROM users WHERE id = ?;
	`
	rows, err := db.DB.Raw(query, id).Rows()
	if err != nil {
		return user, err
	}
	defer rows.Close()

	found := false
	for rows.Next() {
		var low, medium, high string
		err := rows.Scan(&user.Id, &user.Username, &user.Email, &user.Phone, &user.Status, &low, &medium, &high)
		if err != nil {
			return user, err
		}
		user.Preference = models.Preference{
			Low:    low,
			Medium: medium,
			High:   high,
		}
		found = true
	}

	if err = rows.Err(); err != nil {
		return user, err
	}

	if !found {
		return models.User{}, ErrUserNotFound
	}

	return user, nil
}
