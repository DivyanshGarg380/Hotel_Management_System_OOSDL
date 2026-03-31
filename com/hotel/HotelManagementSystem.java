package com.hotel;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.hotel.HotelManagementSystem.RecommendationEngine;
import com.hotel.HotelManagementSystem.Room;

public class HotelManagementSystem extends Application {

    /* Room Details */
    static class Room {
        int roomNumber;
        String roomType;     
        double pricePerDay;
        boolean available;

        Room(int roomNumber, String roomType, double pricePerDay) {
            this.roomNumber = roomNumber;
            this.roomType = roomType;
            this.pricePerDay = pricePerDay;
            this.available = true;
        }

        public String getStatus() { return available ? "Available" : "Occupied"; }

        @Override public String toString() {
            return "Room " + roomNumber + " (" + roomType + ") $" + pricePerDay + "/day";
        }
    }

    /* Customer Details */
    static class Customer {
        String name, contact;
        int wantedRoom, days;

        Customer(String name, String contact, int wantedRoom, int days) {
            this.name = name; 
            this.contact = contact;
            this.wantedRoom = wantedRoom; 
            this.days = days;
        }
    }

    /* Booking  */
    static class Booking {
        static int nextId = 1001;

        int bookingId, roomNumber, days;
        String customerName, contact, status;

        Booking(int roomNumber, String customerName, String contact, int days) {
            this.bookingId = nextId++;
            this.roomNumber = roomNumber;
            this.customerName = customerName;
            this.contact = contact;
            this.days = days;
            this.status = "Pending";   
        }

        private Booking(int bookingId, int roomNumber, String customerName, String contact, int days, String status) {
            this.bookingId = bookingId;
            this.roomNumber = roomNumber;
            this.customerName = customerName;
            this.contact = contact;
            this.days = days;
            this.status = status;
        }
    }

    /* Waitlist manager -> We are implementing LL here */
    static class WaitlistManager {
        Queue<Customer> queue = new LinkedList<>();

        void add(Customer c) { queue.add(c); }
        Customer removeNext() { return queue.poll(); }   
        boolean hasNext() { return !queue.isEmpty(); }
        List<Customer> getAll() { return new ArrayList<>(queue); }
    }

    /* Recommender System */
    static class RecommendationEngine {

        static List<String> recommend(List<Room> rooms, double budget, int days) {
            List<Room> affordable = new ArrayList<>();
            for (Room r : rooms)
                if (r.available && r.pricePerDay * days <= budget) affordable.add(r);

            if (affordable.isEmpty())
                return List.of("No available rooms fit a budget of $" + budget + " for " + days + " day(s).");

            affordable.sort(Comparator.comparingDouble(r -> r.pricePerDay));

            List<String> out = new ArrayList<>();

            Room cheapest = affordable.get(0);
            out.add("Cheapest  →  Room " + cheapest.roomNumber + " (" + cheapest.roomType + ")"
                    + "  $" + cheapest.pricePerDay + "/night"
                    + "  |  Total: $" + cheapest.pricePerDay * days);

            Room best = affordable.stream()
                    .max(Comparator.comparingDouble(RecommendationEngine::score))
                    .orElse(cheapest);
            if (best.roomNumber != cheapest.roomNumber)
                out.add("Best Value  →  Room " + best.roomNumber + " (" + best.roomType + ")"
                        + "  $" + best.pricePerDay + "/night"
                        + "  |  Total: $" + best.pricePerDay * days);

            Room premium = affordable.get(affordable.size() - 1);
            if (premium.roomNumber != cheapest.roomNumber && premium.roomNumber != best.roomNumber)
                out.add("Premium  →  Room " + premium.roomNumber + " (" + premium.roomType + ")"
                        + "  $" + premium.pricePerDay + "/night"
                        + "  |  Total: $" + premium.pricePerDay * days);

            return out;
        }

        static double score(Room r) {
            double weight = r.roomType.equals("Deluxe") ? 3 :
                            r.roomType.equals("Double") ? 2 : 1;
            return weight / r.pricePerDay * 1000;
        }
    }

   /* Data storing */
    static class DataStore {

