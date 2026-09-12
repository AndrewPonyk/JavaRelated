require("dotenv").config();

const { createApp } = require("./app");

const port = Number(process.env.PORT || 3000);

async function main() {
  if (process.env.USE_TEST_DATABASE === "true") {
    const { configureTestDatabase } = require("./api/db/test-database");
    await configureTestDatabase();
  }

  const app = createApp();

  app.listen(port, () => {
    console.log(`Travel Agency Website running at http://localhost:${port}`);
  });
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
