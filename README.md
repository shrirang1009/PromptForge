# PromptForge

> **AI-Powered Distributed Code Workspace Platform**

PromptForge is a collaborative full-stack AI workspace — inspired by tools like v0.dev and Bolt.new — that lets users generate, preview, and edit complete codebases in real-time through conversational prompts. Unlike single-service AI applications, PromptForge is architected as a distributed microservices system, coordinating real-time AI code generation, distributed file storage, project-based access control, and user subscription enforcement using safe transactional boundaries and asynchronous messaging patterns.

---

## Table of Contents

- [Core Features](#core-features)
- [System Architecture](#system-architecture)
- [Microservices Breakdown](#microservices-breakdown)
- [Tech Stack](#tech-stack)
- [Infrastructure Components](#infrastructure-components)
- [System Design Patterns](#system-design-patterns)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [API Overview](#api-overview)
- [Subscription Tiers](#subscription-tiers)

---

## Core Features

### AI Coding Agent and Streaming Workspace
- **Prompt-to-codebase generation** — type a prompt like "build a React calculator app" and the system generates a complete multi-file project with a visual directory tree.
- **Server-Sent Events (SSE)** — AI output streams block-by-block directly to the browser, providing immediate visual feedback during generation.
- **Smart code parsing** — the backend automatically parses LLM output to extract code blocks and determine which files need to be created or modified.

### Live File System and Workspace Storage
- **Workspace tree management** — a dedicated service tracks project structures and sends structured directory layouts (file trees) to the frontend.
- **S3-compatible object storage** — all project files are physically written to and read from MinIO, supporting large projects without database bloat.
- **Physical file lifecycle cleanup** — deleting a project permanently purges the associated folder prefix from the S3 bucket to prevent database–storage mismatches.

### User Identity and Account Security
- **JWT-based authentication** — secure stateless sessions with gateway-level token validation.
- **Verification OTPs** — short-lived OTP tokens cached in Redis to handle email signup verification and password reset flows safely.
- **Role-based admin panel** — system administrators can search user listings, block or unblock accounts, and configure subscription plans.

### Subscription Plans and Razorpay Checkout
- **Tiered access** — Free, Pro, and Enterprise subscription tiers with configurable project and token limits.
- **Token quota enforcement** — daily AI token usage is audited before each prompt call; users are blocked or prompted to upgrade if they exceed their plan limits.
- **Razorpay checkout flow** — initiates secure checkout orders and validates payments by verifying incoming Razorpay webhook signatures before upgrading subscription status.

### Multi-User Collaboration and Permissions
- **Collaborator invites** — project owners can invite other users to join their workspace by email.
- **Role-based security** — actions are restricted based on project roles: Owner, Editor, and Viewer.

---

## System Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        React Frontend                            │
│             Vite · TypeScript · shadcn/ui   :5173                │
└───────────────────────────┬──────────────────────────────────────┘
                            │ HTTPS / SSE
┌───────────────────────────▼──────────────────────────────────────┐
│                        API Gateway                               │
│         JWT validation · CORS · Route dispatch   :8080           │
└────┬──────────────────┬──────────────────┬────────────────────────┘
     │                  │                  │
     ▼                  ▼                  ▼
┌─────────────┐  ┌──────────────┐  ┌──────────────────────┐
│   Account   │  │  Workspace   │  │    Intelligence      │
│   Service   │◄─┤   Service    │◄─┤      Service         │
│   :9050     │  │   :9020      │  │      :9030           │
└──────┬──────┘  └──────┬───────┘  └──────────┬───────────┘
       │                │                      │
       │         Kafka (Saga transactional outbox)
       │         ┌──────┴──────────────────────┘
       │         │
       │    ┌────▼────────────────────────────┐
       │    │          Apache Kafka           │
       │    │     FileStoreRequestEvent       │
       │    │     FileStoreResponseEvent      │
       │    └────┬──────────────┬─────────────┘
       │         │              │
       │    ┌────▼────┐    ┌────▼────┐
       │    │  MinIO  │    │  Redis  │
       │    │  :9000  │    │  :6379  │
       │    └─────────┘    └─────────┘
       │
  ┌────▼──────────────┐
  │    PostgreSQL      │
  │  Users · Projects  │
  │  Chat · Outbox     │
  └────────────────────┘

       ┌──────────────────────────────┐
       │    Discovery (Eureka) :8761  │
       │    Config Service     :8888  │
       └──────────────────────────────┘
```

All services register with Eureka on startup and pull their configuration from the Config Service. The API Gateway is the sole public-facing entry point — all external traffic passes through it.

---

## Microservices Breakdown

### 1. Discovery Service — Port `8761`

**Role:** Service Registration and Lookup (Eureka)

Dynamically registers all microservice instances as they boot up. Enables client-side load balancing via Spring Cloud LoadBalancer, allowing services to call each other by name (e.g. `account-service`, `workspace-service`) instead of hardcoded IPs.

### 2. Config Service — Port `8888`

**Role:** Centralized Configuration Repository

Serves external configuration to all backend services at startup. Pulls properties from the `config-repo` folder, making it easy to adjust environment properties, Kafka topics, database connection pools, and feature flags without rebuilding code.

### 3. API Gateway — Port `8080`

**Role:** Secure Routing and Entry Proxy (Spring Cloud Gateway)

Single entry point for all frontend and external calls. Responsibilities:
- Inspects incoming JWT tokens programmatically to enforce centralized auth.
- Handles global CORS policy.
- Routes requests dynamically: `/api/auth/**` → `account-service`, `/api/projects/**` → `workspace-service`, `/api/chat/**` → `intelligence-service`.

### 4. Account Service — Port `9050`

**Role:** User Security, Subscriptions, and Admin Control

Responsibilities:
- Manages secure logins, signups, password resets, and OTP caching in Redis.
- Tracks user roles, plan tiers (Free, Pro, Enterprise), and enforces daily usage limits.
- Communicates with the Razorpay API to generate checkout orders and verify webhook payment signatures.
- Serves Admin Dashboard APIs: search user listings, block/unblock accounts, configure subscription plans.

### 5. Workspace Service — Port `9020`

**Role:** Project Workspace and Storage Coordinator

Responsibilities:
- Manages workspace directory hierarchies, templates, and collaborator access control.
- Communicates with MinIO S3 storage to read/write generated project files and pack workspace directories into downloadable zip archives.
- Enforces access rules (`canViewProject`, `canEditProject`) before allowing code views or write operations.
- Functions as a **Saga Participant**: consumes code edits from Kafka, writes changes to MinIO, and publishes status receipts back to Kafka.

### 6. Intelligence Service — Port `9030`

**Role:** LLM Handler and Saga Orchestrator

Responsibilities:
- Connects to NVIDIA AI / OpenAI APIs to stream conversational code suggestions block-by-block to the browser via SSE.
- Parses LLM output to extract file paths and code contents, determining exactly what needs to be written or modified.
- Acts as the **Saga Orchestrator**: commits chat changes locally and manages the distributed outbox loop to Kafka.
- Runs the background **Saga Cleanup Scheduler**: sweeps and marks stale transactions as failed after 5 minutes of inactivity.

### 7. Common Library — Shared Dependency

**Role:** Shared Utility and Domain Library

Centralizes across all services:
- Domain DTOs: `UserDto`, `PlanDto`, `FileTreeDto`, `UsageSnapshotDto`
- Permission enums: `ProjectPermission`, `ProjectRole`
- Kafka event schemas: `FileStoreRequestEvent`, `FileStoreResponseEvent`
- Global exception handler (`GlobalExceptionHandler`) for consistent error responses across all services
- Shared security helper (`AuthUtil`) for extracting authenticated context from gateway-forwarded JWT headers

---

## Tech Stack

### Frontend
| Technology | Purpose |
|---|---|
| React 18 + TypeScript | UI framework |
| Vite | Build tooling |
| shadcn/ui + Radix UI | Component library |
| Tailwind CSS | Styling |
| TanStack Query | Server state management |
| React Router v6 | Client-side routing |
| CodeMirror 6 | In-browser code editor |

### Backend
| Technology | Purpose |
|---|---|
| Java 21 | Runtime |
| Spring Boot 3 | Application framework |
| Spring Cloud Gateway | API Gateway |
| Spring Cloud Netflix Eureka | Service discovery |
| Spring Cloud Config | Centralized configuration |
| Spring Cloud OpenFeign | Declarative HTTP clients |
| Resilience4j | Circuit breakers + fallbacks |
| Spring AI | LLM integration |
| Spring Kafka | Kafka producer/consumer |
| Spring Data JPA + Hibernate | ORM |
| Spring Security | Auth and authorization |
| JJWT | JWT token management |

### Infrastructure
| Technology | Purpose |
|---|---|
| Apache Kafka (KRaft) | Event streaming and Saga messaging |
| Redis 7 | OTP cache and session tokens |
| MinIO | S3-compatible file storage |
| PostgreSQL | Primary relational database |
| Docker + Docker Compose | Local infrastructure orchestration |

### External APIs
| Service | Purpose |
|---|---|
| NVIDIA AI / OpenAI | LLM code generation |
| Razorpay | Payment processing and webhooks |

---

## Infrastructure Components

All infrastructure services are defined in `docker-compose.yml` and start with a single command.

| Container | Image | Port | Purpose |
|---|---|---|---|
| `promptforge-kafka` | `confluentinc/confluent-local:7.5.0` | `9092` | Message broker (KRaft mode — no ZooKeeper) |
| `promptforge-redis` | `redis:7-alpine` | `6379` | OTP and token cache |
| `promptforge-minio` | `minio/minio:latest` | `9000`, `9001` | S3-compatible object storage |
| `promptforge-minio-init` | `minio/mc:latest` | — | Auto-creates `projects` and `templates` buckets |
| `promptforge-kafka-ui` | `provectuslabs/kafka-ui:latest` | `8085` | Kafka management UI |
| `promptforge-redis-ui` | `redislabs/redisinsight:latest` | `5540` | Redis management UI |

---

## System Design Patterns

### Asynchronous Saga Pattern (Transactional Outbox)

When a user prompts the AI to write or edit code, both the database record (chat log) and the physical file write (in MinIO) must stay consistent. PromptForge solves this with a Saga flow using a Transactional Outbox:

1. **Write consistency** — `intelligence-service` saves the chat messages and a `PENDING` saga event within a single local DB transaction.
2. **Outbox publishing** — after the DB transaction commits, it publishes a `FileStoreRequestEvent` to Kafka.
3. **Idempotent storage** — `workspace-service` consumes the event, checks an idempotency list to prevent double-processing, writes files to MinIO, and publishes a `FileStoreResponseEvent` back to Kafka.
4. **State transition** — `intelligence-service` consumes the response and marks the saga as `CONFIRMED` or `FAILED`.
5. **Auto-recovery** — `SagaCleanupScheduler` sweeps the database every minute and marks any pending events older than 5 minutes as `FAILED` to release system resources.

### Resilience4j Circuit Breakers

All OpenFeign inter-service calls are wrapped with Resilience4j circuit breakers to prevent a single service outage from cascading:

- **Workspace → Account** — if Account Service fails, project creation falls back to the standard Free plan limit (5 projects) so users can still work.
- **Intelligence → Workspace** — if Workspace Service is offline during an AI stream, the chat gracefully returns a "Workspace service unavailable" warning instead of an exception.
- **Account → Intelligence** — if Intelligence Service fails to return today's token counter, usage checks fall back to 0 tokens consumed to avoid blocking checkout flows.

### JWT-Based Authentication at the Gateway

The API Gateway validates JWT tokens on every incoming request before any traffic reaches the backend microservices. Authenticated user context (user ID, role) is forwarded via custom headers to downstream services, which use `AuthUtil` from the common library to extract it without re-validating the token.

### Role-Based Project Access Control

Project operations are guarded by `SecurityExpressions` in Workspace Service using `@PreAuthorize` annotations:

- `canViewProject(projectId)` — Owner, Editor, and Viewer roles
- `canEditProject(projectId)` — Owner and Editor roles only
- `isProjectOwner(projectId)` — Owner role only (delete, invite management)

---

## Project Structure

```
PromptForge/
├── PromptForge-Frontend/          # React + Vite frontend
│   ├── src/
│   │   ├── components/            # ChatPanel, CodeEditor, FileTree, PreviewPanel, etc.
│   │   ├── pages/                 # Index, ProjectView, ProjectsDashboard, BillingPage, AdminPage
│   │   ├── lib/
│   │   │   ├── api.ts             # All API calls
│   │   │   └── types.ts           # Shared TypeScript interfaces
│   │   └── hooks/                 # use-stream-parser, use-toast, use-mobile
│   └── package.json
│
├── common-lib/                    # Shared Java library (Maven)
│   └── src/main/java/.../
│       ├── dto/                   # UserDto, PlanDto, FileTreeDto, UsageSnapshotDto
│       ├── event/                 # FileStoreRequestEvent, FileStoreResponseEvent
│       ├── exception/             # GlobalExceptionHandler
│       └── security/              # AuthUtil
│
├── config-repo/                   # Externalized configuration files (per service)
│
├── config-service/                # Spring Cloud Config Server
├── discovery-service/             # Eureka Server
├── api-gateway/                   # Spring Cloud Gateway
│
├── account-service/               # User auth, subscriptions, admin, Razorpay
│   └── src/main/java/.../
│       ├── controller/            # AuthController, BillingController, AdminController
│       ├── service/               # AuthService, SubscriptionService, RazorpayService
│       └── entity/                # User, Subscription, Plan, OtpToken
│
├── workspace-service/             # Projects, files, collaborators, MinIO
│   └── src/main/java/.../
│       ├── controller/            # ProjectController, FileController, ProjectMemberController
│       ├── consumer/              # FileStorageConsumer (Kafka)
│       ├── service/               # ProjectService, ProjectFileService, ProjectMemberService
│       └── entity/                # Project, ProjectFile, ProjectMember, Preview
│
├── intelligence-service/          # LLM, SSE streaming, Saga orchestration
│   └── src/main/java/.../
│       ├── controller/            # ChatController
│       ├── llm/                   # LlmResponseParser, PromptUtils, TokenUsageAuditAdvisor
│       ├── consumer/              # Saga response consumer (Kafka)
│       ├── service/               # ChatService, SagaService, SagaCleanupScheduler
│       └── entity/                # ChatMessage, ChatEvent, SagaTransaction
│
└── docker-compose.yml             # Kafka, Redis, MinIO, management UIs
```

---

## Getting Started

### Prerequisites

- Java 21 JDK
- Maven 3.9+
- Node.js 18+ (or Bun)
- Docker and Docker Compose

### Step 1: Start Infrastructure

```bash
docker-compose up -d
```

This starts Kafka, Redis, MinIO (with auto-created buckets), Kafka UI, and RedisInsight. Wait for all health checks to pass (about 60 seconds for Kafka).

### Step 2: Configure Environment

Copy and fill in the required environment variables (see [Environment Variables](#environment-variables) section) in your `config-repo` property files or export them in your shell.

### Step 3: Build the Common Library

```bash
cd common-lib
./mvnw clean install -DskipTests
cd ..
```

### Step 4: Start Backend Services (in order)

```bash
# 1. Config Service
cd config-service && ./mvnw spring-boot:run &

# 2. Discovery Service (wait ~5s for Config Service to be ready)
cd ../discovery-service && ./mvnw spring-boot:run &

# 3. API Gateway
cd ../api-gateway && ./mvnw spring-boot:run &

# 4. Backend Microservices (can start in parallel)
cd ../account-service && ./mvnw spring-boot:run &
cd ../workspace-service && ./mvnw spring-boot:run &
cd ../intelligence-service && ./mvnw spring-boot:run &
```

### Step 5: Start the Frontend

```bash
cd PromptForge-Frontend
npm install        # or: bun install
npm run dev        # or: bun dev
```

The app will be available at `http://localhost:5173`.

### Service Startup Order Summary

| Order | Service | Port | Wait for |
|---|---|---|---|
| 1 | Config Service | 8888 | — |
| 2 | Discovery Service | 8761 | Config Service |
| 3 | API Gateway | 8080 | Discovery Service |
| 4 | Account Service | 9050 | API Gateway |
| 4 | Workspace Service | 9020 | API Gateway |
| 4 | Intelligence Service | 9030 | API Gateway |

---

## Environment Variables

The following variables must be set (via `config-repo` YAML files or environment):

### Account Service
| Variable | Description |
|---|---|
| `spring.datasource.url` | PostgreSQL JDBC URL |
| `spring.datasource.username` | DB username |
| `spring.datasource.password` | DB password |
| `jwt.secret` | JWT signing secret (min 256-bit) |
| `razorpay.key-id` | Razorpay API key ID |
| `razorpay.key-secret` | Razorpay API key secret |
| `razorpay.webhook-secret` | Razorpay webhook signature secret |
| `spring.redis.host` | Redis host (default: `localhost`) |

### Intelligence Service
| Variable | Description |
|---|---|
| `spring.datasource.url` | PostgreSQL JDBC URL |
| `spring.ai.openai.api-key` | NVIDIA AI or OpenAI API key |
| `spring.ai.openai.base-url` | API base URL (override for NVIDIA) |
| `spring.kafka.bootstrap-servers` | Kafka broker address |

### Workspace Service
| Variable | Description |
|---|---|
| `spring.datasource.url` | PostgreSQL JDBC URL |
| `minio.endpoint` | MinIO endpoint URL (default: `http://localhost:9000`) |
| `minio.access-key` | MinIO access key |
| `minio.secret-key` | MinIO secret key |
| `spring.kafka.bootstrap-servers` | Kafka broker address |

### API Gateway
| Variable | Description |
|---|---|
| `jwt.secret` | JWT signing secret (must match Account Service) |

---

## API Overview

All routes are prefixed with `/api` and proxied through the API Gateway on port `8080`.

### Auth (`/api/auth`)
| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/signup/init` | Send OTP to email for signup |
| `POST` | `/auth/signup/complete` | Complete signup with OTP |
| `POST` | `/auth/login` | Login, returns JWT |
| `POST` | `/auth/forgot-password` | Send password reset OTP |
| `POST` | `/auth/reset-password` | Reset password with OTP |

### Projects (`/api/projects`)
| Method | Path | Description |
|---|---|---|
| `GET` | `/projects` | List user's projects |
| `POST` | `/projects` | Create a new project |
| `GET` | `/projects/{id}` | Get project details |
| `DELETE` | `/projects/{id}` | Delete project and files |
| `GET` | `/projects/{id}/files` | Get file tree |
| `GET` | `/projects/{id}/files/content` | Get file content |

### Chat (`/api/chat`)
| Method | Path | Description |
|---|---|---|
| `GET` | `/chat/{projectId}/stream` | SSE stream for AI code generation |
| `GET` | `/chat/{projectId}/history` | Get chat history |

### Billing (`/api/billing`)
| Method | Path | Description |
|---|---|---|
| `GET` | `/billing/plans` | List available plans |
| `POST` | `/billing/checkout` | Create Razorpay checkout order |
| `POST` | `/billing/webhook` | Handle Razorpay payment webhook |
| `GET` | `/billing/subscription` | Get current subscription |

### Admin (`/api/admin`)
| Method | Path | Description |
|---|---|---|
| `GET` | `/admin/dashboard` | Dashboard stats |
| `GET` | `/admin/users` | List all users |
| `POST` | `/admin/users/{id}/block` | Block a user |
| `POST` | `/admin/users/{id}/unblock` | Unblock a user |
| `POST` | `/admin/plans` | Create or update a plan |

---

## Subscription Tiers

| Feature | Free | Pro | Enterprise |
|---|---|---|---|
| Projects | 5 | 20 | Unlimited |
| Daily AI tokens | Limited | Higher limit | Unlimited |
| Collaborators | — | Yes | Yes |
| File downloads | Yes | Yes | Yes |
| Priority support | — | — | Yes |

Token quotas are enforced by `intelligence-service` via a pre-call check against the daily usage counter stored in the Account Service. If a user exceeds their quota, the SSE stream is terminated before the LLM call is made.

---

## Management UIs

| UI | URL | Purpose |
|---|---|---|
| Kafka UI | http://localhost:8085 | Browse topics, consumer groups, messages |
| RedisInsight | http://localhost:5540 | Inspect Redis keys, OTP cache |
| MinIO Console | http://localhost:9001 | Browse S3 buckets and project files |
| Eureka Dashboard | http://localhost:8761 | View registered service instances |

---

## License

This project is the intellectual property of its author. All rights reserved.
