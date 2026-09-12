package handlers

import (
	"errors"
	"log/slog"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"

	"github.com/example/url-shortener-service/internal/models"
	"github.com/example/url-shortener-service/internal/qrcode"
	"github.com/example/url-shortener-service/internal/service"
)

type URLHandler struct {
	urls      *service.URLService
	qr        *qrcode.Generator
	logger    *slog.Logger
	adminKeys []string
}

func NewURLHandler(urls *service.URLService, qr *qrcode.Generator, logger *slog.Logger, adminKeys []string) *URLHandler {
	return &URLHandler{urls: urls, qr: qr, logger: logger, adminKeys: adminKeys}
}

func (h *URLHandler) Create(c *gin.Context) {
	var req models.CreateURLRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		respondError(c, http.StatusBadRequest, "invalid_request", err.Error())
		return
	}

	resp, err := h.urls.Create(c.Request.Context(), req)
	if err != nil {
		h.handleServiceError(c, err)
		return
	}

	c.JSON(http.StatusCreated, resp)
}

func (h *URLHandler) List(c *gin.Context) {
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "50"))
	resp, err := h.urls.List(c.Request.Context(), limit)
	if err != nil {
		h.handleServiceError(c, err)
		return
	}
	c.JSON(http.StatusOK, resp)
}

func (h *URLHandler) Get(c *gin.Context) {
	resp, err := h.urls.Get(c.Request.Context(), c.Param("code"))
	if err != nil {
		h.handleServiceError(c, err)
		return
	}

	c.JSON(http.StatusOK, resp)
}

func (h *URLHandler) Update(c *gin.Context) {
	var req models.UpdateURLRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		respondError(c, http.StatusBadRequest, "invalid_request", err.Error())
		return
	}

	resp, err := h.urls.Update(c.Request.Context(), c.Param("code"), req)
	if err != nil {
		h.handleServiceError(c, err)
		return
	}

	c.JSON(http.StatusOK, resp)
}

func (h *URLHandler) Delete(c *gin.Context) {
	if err := h.urls.Delete(c.Request.Context(), c.Param("code")); err != nil {
		h.handleServiceError(c, err)
		return
	}

	c.Status(http.StatusNoContent)
}

func (h *URLHandler) Analytics(c *gin.Context) {
	resp, err := h.urls.Analytics(c.Request.Context(), c.Param("code"))
	if err != nil {
		h.handleServiceError(c, err)
		return
	}

	c.JSON(http.StatusOK, resp)
}

func (h *URLHandler) Redirect(c *gin.Context) {
	code := c.Param("code")
	target, err := h.urls.Resolve(c.Request.Context(), code, service.ClickMetadata{
		Referrer:  c.GetHeader("Referer"),
		UserAgent: c.GetHeader("User-Agent"),
		IP:        c.ClientIP(),
		Country:   c.GetHeader("CF-IPCountry"),
	})
	if err != nil {
		h.handleServiceError(c, err)
		return
	}

	c.Redirect(http.StatusFound, target)
}

func (h *URLHandler) QR(c *gin.Context) {
	resp, err := h.urls.Get(c.Request.Context(), c.Param("code"))
	if err != nil {
		h.handleServiceError(c, err)
		return
	}

	png, err := h.qr.PNG(resp.ShortURL)
	if err != nil {
		h.logger.Error("generate qr", "error", err, "short_code", resp.ShortCode)
		respondError(c, http.StatusInternalServerError, "qr_generation_failed", "unable to generate QR code")
		return
	}

	c.Header("Cache-Control", "public, max-age=3600")
	c.Data(http.StatusOK, "image/png", png)
}

func (h *URLHandler) handleServiceError(c *gin.Context, err error) {
	switch {
	case errors.Is(err, service.ErrNotFound):
		respondError(c, http.StatusNotFound, "not_found", "short URL not found")
	case errors.Is(err, service.ErrConflict):
		respondError(c, http.StatusConflict, "conflict", "short code already exists")
	case errors.Is(err, service.ErrValidation):
		respondError(c, http.StatusBadRequest, "validation_failed", err.Error())
	default:
		h.logger.Error("request failed", "error", err, "path", c.Request.URL.Path)
		respondError(c, http.StatusInternalServerError, "internal_error", "unexpected server error")
	}
}

func respondError(c *gin.Context, status int, code string, message string) {
	c.JSON(status, gin.H{
		"error": gin.H{
			"code":    code,
			"message": message,
		},
	})
}
