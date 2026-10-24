package models

// Channels supported by the delivery layer.
const (
	ChannelEmail = "email"
	ChannelSMS   = "sms"
	ChannelPush  = "push"
)

// ValidChannels lists every delivery channel a user preference may point at.
var ValidChannels = []string{ChannelEmail, ChannelSMS, ChannelPush}

// IsValidChannel reports whether the given channel is supported.
func IsValidChannel(channel string) bool {
	for _, valid := range ValidChannels {
		if valid == channel {
			return true
		}
	}
	return false
}
