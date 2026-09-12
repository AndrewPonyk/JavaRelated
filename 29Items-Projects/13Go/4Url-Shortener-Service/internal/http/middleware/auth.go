package middleware

import (
	"crypto/subtle"
	"net/http"

	"github.com/gin-gonic/gin"
)

func APIKey(keys []string) gin.HandlerFunc {
	return func(c *gin.Context) {
		if len(keys) == 0 {
			c.Next()
			return
		}

		provided := c.GetHeader("X-API-Key")
		if provided == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{
				"error": gin.H{"code": "unauthorized", "message": "missing API key"},
			})
			return
		}

		for _, key := range keys {
			if subtle.ConstantTimeCompare([]byte(provided), []byte(key)) == 1 {
				c.Next()
				return
			}
		}

		c.AbortWithStatusJSON(http.StatusForbidden, gin.H{
			"error": gin.H{"code": "forbidden", "message": "invalid API key"},
		})
	}
}
