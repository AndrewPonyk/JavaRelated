package metrics

import (
	"sync"

	"github.com/prometheus/client_golang/prometheus"
)

var registerOnce sync.Once

var (
	TasksProcessed = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "task_queue_tasks_processed_total",
			Help: "Total number of processed tasks by status.",
		},
		[]string{"status"},
	)

	TaskDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "task_queue_task_duration_seconds",
			Help:    "Task processing duration.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"type"},
	)

	QueueDepth = prometheus.NewGaugeVec(
		prometheus.GaugeOpts{
			Name: "task_queue_depth",
			Help: "Current task queue depth by queue name.",
		},
		[]string{"queue"},
	)
)

func Register() {
	registerOnce.Do(func() {
		prometheus.MustRegister(TasksProcessed, TaskDuration, QueueDepth)
	})
}
