package middleware

import (
	"crypto/subtle"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
)

type Principal struct {
	Subject string
	Scopes  map[string]struct{}
}

func AdminAPIKeyAuth(expectedKey string) gin.HandlerFunc {
	return func(c *gin.Context) {
		if publicPath(c.Request.URL.Path) {
			c.Next()
			return
		}

		if subtle.ConstantTimeCompare([]byte(c.GetHeader("X-Admin-API-Key")), []byte(expectedKey)) != 1 {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{
				"error": gin.H{"message": "missing or invalid admin api key"},
			})
			return
		}
		c.Next()
	}
}

func DataPlaneJWTAuth(sharedSecret string, strict bool) gin.HandlerFunc {
	return func(c *gin.Context) {
		if !strict && c.GetHeader("Authorization") == "" {
			c.Next()
			return
		}

		tokenValue := strings.TrimPrefix(c.GetHeader("Authorization"), "Bearer ")
		if tokenValue == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": gin.H{"message": "bearer token required"}})
			return
		}

		claims := jwt.MapClaims{}
		token, err := jwt.ParseWithClaims(tokenValue, claims, func(token *jwt.Token) (any, error) {
			return []byte(sharedSecret), nil
		}, jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}))
		if err != nil || !token.Valid {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": gin.H{"message": "invalid bearer token"}})
			return
		}

		principal := Principal{
			Subject: subjectFromClaims(claims),
			Scopes:  scopesFromClaims(claims),
		}
		c.Set("principal", principal)
		c.Next()
	}
}

func RequireScopes(scopes []string) gin.HandlerFunc {
	return func(c *gin.Context) {
		if !CheckScopes(scopes, c) {
			return
		}
		c.Next()
	}
}

func CheckScopes(scopes []string, c *gin.Context) bool {
	if len(scopes) == 0 {
		return true
	}
	value, ok := c.Get("principal")
	if !ok {
		c.AbortWithStatusJSON(http.StatusForbidden, gin.H{"error": gin.H{"message": "required scopes missing"}})
		return false
	}
	principal, ok := value.(Principal)
	if !ok {
		c.AbortWithStatusJSON(http.StatusForbidden, gin.H{"error": gin.H{"message": "required scopes missing"}})
		return false
	}
	for _, scope := range scopes {
		if _, ok := principal.Scopes[scope]; !ok {
			c.AbortWithStatusJSON(http.StatusForbidden, gin.H{"error": gin.H{"message": "required scopes missing"}})
			return false
		}
	}
	return true
}

func publicPath(path string) bool {
	return path == "/healthz" || path == "/readyz" || path == "/metrics"
}

func subjectFromClaims(claims jwt.MapClaims) string {
	if subject, ok := claims["sub"].(string); ok {
		return subject
	}
	return ""
}

func scopesFromClaims(claims jwt.MapClaims) map[string]struct{} {
	out := map[string]struct{}{}
	switch raw := claims["scope"].(type) {
	case string:
		for _, scope := range strings.Fields(raw) {
			out[scope] = struct{}{}
		}
	case []any:
		for _, item := range raw {
			if scope, ok := item.(string); ok {
				out[scope] = struct{}{}
			}
		}
	}
	return out
}
