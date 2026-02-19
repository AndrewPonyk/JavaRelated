# Java/Python/JS Browser IDE

A browser-based IDE for writing and executing **Java**, **Python**, and **JavaScript** code. Built with React, Monaco Editor, and Spring Boot.

![IDE Screenshot](./screenshot.png)

## Features

- 📁 File explorer with create/rename/delete
- 📑 Tabbed editor with multiple files
- 🎨 Syntax highlighting for Java, Python, JavaScript, and more
- ▶️ Run code directly in the browser
- 📤 Console output with clear on each run
- 🐳 Docker support for easy deployment

## Tech Stack

**Frontend:**
- React 18
- Monaco Editor (VS Code's editor)
- Vite (build tool)

**Backend:**
- Spring Boot 3.5.10
- Java 17
- Maven

**Runtime:**
- Python 3 (for `.py` execution)

## Quick Start

### Run Locally

```bash
# 1. Build frontend
npm run build

# 2. Copy to Spring Boot static folder
cp -r dist/* java-ide-backend/src/main/resources/static/

# 3. Run Spring Boot
cd java-ide-backend
mvn spring-boot:run
```

Open `http://localhost:8080`

## Build & Push Docker Image

```bash
# 1. Build frontend
npm run build

# 2. Copy to static folder
cp -r dist/* java-ide-backend/src/main/resources/static/

# 3. Build Docker image (from backend folder)
cd java-ide-backend
docker build -t andrew9999/java-python-ide:latest .

# 4. Push to Docker Hub
docker login
docker push andrew9999/java-python-ide:latest
```

## Run from Docker

```bash
docker run -p 8080:8080 andrew9999/java-python-ide:latest
```

Then open `http://localhost:8080`

## Usage

1. **Create a file:** Click `+` in the explorer, enter filename (e.g., `Main.java`, `script.py`, `app.js`)
2. **Write code:** Use the Monaco editor with syntax highlighting
3. **Run:** Click `▶ Run File` button
4. **View output:** Results appear in the console panel

### Example Files

**Java (`Main.java`):**
```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from Java!");
    }
}
```

**Python (`script.py`):**
```python
print("Hello from Python!")
for i in range(5):
    print(i)
```

**JavaScript (`app.js`):**
```javascript
console.log("Hello from JavaScript!");
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/execute` | Execute Java code |
| POST | `/api/execute-python` | Execute Python code |

### Request Body
```json
{
  "className": "Main",
  "code": "public class Main { ... }"
}
```

### Response
```json
{
  "success": true,
  "output": "Hello from Java!\n",
  "error": null
}
```

## Project Structure

```
.
├── src/                          # React frontend source
│   ├── App.jsx
│   ├── components/
│   │   ├── Editor.jsx
│   │   ├── FileTree.jsx
│   │   └── TabBar.jsx
│   └── hooks/
│       └── useFileSystem.js
├── java-ide-backend/             # Spring Boot backend
│   ├── src/main/java/com/ap/
│   │   ├── controller/
│   │   ├── dto/
│   │   └── service/
│   ├── src/main/resources/static/  # Built frontend files
│   ├── pom.xml
│   └── Dockerfile
├── package.json
└── vite.config.js
```

## Security Note

⚠️ This IDE executes arbitrary code. Do not expose publicly without:
- Timeout limits (currently 10s for Python)
- Memory limits
- Security sandboxing
- Authentication

For local/development use only.

## License

MIT
