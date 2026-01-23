-- Database initialization script for Docker
-- This runs automatically when the MySQL container starts

-- Create database if not exists (already handled by MYSQL_DATABASE env var)
-- CREATE DATABASE IF NOT EXISTS customer_api;

-- Use the database
USE customer_api;

-- Set character encoding
ALTER DATABASE customer_api CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant privileges (if needed for additional users)
-- CREATE USER IF NOT EXISTS 'apiuser'@'%' IDENTIFIED BY 'apipassword';
-- GRANT ALL PRIVILEGES ON customer_api.* TO 'apiuser'@'%';
-- FLUSH PRIVILEGES;

-- Note: Tables are created by Flask-Migrate, not this script
-- This script is for database-level configuration only
