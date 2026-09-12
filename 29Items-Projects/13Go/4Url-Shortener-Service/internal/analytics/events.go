package analytics

import "time"

type ClickEvent struct {
	ShortCode string
	Referrer  string
	UserAgent string
	IP        string
	Country   string
	Occurred  time.Time
}
