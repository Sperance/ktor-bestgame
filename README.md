# ktor-bestgame

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)
- The [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). You'll need to [request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) to join.

## Features

Here's a list of features included in this project:

| Name                                                                   | Description                                                                        |
| ------------------------------------------------------------------------|------------------------------------------------------------------------------------ |
| [Call Logging](https://start.ktor.io/p/call-logging)                   | Logs client requests                                                               |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                     |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers |
| [Routing](https://start.ktor.io/p/routing)                             | Provides a structured routing DSL                                                  |
| [Default Headers](https://start.ktor.io/p/default-headers)             | Adds a default set of headers to HTTP responses                                    |
| [CORS](https://start.ktor.io/p/cors)                                   | Enables Cross-Origin Resource Sharing (CORS)                                       |

## Configuration

Since 0.21.0 nothing secret lives in the source; the server reads its environment:

| Variable               | Default                     | Meaning                                                   |
|------------------------|-----------------------------|-----------------------------------------------------------|
| `MONGO_URI`            | `mongodb://localhost:27017` | MongoDB connection string (a replica set: writes use transactions) |
| `MONGO_DB`             | `mongobase`                 | Database name                                             |
| `PORT`                 | `8080`                      | HTTP port                                                 |
| `CORS_HOSTS`           | unset — no CORS             | Comma-separated origins allowed from a browser            |
| `ADMIN_PASSWORD`       | unset — no administrator    | Password of the seeded `admin` account                    |
| `TEST_PLAYER_PASSWORD` | unset — no test player      | Password of the seeded `test1` account                    |

Clients sign in with `POST /api/v1/user/login` and send the returned token as
`Authorization: Bearer <token>`. `GET /system/version` names the running version.

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                    | Description                                                          |
| -----------------------------------------|---------------------------------------------------------------------- |
| `./gradlew test`                        | Run the tests                                                        |
| `./gradlew build`                       | Build everything                                                     |
| `./gradlew buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`                  | Build the docker image to use with the fat JAR                       |
| `./gradlew publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `./gradlew run`                         | Run the server                                                       |
| `./gradlew runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

