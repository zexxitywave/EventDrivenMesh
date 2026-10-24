package users

import (
	"github.com/SatvikG7/Scalable-Notification-System/internal/db"
	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
)

func DeleteUser(c *fiber.Ctx) error {
	id := c.Params("id")

	if _, err := uuid.Parse(id); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Invalid user id",
		})
	}

	result := db.DB.Exec("DELETE FROM users WHERE id = ?", id)
	if result.Error != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to delete user",
		})
	}

	if result.RowsAffected == 0 {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{
			"error": "User not found",
		})
	}

	return c.JSON(
		fiber.Map{
			"message": "User deleted successfully",
		},
	)
}
