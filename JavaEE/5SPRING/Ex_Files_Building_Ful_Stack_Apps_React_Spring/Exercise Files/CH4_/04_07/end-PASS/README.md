# Contact Management CRM - Spring Boot + React Full-Stack Application

A full-stack contact management application built with Spring Boot backend and React frontend.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 2.1.4, Java 1.8 |
| Database | H2 (in-memory) |
| ORM | JPA/Hibernate |
| Frontend | React 18.2.0 |
| CSS | Materialize 1.0.0 |
| Build | Maven (backend), npm (frontend) |

## Running the Application

**Backend (port 8080):**
```bash
./mvnw spring-boot:run
```

**Frontend (port 3000):**
```bash
cd client
npm install
npm start
```

---

## Educational Comments Guide

This codebase contains **45 educational comments** (`|su:1` to `|su:45`) placed at key learning points. Comments marked with `--c` indicate critical concepts.

### Backend (Spring Boot) - Comments 1-21

| File | Comments | Key Learning Points |
|------|----------|---------------------|
| `ReactAndSpringDataRestApplication.java` | 1-2 | `@SpringBootApplication`--c, `SpringApplication.run()` |
| `Contact.java` | 3-6 | `@Entity`--c, `@Data`, `@Id @GeneratedValue`--c |
| `ContactRepository.java` | 7-8 | `CrudRepository`--c, `@RepositoryRestResource` |
| `ContactsController.java` | 9-17 | `@RestController`--c, `@CrossOrigin`--c, Constructor injection--c, `@Valid @RequestBody`--c |
| `DemoLoader.java` | 18-21 | `CommandLineRunner`--c, startup data seeding |

### Frontend (React) - Comments 22-45

| File | Comments | Key Learning Points |
|------|----------|---------------------|
| `index.js` | 22 | `ReactDOM.render()`--c |
| `App.js` | 23-24 | Root functional component |
| `Contacts.js` | 25-32 | Class component--c, `this.state`--c, `componentDidMount()`--c, `key={}`--c |
| `AddContacts.js` | 33-42 | `useRef()`--c, `fetch() POST`--c, form handling |
| `SingleContact.js` | 43-45 | Presentational component--c, JSX interpolation |

### Critical Concepts (marked with --c)

| # | Concept | Description |
|---|---------|-------------|
| 1 | `@SpringBootApplication` | Combined annotation enabling auto-config, component scan |
| 4 | `@Entity` | JPA entity - maps class to database table |
| 5 | `@Id @GeneratedValue` | Primary key with auto-increment |
| 7 | `CrudRepository` | Spring Data interface providing CRUD operations |
| 9 | `@RestController` | REST API controller returning JSON |
| 11 | `@CrossOrigin` | CORS config for frontend-backend communication |
| 12 | Constructor injection | Dependency injection pattern |
| 16 | `@Valid @RequestBody` | Validation + JSON deserialization |
| 19 | `CommandLineRunner` | Startup hook for initialization |
| 22 | `ReactDOM.render()` | React entry point mounting to DOM |
| 25 | Class component | Container pattern managing state |
| 26 | `this.state` | React state triggering re-renders |
| 27 | `componentDidMount()` | Lifecycle hook for API calls |
| 32 | `key={}` | React list key for efficient diffing |
| 33 | `useRef()` | React hook for DOM refs |
| 38 | `fetch() POST` | HTTP POST to backend API |
| 43 | Presentational component | Stateless component receiving props |

---

## Application Flow

```
INITIAL LOAD:
Browser -> App.js -> Contacts.js (componentDidMount)
                  -> fetch('http://localhost:8080/api/contacts')
                  -> Spring Boot: ContactsController.contacts()
                  -> ContactRepository.findAll()
                  -> H2 Database
                  <- JSON response
                  <- setState() triggers re-render
                  <- map() -> SingleContact components

ADD NEW CONTACT:
AddContacts form -> onSubmit
                 -> fetch POST to '/api/contacts'
                 -> ContactsController.createContact()
                 -> ContactRepository.save()
                 <- saved contact with ID
                 -> window.location.reload()
```

## Project Structure

```
end-PASS/
├── src/main/java/com/crm/crm/model/
│   ├── ReactAndSpringDataRestApplication.java  [|su:1-2]
│   ├── Contact.java                            [|su:3-6]
│   ├── ContactRepository.java                  [|su:7-8]
│   ├── ContactsController.java                 [|su:9-17]
│   └── DemoLoader.java                         [|su:18-21]
├── src/main/resources/
│   └── application.properties
├── client/src/
│   ├── index.js                                [|su:22]
│   ├── App.js                                  [|su:23-24]
│   └── components/
│       ├── Contacts.js                         [|su:25-32]
│       ├── AddContacts.js                      [|su:33-42]
│       └── SingleContact.js                    [|su:43-45]
└── pom.xml
```
