# Asynchronous Order Processing System

## Overview

This project implements a backend system that accepts orders asynchronously and processes them in the background.

The system is designed to remain responsive under load, handle retries safely, and ensure that each order is processed exactly once, even in the presence of failures.

The focus of this project is correctness, reliability, and clear backend design.

---

## Problem Statement

In many real-world systems, order processing involves multiple steps and can take time. Blocking clients until processing completes reduces system responsiveness and reliability.

This system decouples order submission from order execution by processing orders asynchronously, allowing the API to respond quickly while background workers handle the actual processing.

---

## High-Level Design

The system consists of the following components:

- **Order API**
  - Accepts order requests
  - Performs validation and idempotency checks
  - Persists orders with an initial state
  - Returns immediate acknowledgment

- **Order Store (Database)**
  - Stores order details
  - Stores order state transitions
  - Acts as the source of truth

- **Background Worker**
  - Periodically picks pending orders
  - Processes orders step by step
  - Updates order state safely

---

## Order Lifecycle

Orders transition through the following states:

- `RECEIVED`
- `PROCESSING`
- `COMPLETED`
- `FAILED`

All state changes are persisted to ensure recoverability and correctness.

---

## Asynchronous Processing Approach

The system uses a database-backed queue for asynchronous processing:

- Orders are initially stored with state `RECEIVED`
- Workers fetch orders using row-level locking
- Orders are marked `PROCESSING` before execution
- This ensures safe parallel processing and avoids duplicate work

---

## Tech Stack

- Java
- Vert.x
- PostgreSQL
- Gradle


