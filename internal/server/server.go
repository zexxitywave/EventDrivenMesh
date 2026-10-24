package server

import (
	"os"

	"github.com/SatvikG7/Scalable-Notification-System/internal/handlers"
	"github.com/SatvikG7/Scalable-Notification-System/internal/handlers/users"
	"github.com/SatvikG7/Scalable-Notification-System/internal/rabbitmq"

	"github.com/gofiber/fiber/v2"
)

func Init() error {
	app := fiber.New(fiber.Config{
		AppName: "Scalable Notification System",

		// Raise the default concurrency limit (256) so the server can handle
		// hundreds of simultaneous connections from a load tester without
		// queuing requests at the listener level.
		Concurrency: 1024 * 1024,

		// Pre-allocate read/write buffers large enough for typical JSON
		// notification payloads — avoids per-request heap growth under load.
		ReadBufferSize:  8 * 1024,
		WriteBufferSize: 8 * 1024,

		// Keep connections alive so the load tester can reuse TCP sockets
		// instead of re-dialing for every request.
		DisableKeepalive: false,
	})

	app.Get("/", func(c *fiber.Ctx) error {
		return c.SendString("Scalable Notification System")
	})

	app.Get("/health", func(c *fiber.Ctx) error {
		return c.SendString("OK")
	})

	app.Get("/users", users.GetUsers)
	app.Get("/users/get/:id", users.GetUser)
	app.Post("/users/create", users.CreateUser)
	app.Delete("/users/delete/:id", users.DeleteUser)

	app.Post("/notify", handlers.CreateNotification)

	app.Get("/stats", func(c *fiber.Ctx) error {
		return c.JSON(rabbitmq.Stats())
	})

	app.Listen("127.0.0.1:" + os.Getenv("PORT"))

	return nil
}
