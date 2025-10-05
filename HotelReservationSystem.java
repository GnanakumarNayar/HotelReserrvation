package hotelReservation;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Simple Hotel Reservation System (single-file Java implementation)
 * Features:
 * - Room categorization (STANDARD, DELUXE, SUITE)
 * - Search available rooms by date range and category
 * - Make and cancel reservations
 * - Payment simulation (random success/failure)
 * - Persist data to disk using Java serialization (hotel.db)
 *
 * To compile:
 *   javac HotelReservationSystem.java
 * To run:
 *   java HotelReservationSystem
 *
 * Notes:
 * - This is a simple demo suitable for learning OOP + File I/O.
 * - For production you'd use a real DB, concurrency control, input validation,
 *   and better error handling.
 */

enum RoomType {
    STANDARD, DELUXE, SUITE
}

class Room implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int id;
    private final RoomType type;
    private final double pricePerNight;

    public Room(int id, RoomType type, double pricePerNight) {
        this.id = id;
        this.type = type;
        this.pricePerNight = pricePerNight;
    }

    public int getId() { return id; }
    public RoomType getType() { return type; }
    public double getPricePerNight() { return pricePerNight; }

    @Override
    public String toString() {
        return String.format("Room{id=%d, type=%s, price=%.2f}", id, type, pricePerNight);
    }
}

class Booking implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String bookingId;
    private final int roomId;
    private final String guestName;
    private final LocalDate from;
    private final LocalDate to;
    private final double amountPaid;

    public Booking(String bookingId, int roomId, String guestName, LocalDate from, LocalDate to, double amountPaid) {
        this.bookingId = bookingId;
        this.roomId = roomId;
        this.guestName = guestName;
        this.from = from;
        this.to = to;
        this.amountPaid = amountPaid;
    }

    public String getBookingId() { return bookingId; }
    public int getRoomId() { return roomId; }
    public String getGuestName() { return guestName; }
    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public double getAmountPaid() { return amountPaid; }

    @Override
    public String toString() {
        return String.format("Booking{id=%s, room=%d, guest='%s', from=%s, to=%s, paid=%.2f}",
                bookingId, roomId, guestName, from, to, amountPaid);
    }
}

class PaymentSimulator {
    private static final Random rnd = new Random();

    // Simulates payment. 90% success rate
    public static boolean processPayment(double amount) {
        System.out.printf("Simulating payment of %.2f...\n", amount);
        try { Thread.sleep(500); } catch (InterruptedException e) { /* ignore */ }
        boolean success = rnd.nextDouble() < 0.90;
        System.out.println(success ? "Payment succeeded." : "Payment failed.");
        return success;
    }
}

