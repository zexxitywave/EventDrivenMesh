package models

type Preference struct {
	Low    string `json:"low" gorm:"column:preference_low_channel;size:50;not null"`
	Medium string `json:"medium" gorm:"column:preference_medium_channel;size:50;not null"`
	High   string `json:"high" gorm:"column:preference_high_channel;size:50;not null"`
}
