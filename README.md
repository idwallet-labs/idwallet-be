# idwallet-be

Kotlin Spring Boot MVC API for IDWallet credential and submission flows.

## Stack

- Kotlin
- Spring Boot MVC
- PostgreSQL schema + Flyway migration
- Docker
- GitHub Actions CI

## API Scope

- Credential list.
- Credential detail.
- Submission request creation.
- Submission request status.
- Submission response approval.

## Privacy Boundary

- Credentials expose `payloadHash` and metadata only.
- Raw credential payloads are not returned by the API.
