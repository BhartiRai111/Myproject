# StoreHub - General Store Management System

A simple full-stack store management application.

This is the initial foundation: **Authentication, User Management, and a basic Dashboard**.
Other modules (Products, Inventory, Suppliers, Purchases, Sales, Customers, Payments, Reports)
are scaffolded as disabled "Coming Soon" navigation items and will be implemented later.

## Tech Stack

- **Backend:** Java 17, Spring Boot 3, Spring Data JPA, Spring Security, JWT, Maven
- **Frontend:** React 18, TypeScript, React-Bootstrap, React Router, Axios (Vite)
- **Database:** MySQL

## Project Structure

```
backend/    Spring Boot REST API
frontend/   React + TypeScript SPA
```

## Backend Setup

1. Create a MySQL database (or let the app create it automatically):
   ```sql
   CREATE DATABASE storehub_db;
   ```
2. Configure the database connection via environment variables (or edit
   `backend/src/main/resources/application.properties` directly):
   ```
   DB_USERNAME=root
   DB_PASSWORD=your_password
   JWT_SECRET=a-long-random-secret
   CORS_ALLOWED_ORIGINS=http://localhost:5173
   ```
3. Build and run:
   ```bash
   cd backend
   mvn spring-boot:run
   ```
   The API starts on `http://localhost:8080`.

## Frontend Setup

1. Copy `.env.example` to `.env` and adjust if needed:
   ```
   VITE_API_BASE_URL=http://localhost:8080/api
   ```
2. Install and run:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
   The app starts on `http://localhost:5173`.

## Default Roles

- `ADMIN` – full access, manages users
- `STORE_MANAGER` – dashboard + future operational modules
- `STAFF` – dashboard + future operational modules

Public registration (`/register`) only allows creating `STORE_MANAGER` or `STAFF`
accounts. `ADMIN` accounts can only be created by an existing ADMIN from the
User Management screen.

## Key API Endpoints

| Method | Endpoint                | Description                     | Access        |
|--------|--------------------------|----------------------------------|---------------|
| POST   | /api/auth/register       | Register a new account           | Public        |
| POST   | /api/auth/login          | Login, returns JWT + user info   | Public        |
| POST   | /api/auth/logout         | Logout                           | Authenticated |
| GET    | /api/auth/me             | Get current logged-in user       | Authenticated |
| GET    | /api/users               | List/search/filter users         | ADMIN         |
| GET    | /api/users/{id}          | Get a user by id                 | ADMIN         |
| POST   | /api/users               | Create a user (any role)         | ADMIN         |
| PUT    | /api/users/{id}          | Update a user                    | ADMIN         |
| PATCH  | /api/users/{id}/status   | Activate/Deactivate a user       | ADMIN         |
