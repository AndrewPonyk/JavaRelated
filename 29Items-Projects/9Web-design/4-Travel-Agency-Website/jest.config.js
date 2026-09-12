module.exports = {
  collectCoverageFrom: [
    "src/api/**/*.js",
    "src/assets/js/filters.js",
    "!src/api/db/migrate.js",
    "!src/api/db/pool.js",
    "!src/api/db/test-database.js"
  ],
  coverageThreshold: {
    global: {
      branches: 70,
      functions: 70,
      lines: 70,
      statements: 70
    }
  },
  testEnvironment: "node",
  testMatch: ["<rootDir>/tests/unit/**/*.test.js"]
};