        static final Path SAVE_FILE =
            Paths.get(System.getProperty("user.home"), "hotel_data.dat");

        static synchronized void save(HotelService svc) {
            try (BufferedWriter w = Files.newBufferedWriter(SAVE_FILE)) {

                w.write("# Hotel Management System — auto-saved data");
                w.newLine();
                w.write("# Do not edit manually unless you know the format.");
                w.newLine();
                w.newLine();

                w.write("NEXT_ID|" + Booking.nextId);
                w.newLine();
                w.newLine();

                for (Room r : svc.rooms) {
                    w.write("ROOM|" + r.roomNumber + "|" + r.roomType
                            + "|" + r.pricePerDay + "|" + r.available);
                    w.newLine();
                }
                w.newLine();

                for (Booking b : svc.bookings) {
                    w.write("BOOKING|" + b.bookingId + "|" + b.roomNumber
                            + "|" + b.customerName + "|" + b.contact
                            + "|" + b.days + "|" + b.status);
                    w.newLine();
                }
                w.newLine();

                for (Customer c : svc.waitlist.getAll()) {
                    w.write("WAITLIST|" + c.name + "|" + c.contact
                            + "|" + c.wantedRoom + "|" + c.days);
                    w.newLine();
                }

            } catch (IOException e) {
                System.err.println("[DataStore] Save failed: " + e.getMessage());
            }
        }

        static boolean load(HotelService svc) {
            if (!Files.exists(SAVE_FILE)) return false;

            try (BufferedReader r = Files.newBufferedReader(SAVE_FILE)) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    String[] p = line.split("\\|", -1);  

                    switch (p[0]) {

                        case "NEXT_ID" -> {
                            Booking.nextId = Integer.parseInt(p[1]);
                        }

                        case "ROOM" -> {
                            Room room = new Room(
                                Integer.parseInt(p[1]),     
                                p[2],                       
                                Double.parseDouble(p[3])   
                            );
                            room.available = Boolean.parseBoolean(p[4]);
                            svc.rooms.add(room);
                        }

                        case "BOOKING" -> {
                            Booking b = new Booking(
                                Integer.parseInt(p[1]),   
                                Integer.parseInt(p[2]),   
                                p[3],                     
                                p[4],                      
                                Integer.parseInt(p[5]),    
                                p[6]                   
                            );
                            svc.bookings.add(b);
                        }

                        case "WAITLIST" -> {
                            svc.waitlist.add(new Customer(
                                p[1],                      
                                p[2],                       
                                Integer.parseInt(p[3]),    
                                Integer.parseInt(p[4])  
                            ));
                        }

                        default -> System.err.println(
                            "[DataStore] Unknown record type, skipping: " + p[0]);
                    }
                }

