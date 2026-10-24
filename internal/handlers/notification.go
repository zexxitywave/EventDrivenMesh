package handlers

import (
	"encoding/json"
	"errors"
	"fmt"

	"github.com/SatvikG7/Scalable-Notification-System/internal/models"
	"github.com/SatvikG7/Scalable-Notification-System/internal/rabbitmq"
	"github.com/SatvikG7/Scalable-Notification-System/internal/utils"
	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
)

// apiError carries the HTTP status that should be returned for a failure.
type apiError struct {
	status int
	msg    string
}

func (e *apiError) Error() string {
	return e.msg
}

func newAPIError(status int, format string, args ...any) *apiError {
	return &apiError{status: status, msg: fmt.Sprintf(format, args...)}
}

func validateNotification(notification *models.Notification) error {
	if notification.Recipient == uuid.Nil {
		return newAPIError(fiber.StatusBadRequest, "recipient id cannot be empty")
	}

	if notification.Priority != "low" && notification.Priority != "medium" && notification.Priority != "high" {
		return newAPIError(fiber.StatusBadRequest, "invalid notification priority: %q (expected low, medium or high)", notification.Priority)
	}

	return nil
}

func saveNotification(notification *models.Notification) error {
	user, err := utils.GetUser(notification.Recipient.String())
	if err != nil {
		if errors.Is(err, utils.ErrUserNotFound) {
			return newAPIError(fiber.StatusNotFound, "recipient %s does not exist", notification.Recipient)
		}
		return err
	}

	channel := user.Preference.GetChannel(notification.Priority)
	if !models.IsValidChannel(channel) {
		return newAPIError(fiber.StatusBadRequest, "recipient %s has no %s notification channel configured", notification.Recipient, notification.Priority)
	}

	notification.Channel = channel

	notificationBytes, err := json.Marshal(notification)
	if err != nil {
		return err
	}

	queueName := fmt.Sprintf("notifications_%s_%s", channel, notification.Priority)

	if err := rabbitmq.PublishNotification(queueName, notificationBytes); err != nil {
		return err
	}

	return nil
}

func CreateNotification(c *fiber.Ctx) error {
	notification := new(models.Notification)
	if err := c.BodyParser(notification); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Cannot parse JSON",
		})
	}

	if err := validateNotification(notification); err != nil {
		return respondWithError(c, err)
	}

	notification.Id = uuid.New()
	notification.Status = "pending"

	if err := saveNotification(notification); err != nil {
		return respondWithError(c, err)
	}

	return c.Status(fiber.StatusCreated).JSON(notification)
}

func respondWithError(c *fiber.Ctx, err error) error {
	var apiErr *apiError
	if errors.As(err, &apiErr) {
		return c.Status(apiErr.status).JSON(fiber.Map{
			"error": apiErr.msg,
		})
	}

	return c.Status(fiber.StatusServiceUnavailable).JSON(fiber.Map{
		"error": "Failed to queue notification: " + err.Error(),
	})
}
