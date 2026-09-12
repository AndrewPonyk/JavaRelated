module.exports = {
  test: {
    include: ["tests/unit/**/*.test.js", "tests/integration/**/*.test.js"],
    environment: "node",
    globals: true,
    coverage: {
      provider: "v8",
      reporter: ["text", "json", "html"],
      include: ["netlify/functions/**/*.js"],
      thresholds: {
        lines: 70,
        functions: 70,
        branches: 60,
        statements: 70,
      },
    },
  },
};
