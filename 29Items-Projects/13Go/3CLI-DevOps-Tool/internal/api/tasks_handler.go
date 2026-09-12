package api

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/your-org/cli-devops-tool/internal/service"
	"gorm.io/gorm"
)

type TasksHandler struct {
	service *service.TaskService
	token   string
}

func NewTasksHandler(service *service.TaskService, token string) *TasksHandler {
	return &TasksHandler{service: service, token: token}
}

func (h *TasksHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if !h.authorized(r) {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	switch {
	case r.Method == http.MethodGet && r.URL.Path == "/tasks":
		h.list(w, r)
	case r.Method == http.MethodPost && r.URL.Path == "/tasks":
		h.create(w, r)
	case strings.HasPrefix(r.URL.Path, "/tasks/"):
		h.withID(w, r)
	default:
		writeError(w, http.StatusNotFound, "not found")
	}
}

func (h *TasksHandler) list(w http.ResponseWriter, r *http.Request) {
	tasks, err := h.service.List(r.Context(), r.URL.Query().Get("status"), 100)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "list tasks failed")
		return
	}
	writeJSON(w, http.StatusOK, tasks)
}

func (h *TasksHandler) create(w http.ResponseWriter, r *http.Request) {
	var input service.CreateTaskInput
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20)).Decode(&input); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json body")
		return
	}

	task, err := h.service.Create(r.Context(), input)
	if err != nil {
		writeError(w, statusForError(err), err.Error())
		return
	}
	writeJSON(w, http.StatusCreated, task)
}

func (h *TasksHandler) withID(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseUint(strings.TrimPrefix(r.URL.Path, "/tasks/"), 10, 64)
	if err != nil || id == 0 {
		writeError(w, http.StatusBadRequest, "invalid task id")
		return
	}

	switch r.Method {
	case http.MethodGet:
		task, err := h.service.Get(r.Context(), uint(id))
		if err != nil {
			writeError(w, statusForError(err), err.Error())
			return
		}
		writeJSON(w, http.StatusOK, task)
	case http.MethodPatch:
		var input service.UpdateTaskInput
		if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20)).Decode(&input); err != nil {
			writeError(w, http.StatusBadRequest, "invalid json body")
			return
		}
		task, err := h.service.Update(r.Context(), uint(id), input)
		if err != nil {
			writeError(w, statusForError(err), err.Error())
			return
		}
		writeJSON(w, http.StatusOK, task)
	case http.MethodDelete:
		if err := h.service.Delete(r.Context(), uint(id)); err != nil {
			writeError(w, statusForError(err), err.Error())
			return
		}
		w.WriteHeader(http.StatusNoContent)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (h *TasksHandler) authorized(r *http.Request) bool {
	if h.token == "" {
		return true
	}
	return r.Header.Get("Authorization") == "Bearer "+h.token
}

func statusForError(err error) int {
	switch {
	case errors.Is(err, service.ErrValidation):
		return http.StatusBadRequest
	case errors.Is(err, gorm.ErrRecordNotFound):
		return http.StatusNotFound
	default:
		return http.StatusInternalServerError
	}
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