                System.out.println("[DataStore] Loaded from " + SAVE_FILE);
                return true;

            } catch (IOException | NumberFormatException e) {
                System.err.println("[DataStore] Load failed: " + e.getMessage()
                    + " — starting fresh.");
                // Clear any partial data that may have been added
                svc.rooms.clear();
                svc.bookings.clear();
                svc.waitlist.queue.clear();
                return false;
            }
        }
    }

    /* Hotel Service */
    static class HotelService {
        Runnable refreshCallback;
        List<Room>      rooms    = new ArrayList<>();
        List<Booking>   bookings = new ArrayList<>();
        WaitlistManager waitlist = new WaitlistManager();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

        HotelService(Runnable refreshCallback) {
            this.refreshCallback = refreshCallback;

            boolean loaded = DataStore.load(this);

            if (!loaded) {
                System.out.println("[DataStore] No save file found — seeding default rooms.");
                rooms.add(new Room(101, "Single",  50.00));
                rooms.add(new Room(102, "Single",  55.00));
                rooms.add(new Room(201, "Double",  90.00));
                rooms.add(new Room(202, "Double",  95.00));
                rooms.add(new Room(301, "Deluxe", 150.00));
                rooms.add(new Room(302, "Deluxe", 175.00));
                DataStore.save(this);
            }
        }

        Room findRoom(int num) {
            for (Room r : rooms) if (r.roomNumber == num) return r;
            return null;
        }

        Booking findBooking(int id) {
            for (Booking b : bookings) if (b.bookingId == id) return b;
            return null;
        }

        synchronized String bookRoom(String name, String contact, int roomNum, int days) {
            Room room = findRoom(roomNum);
            if (room == null) return "Room " + roomNum + " does not exist.";

            if (!room.available) {
                waitlist.add(new Customer(name, contact, roomNum, days));
                DataStore.save(this);  
                return "Room " + roomNum + " is occupied.\n"
                     + name + " added to waitlist (position #" + waitlist.queue.size() + ").";
            }

            room.available = false;
            Booking b = new Booking(roomNum, name, contact, days);
            bookings.add(b);
            DataStore.save(this);       
            startTimeoutTimer(b, room);

            return "Room " + roomNum + " booked for " + name + "!\n"
                 + "Booking ID: #" + b.bookingId + "\n"
                 + "Confirm within 30 seconds to avoid auto-release.";
        }

        synchronized String confirmBooking(int id) {
            Booking b = findBooking(id);
            if (b == null)                    return "Booking #" + id + " not found.";
            if (b.status.equals("Timed Out")) return "Booking #" + id + " already timed out.";
            if (b.status.equals("Confirmed")) return "Booking #" + id + " is already confirmed.";
            b.status = "Confirmed";
            DataStore.save(this);         
            return "Booking #" + id + " confirmed!";
        }

        synchronized String checkoutRoom(int roomNum) {
            Room room = findRoom(roomNum);
            if (room == null)     return "Room " + roomNum + " not found.";
            if (room.available)   return "Room " + roomNum + " is already free.";

            for (Booking b : bookings) {
                if (b.roomNumber == roomNum
                        && !b.status.equals("Checked Out")
                        && !b.status.equals("Timed Out")) {
                    b.status = "Checked Out";
                    break;
                }
            }
            room.available = true;
            String waitlistMsg = assignWaitlistToRoom(roomNum);
            // assignWaitlistToRoom may call bookRoom which saves; save here too for safety
            DataStore.save(this);
            return "Room " + roomNum + " checked out.\n" + waitlistMsg;
        }

        synchronized String assignWaitlistToRoom(int roomNum) {
            if (!waitlist.hasNext()) return "No guests in waitlist.";

            Customer next = null;
            for (Customer c : waitlist.getAll()) {
                if (c.wantedRoom == roomNum) { next = c; break; }
            }
            if (next == null) next = waitlist.removeNext();
            else              waitlist.queue.remove(next);

            String result = bookRoom(next.name, next.contact, roomNum, next.days);
            return "Waitlist auto-assigned: " + next.name + "\n" + result;
        }

        void startTimeoutTimer(Booking booking, Room room) {
            scheduler.schedule(() -> {
                synchronized (HotelService.this) {
                    if (booking.status.equals("Pending")) {
                        booking.status = "Timed Out";
                        room.available = true;
                        DataStore.save(this);  

                        Platform.runLater(() -> {
                            if (refreshCallback != null) refreshCallback.run();
                        });
                    }
                }
            }, 30, TimeUnit.SECONDS);
        }

        void simulateConcurrentBooking(int roomNum, int numUsers, TextArea output) {
            for (int i = 1; i <= numUsers; i++) {
                final int userId = i;
                Thread t = new Thread(() -> {
                    String result = bookRoom("User-" + userId, "555-" + userId, roomNum, 1);
                    Platform.runLater(() ->
                        output.appendText("Thread-" + userId + ": " + result + "\n\n")
                    );
                });
                t.setDaemon(true);
                t.start();
            }
        }

        void shutdown() {
            DataStore.save(this);   
            scheduler.shutdownNow();
        }
    }

    HotelService service = new HotelService(this::refreshAll);

    ObservableList<Room> roomData = FXCollections.observableArrayList();
    ObservableList<Booking> bookingData = FXCollections.observableArrayList();

    TableView<Room> roomTable;
    TableView<Booking> bookingTable;
    TextArea waitlistArea;

    @Override
    public void start(Stage stage) {
        roomData.addAll(service.rooms);
        bookingData.addAll(service.bookings);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            new Tab("Rooms", buildRoomsTab()),
            new Tab("Booking", buildBookingTab()),
            new Tab("Waitlist", buildWaitlistTab()),
            new Tab("Simulation", buildSimulationTab()),
            new Tab("Recommendations", buildRecommendationsTab())
        );

        stage.setTitle("Hotel Management System  —  data: " + DataStore.SAVE_FILE);
        stage.setScene(new Scene(tabs, 1000, 700));
        stage.setOnCloseRequest(e -> service.shutdown());
        stage.show();
    }


    VBox buildRoomsTab() {
        TextField tfNum   = new TextField(); tfNum.setPromptText("e.g. 103");
        ComboBox<String> cbType  = new ComboBox<>(FXCollections.observableArrayList("Single","Double","Deluxe"));
        cbType.setValue("Single");
        TextField tfPrice = new TextField(); tfPrice.setPromptText("e.g. 70.00");
        Label msg = new Label();

        Button btnAdd = new Button("Add Room");
        Button btnAll = new Button("Show All");
        Button btnAvail = new Button("Available Only");

        btnAdd.setOnAction(e -> {
            try {
                int num = Integer.parseInt(tfNum.getText().trim());
                double price = Double.parseDouble(tfPrice.getText().trim());
                if (service.findRoom(num) != null) { msg.setText("Room " + num + " already exists!"); return; }
                Room r = new Room(num, cbType.getValue(), price);
                service.rooms.add(r);
                roomData.add(r);
                DataStore.save(service);  
                msg.setText("Room " + num + " added.");
                tfNum.clear(); tfPrice.clear();
            } catch (NumberFormatException ex) { msg.setText("Enter valid number and price."); }
        });

        btnAll.setOnAction  (e -> { roomData.setAll(service.rooms); msg.setText("Showing all rooms."); });
        btnAvail.setOnAction(e -> {
            roomData.clear();
            for (Room r : service.rooms) if (r.available) roomData.add(r);
            msg.setText("Showing available rooms only.");
        });

        GridPane form = form();
        form.addRow(0, new Label("Room Number:"), tfNum,   new Label("Room Type:"),   cbType);
        form.addRow(1, new Label("Price/Day ($):"), tfPrice);

        roomTable = new TableView<>(roomData);
        roomTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        roomTable.getColumns().addAll(
            col("Room No.", 100, r -> String.valueOf(r.roomNumber)),
            col("Type",     120, r -> r.roomType),
            col("$/Day",    110, r -> String.valueOf(r.pricePerDay)),
            col("Status",   120, r -> r.getStatus())
        );

        VBox root = new VBox(10,
            label("Add New Room"), form,
            row(btnAdd, btnAll, btnAvail, msg),
            sep(),
            label("All Rooms"), roomTable
        );
        root.setPadding(new Insets(12)); VBox.setVgrow(roomTable, Priority.ALWAYS);
        return root;
    }

    VBox buildBookingTab() {
        TextField tfName = field("Customer name");
        TextField tfContact = field("Contact number");
        TextField tfRoom  = field("Room number");
        TextField tfDays = field("Days of stay");
        Label bookMsg = new Label();
        bookMsg.setWrapText(true);
        bookMsg.setMaxWidth(Double.MAX_VALUE);

        Button btnBook = new Button("Book Room");
        btnBook.setOnAction(e -> {
            try {
                String name = tfName.getText().trim(), contact = tfContact.getText().trim();
                int rn = Integer.parseInt(tfRoom.getText().trim());
                int dy = Integer.parseInt(tfDays.getText().trim());
                if (name.isEmpty() || contact.isEmpty()) { bookMsg.setText("Fill all fields."); return; }
                bookMsg.setText(service.bookRoom(name, contact, rn, dy));
                refreshAll(); tfName.clear(); tfContact.clear(); tfRoom.clear(); tfDays.clear();
            } catch (NumberFormatException ex) { bookMsg.setText("Room and days must be integers."); }
        });

        GridPane bForm = form();
        bForm.addRow(0, new Label("Name:"), tfName, new Label("Contact:"), tfContact);
        bForm.addRow(1, new Label("Room No:"), tfRoom, new Label("Days:"), tfDays);

        TextField tfCId  = field("Booking ID"); Label confirmMsg = new Label();
        Button btnConfirm = new Button("Confirm Booking");
        btnConfirm.setOnAction(e -> {
            try {
                confirmMsg.setText(service.confirmBooking(Integer.parseInt(tfCId.getText().trim())));
                refreshAll(); tfCId.clear();
            } catch (NumberFormatException ex) { confirmMsg.setText("Enter a valid Booking ID."); }
        });

        TextField tfCOut = field("Room number"); Label checkMsg = new Label();
        Button btnCheckout = new Button("Checkout Room");
        btnCheckout.setOnAction(e -> {
            try {
                checkMsg.setText(service.checkoutRoom(Integer.parseInt(tfCOut.getText().trim())));
                refreshAll(); tfCOut.clear();
            } catch (NumberFormatException ex) { checkMsg.setText("Enter a valid room number."); }
        });

        bookingTable = new TableView<>(bookingData);
        bookingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        bookingTable.getColumns().addAll(
            col("ID",       80,  b -> "#" + b.bookingId),
            col("Room",     70,  b -> String.valueOf(b.roomNumber)),
            col("Customer", 140, b -> b.customerName),
            col("Contact",  120, b -> b.contact),
            col("Days",     60,  b -> String.valueOf(b.days)),
            col("Status",   110, b -> b.status)
        );

        HBox bookRow = new HBox(10, btnBook, bookMsg);
        HBox.setHgrow(bookMsg, Priority.ALWAYS);

        VBox root = new VBox(10,
            label("Book a Room"), bForm, bookRow,
            sep(),
            label("Confirm Booking (must confirm within 30 seconds!)"),
            row(new Label("Booking ID:"), tfCId, btnConfirm, confirmMsg),
            sep(),
            label("Checkout"),
            row(new Label("Room No:"), tfCOut, btnCheckout, checkMsg),
            sep(),
            label("All Bookings"), bookingTable
        );
        root.setPadding(new Insets(12)); VBox.setVgrow(bookingTable, Priority.ALWAYS);
        return root;
    }

    VBox buildWaitlistTab() {
        waitlistArea = new TextArea();
        waitlistArea.setEditable(false);
        waitlistArea.setPromptText("Waitlist will appear here when rooms are fully booked...");

        Button btnRefresh = new Button("Refresh Waitlist");
        btnRefresh.setOnAction(e -> refreshWaitlist());

        Label info = new Label(
            "When a room is booked and another customer tries to book the same room,\n" +
            "they are added to this FIFO queue (LinkedList).\n" +
            "When a room is freed, the next person in line is auto-assigned the room."
        );

        VBox root = new VBox(12, info, btnRefresh, sep(), label("Current Queue"), waitlistArea);
        root.setPadding(new Insets(15)); VBox.setVgrow(waitlistArea, Priority.ALWAYS);
        return root;
    }

    VBox buildSimulationTab() {
        TextField tfRoom  = field("Room number (e.g. 201)");
        TextField tfUsers = field("Number of threads (2–8)");
        TextArea  output  = new TextArea();
        output.setEditable(false);
        output.setPromptText("Results appear here after running...");

        Button btnRun = new Button("Run Simulation");
        btnRun.setOnAction(e -> {
            try {
                int rn = Integer.parseInt(tfRoom.getText().trim());
                int nu = Integer.parseInt(tfUsers.getText().trim());
                if (nu < 2 || nu > 8)              { output.setText("Users must be 2–8.");        return; }
                if (service.findRoom(rn) == null)   { output.setText("Room " + rn + " not found."); return; }

                output.setText("Launching " + nu + " threads on Room " + rn + "...\n\n");
                service.simulateConcurrentBooking(rn, nu, output);

                Executors.newSingleThreadScheduledExecutor()
                         .schedule(() -> Platform.runLater(this::refreshAll), 2, TimeUnit.SECONDS);

            } catch (NumberFormatException ex) { output.setText("Enter valid integers."); }
        });

        Label explanation = new Label(
            "What this demonstrates:\n\n" +
            "  • N Java threads are created and all call bookRoom() at the same time.\n\n" +
            "  • bookRoom() is marked 'synchronized' — meaning the JVM puts a lock on it.\n\n" +
            "  • Only ONE thread gets the lock and books the room successfully.\n\n" +
            "  • All other threads wait their turn, then get added to the waitlist.\n\n" +
            "  • This prevents a 'race condition' where two people book the same room."
        );
        explanation.setWrapText(true);

        GridPane form = form();
        form.addRow(0, new Label("Room No:"), tfRoom, new Label("Users:"), tfUsers);

        VBox root = new VBox(12, explanation, sep(), form, btnRun, sep(), output);
        root.setPadding(new Insets(15)); VBox.setVgrow(output, Priority.ALWAYS);
        return root;
    }

    VBox buildRecommendationsTab() {
        TextField tfBudget = field("Total budget e.g. 300");
        TextField tfDays = field("Number of days e.g. 3");
        VBox results  = new VBox(10);
        Label msg = new Label();

        Button btnSearch = new Button("Get Recommendations");
        btnSearch.setOnAction(e -> {
            try {
                double budget = Double.parseDouble(tfBudget.getText().trim());
                int    days   = Integer.parseInt(tfDays.getText().trim());
                List<String> recs = RecommendationEngine.recommend(service.rooms, budget, days);
                results.getChildren().clear();
                msg.setText("Showing results for $" + budget + " over " + days + " day(s):");
                for (String rec : recs) {
                    Label lbl = new Label(rec);
                    lbl.setWrapText(true); lbl.setMaxWidth(Double.MAX_VALUE);
                    lbl.setPadding(new Insets(8));
                    lbl.setStyle("-fx-background-color:#dff0d8; -fx-background-radius:5; -fx-font-size:14;");
                    results.getChildren().add(lbl);
                }
            } catch (NumberFormatException ex) { msg.setText("Enter valid numbers."); }
        });

        Label info = new Label(
            "Enter your total budget and how many days you want to stay.\n" +
            "The engine will suggest the cheapest, best-value, and premium room options\n" +
            "from the rooms that are currently available."
        );
        info.setWrapText(true);

        GridPane form = form();
        form.addRow(0, new Label("Total Budget ($):"), tfBudget, new Label("Days of Stay:"), tfDays);

        VBox root = new VBox(12, info, sep(), form, btnSearch, msg, new ScrollPane(results) {{ setFitToWidth(true); }});
        root.setPadding(new Insets(15)); return root;
    }

    <T> TableColumn<T, String> col(String title, int width, Function<T, String> getter) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(data -> new SimpleStringProperty(getter.apply(data.getValue())));
        return c;
    }

    GridPane form() {
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(10));
        return g;
    }

    HBox row(javafx.scene.Node... nodes) {
        HBox h = new HBox(10, nodes);
        h.setAlignment(Pos.CENTER_LEFT); h.setPadding(new Insets(4, 0, 4, 0));
        return h;
    }

    TextField field(String prompt) { TextField f = new TextField(); f.setPromptText(prompt); return f; }
    Label label(String t) { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;"); return l; }
    Separator sep() { return new Separator(); }

    void refreshAll() {
        roomData.setAll(service.rooms);
        bookingData.setAll(service.bookings);
        refreshWaitlist();
    }

    void refreshWaitlist() {
        List<Customer> all = service.waitlist.getAll();
        if (all.isEmpty()) { waitlistArea.setText("Waitlist is empty."); return; }
        StringBuilder sb = new StringBuilder("Total in queue: " + all.size() + "\n\n");
        int pos = 1;
        for (Customer c : all)
            sb.append("  #").append(pos++).append("  ").append(c.name)
              .append("  |  ").append(c.contact)
              .append("  |  Wants Room: ").append(c.wantedRoom)
              .append("  |  Days: ").append(c.days).append("\n");
        waitlistArea.setText(sb.toString());
    }

    public static void main(String[] args) { launch(args); }
}