class Hotel implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<Room> rooms = new ArrayList<>();
    private List<Booking> bookings = new ArrayList<>();

    public Hotel() {}

    public void addRoom(Room r) { rooms.add(r); }

    public List<Room> getRooms() { return Collections.unmodifiableList(rooms); }

    public List<Booking> getBookings() { return Collections.unmodifiableList(bookings); }

    // Check if room is available for the given date range (from inclusive, to exclusive)
    public boolean isRoomAvailable(int roomId, LocalDate from, LocalDate to) {
        for (Booking b : bookings) {
            if (b.getRoomId() != roomId) continue;
            // Overlap check: if requested.from < booking.to && booking.from < requested.to => overlap
            if (from.isBefore(b.getTo()) && b.getFrom().isBefore(to)) {
                return false;
            }
        }
        return true;
    }

    public List<Room> searchAvailable(RoomType type, LocalDate from, LocalDate to) {
        List<Room> result = new ArrayList<>();
        for (Room r : rooms) {
            if (type != null && r.getType() != type) continue;
            if (isRoomAvailable(r.getId(), from, to)) result.add(r);
        }
        return result;
    }

    // Make a reservation if room is available and payment succeeds.
    public Optional<Booking> makeReservation(int roomId, String guestName, LocalDate from, LocalDate to) {
        Optional<Room> roomOpt = rooms.stream().filter(r -> r.getId() == roomId).findFirst();
        if (!roomOpt.isPresent()) return Optional.empty();
        Room room = roomOpt.get();
        if (!isRoomAvailable(roomId, from, to)) return Optional.empty();
        long nights = from.until(to).getDays();
        if (nights <= 0) return Optional.empty();
        double amount = nights * room.getPricePerNight();
        boolean paid = PaymentSimulator.processPayment(amount);
        if (!paid) return Optional.empty();
        String bookingId = UUID.randomUUID().toString();
        Booking booking = new Booking(bookingId, roomId, guestName, from, to, amount);
        bookings.add(booking);
        return Optional.of(booking);
    }

    public boolean cancelBooking(String bookingId) {
        Iterator<Booking> it = bookings.iterator();
        while (it.hasNext()) {
            Booking b = it.next();
            if (b.getBookingId().equals(bookingId)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public Optional<Booking> findBooking(String bookingId) {
        return bookings.stream().filter(b -> b.getBookingId().equals(bookingId)).findFirst();
    }

}

class Database {
    private final File file;

    public Database(String path) { this.file = new File(path); }

    public void save(Hotel hotel) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(hotel);
            System.out.println("Saved hotel state to " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save database: " + e.getMessage());
        }
    }

    public Hotel loadOrCreate() {
        if (!file.exists()) {
            Hotel h = new Hotel();
            seedSampleData(h);
            save(h);
            return h;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object o = ois.readObject();
            if (o instanceof Hotel) {
                System.out.println("Loaded hotel state from " + file.getAbsolutePath());
                return (Hotel) o;
            }
        } catch (Exception e) {
            System.err.println("Failed to load database (will create new): " + e.getMessage());
        }
        Hotel h = new Hotel();
        seedSampleData(h);
        save(h);
        return h;
    }

    private void seedSampleData(Hotel h) {
        // Add sample rooms
        h.addRoom(new Room(101, RoomType.STANDARD, 2000));
        h.addRoom(new Room(102, RoomType.STANDARD, 2000));
        h.addRoom(new Room(201, RoomType.DELUXE, 3500));
        h.addRoom(new Room(202, RoomType.DELUXE, 3500));
        h.addRoom(new Room(301, RoomType.SUITE, 6000));
        System.out.println("Seeded sample rooms.");
    }
}

public class HotelReservationSystem {
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);
        Database db = new Database("hotel.db");
        Hotel hotel = db.loadOrCreate();

        boolean running = true;
        while (running) {
            System.out.println("\n--- Hotel Reservation System ---");
            System.out.println("1) List rooms");
            System.out.println("2) Search available rooms");
            System.out.println("3) Make reservation");
            System.out.println("4) Cancel reservation");
            System.out.println("5) View booking details");
            System.out.println("6) List bookings");
            System.out.println("7) Save & Exit");
            System.out.print("Choose: ");
            String choice = in.nextLine().trim();
            try {
                switch (choice) {
                    case "1":
                        listRooms(hotel);
                        break;
                    case "2":
                        searchRooms(hotel, in);
                        break;
                    case "3":
                        makeReservation(hotel, in);
                        break;
                    case "4":
                        cancelReservation(hotel, in);
                        break;
                    case "5":
                        viewBooking(hotel, in);
                        break;
                    case "6":
                        listBookings(hotel);
                        break;
                    case "7":
                        db.save(hotel);
                        running = false;
                        break;
                    default:
                        System.out.println("Invalid option.");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
        in.close();
        System.out.println("Goodbye!");
    }

    private static void listRooms(Hotel hotel) {
        System.out.println("Rooms:");
        for (Room r : hotel.getRooms()) System.out.println("  " + r);
    }

    private static void listBookings(Hotel hotel) {
        System.out.println("Bookings:");
        for (Booking b : hotel.getBookings()) System.out.println("  " + b);
    }

    private static void searchRooms(Hotel hotel, Scanner in) {
        RoomType type = readRoomTypeOptional(in);
        LocalDate[] range = readDateRange(in);
        List<Room> avail = hotel.searchAvailable(type, range[0], range[1]);
        if (avail.isEmpty()) System.out.println("No rooms available for that range.");
        else {
            System.out.println("Available rooms:");
            for (Room r : avail) System.out.println("  " + r);
        }
    }

    private static void makeReservation(Hotel hotel, Scanner in) {
        System.out.print("Guest name: ");
        String guest = in.nextLine().trim();
        LocalDate[] range = readDateRange(in);
        System.out.print("Enter room id to book: ");
        int roomId = Integer.parseInt(in.nextLine().trim());
        Optional<Booking> b = hotel.makeReservation(roomId, guest, range[0], range[1]);
        if (b.isPresent()) {
            System.out.println("Booking successful: " + b.get());
        } else {
            System.out.println("Booking failed (room may be unavailable or payment failed).");
        }
    }

    private static void cancelReservation(Hotel hotel, Scanner in) {
        System.out.print("Booking id to cancel: ");
        String id = in.nextLine().trim();
        boolean ok = hotel.cancelBooking(id);
        System.out.println(ok ? "Booking cancelled." : "No such booking.");
    }

    private static void viewBooking(Hotel hotel, Scanner in) {
        System.out.print("Booking id: ");
        String id = in.nextLine().trim();
        Optional<Booking> b = hotel.findBooking(id);
        if (b.isPresent()) System.out.println(b.get());
        else System.out.println("No such booking.");
    }

    private static RoomType readRoomTypeOptional(Scanner in) {
        System.out.print("Filter by room type? (standard/deluxe/suite/none): ");
        String t = in.nextLine().trim().toLowerCase();
        switch (t) {
            case "standard": return RoomType.STANDARD;
            case "deluxe": return RoomType.DELUXE;
            case "suite": return RoomType.SUITE;
            default: return null;
        }
    }

    private static LocalDate[] readDateRange(Scanner in) {
        while (true) {
            try {
                System.out.print("From (yyyy-MM-dd): ");
                String f = in.nextLine().trim();
                System.out.print("To (yyyy-MM-dd) (exclusive): ");
                String t = in.nextLine().trim();
                LocalDate from = LocalDate.parse(f, DF);
                LocalDate to = LocalDate.parse(t, DF);
                if (!from.isBefore(to)) {
                    System.out.println("'From' must be before 'To'. Try again.");
                    continue;
                }
                return new LocalDate[] { from, to };
            } catch (Exception e) {
                System.out.println("Invalid date format, please use yyyy-MM-dd.");
            }
        }
    }
}
