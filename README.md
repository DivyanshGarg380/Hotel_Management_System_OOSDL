# Hotel Management System (JavaFX)

## Overview

This project is a desktop-based Hotel Management System developed using Java and JavaFX. It provides an interactive graphical user interface to manage hotel operations such as room handling, customer bookings, waitlists, and intelligent recommendations.

The system is designed to simulate real-world hotel booking scenarios and incorporates advanced concepts such as synchronization, multithreading, and queue-based waitlist management.

---

## Features

### 1. Room Management

* Add new rooms with:

  * Room Number
  * Room Type (Single, Double, Deluxe)
  * Price per day
* View all rooms in a tabular format
* Filter and display only available rooms
* Real-time update of room availability

---

### 2. Customer and Booking Management

* Capture customer details:

  * Name
  * Contact Number
  * Room Number
  * Number of days
* Book rooms using an interactive interface
* Prevent booking of already occupied rooms
* Confirm bookings within a limited time
* Checkout functionality to release rooms

---

### 3. Synchronization and Multithreading

* Thread-safe booking system using `synchronized` methods
* Simulates multiple users trying to book the same room concurrently
* Prevents race conditions during booking
* Demonstrates real-world concurrency handling

---

### 4. Booking Timeout System

* Automatically releases a room if booking is not confirmed within 30 seconds
* Implemented using background threads
* Ensures fair availability of rooms

---

### 5. Waitlist Management (Queue-Based)

* Customers are added to a waitlist if a room is unavailable
* Uses FIFO queue (LinkedList implementation)
* Automatically assigns room to next customer when it becomes available
* Displays waitlist in the GUI

---

### 6. Smart Room Recommendation Engine

* Suggests rooms based on:

  * User budget
  * Duration of stay
* Provides:

  * Cheapest option
  * Best value option
  * Premium option
* Uses scoring logic based on price and room type

---

### 7. Concurrent Booking Simulation

* Simulates multiple threads attempting to book the same room
* Displays success and failure outcomes
* Demonstrates synchronization effectiveness in real-time

---

## Technologies Used

* Java (JDK 21 LTS recommended)
* JavaFX (SDK 21)
* Java Collections Framework (ArrayList, Queue, LinkedList)
* Multithreading and Synchronization
* JavaFX UI Components (TableView, GridPane, VBox, HBox, etc.)

---

## Project Structure

```
src/
 └── com/
     └── hotel/
         └── HotelManagementSystem.java
```

All classes (Room, Customer, Booking, Service, etc.) are implemented within a single file for simplicity.

---

## How to Run

### Prerequisites

* Install JDK 21
* Download JavaFX SDK 21
* Extract JavaFX (e.g., `C:/javafx-sdk-21`)

---

### Compile Command

```
javac --module-path "C:/javafx-sdk-21/lib" --add-modules javafx.controls,javafx.fxml -d out src/com/hotel/HotelManagementSystem.java
```

---

### Run Command

```
java --module-path "C:/javafx-sdk-21/lib" --add-modules javafx.controls,javafx.fxml -cp out com.hotel.HotelManagementSystem
```

---

### Notes

* Replace `C:/javafx-sdk-21` with your actual JavaFX path.
* Ensure folder structure matches the package declaration (`com.hotel`).
* If using VS Code or IntelliJ, configure VM options with the same module path.

---

## Conclusion

This project demonstrates a combination of GUI development, object-oriented design, and core computer science concepts such as synchronization, multithreading, and queue management. It provides a realistic simulation of hotel operations and highlights practical problem-solving in software development.

---
