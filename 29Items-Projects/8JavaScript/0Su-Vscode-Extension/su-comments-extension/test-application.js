// This is a test file to verify the SU Comments extension functionality

// |su:1 Initial setup for the application --
const app = {
  init: function() {
    console.log("Application initialized");
  }
};

// |su:2 Configuration settings ++
const config = {
  apiUrl: "https://api.example.com",
  timeout: 5000
};

// |su:3 Database connection logic --c
function connectToDatabase() {
  // Complex connection logic here
  const connection = {
    host: config.apiUrl,
    port: 5432,
    // |su:4 Error handling for database --
    onError: function(error) {
      console.error("Database error:", error);
    }
  };
  
  // |su:5 Temporary workaround --h
  setTimeout(() => {
    console.log("Connected to DB");
  }, 100);
  
  return connection;
}

// |su:6 Main application logic --
function main() {
  app.init();
  const db = connectToDatabase();
  
  // |su:7 User authentication flow ++
  function authenticateUser(username, password) {
    // Simple auth logic
    return username && password;
  }
  
  // |su:8 Data processing pipeline --b
  const processData = (data) => {
    // This is bad code that should be refactored
    eval(data); // Never do this in production!
  };
  
  // |su:9 Logging mechanism --
  const logger = {
    info: (msg) => console.log(`INFO: ${msg}`),
    error: (msg) => console.error(`ERROR: ${msg}`)
  };
  
  logger.info("Main function executed");
}

// |su:10 Cleanup and exit ++
function cleanup() {
  console.log("Cleaning up resources...");
}

// Execute main function
main();

// |su:11 Final thoughts --
// This application needs refactoring
// Consider using a proper framework