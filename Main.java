import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * Console-based Offline Food Ordering System coursework demonstration.
 * Orders are simulated locally and saved as CSV in the current working directory.
 */
public class Main {
    private static final int DELIVERY_FEE_KES = 150;
    private static final int MAX_QUANTITY = 99;
    private static final List<MenuItem> MENU = List.of(
            new MenuItem(1, "Main Meals", "Nyama Choma Platter", 850),
            new MenuItem(2, "Main Meals", "Chicken Pilau", 420),
            new MenuItem(3, "Main Meals", "Beef Burger", 550),
            new MenuItem(4, "Main Meals", "Chicken Wrap", 380),
            new MenuItem(5, "Vegetarian", "Vegetable Curry", 350),
            new MenuItem(6, "Sides", "Chapati (2 pieces)", 100),
            new MenuItem(7, "Sides", "Chips Masala", 250),
            new MenuItem(8, "Drinks", "Fresh Juice", 180),
            new MenuItem(9, "Drinks", "Soda", 100),
            new MenuItem(10, "Drinks", "Bottled Water", 80)
    );

    private final Scanner input = new Scanner(System.in);
    private final Map<Integer, Integer> cart = new LinkedHashMap<>();
    private final OrderRepository repository;
    private boolean running = true;

    private Main(Path orderFile) {
        repository = new OrderRepository(orderFile);
    }

    public static void main(String[] args) {
        new Main(Path.of("orders.csv")).run();
    }

    private void run() {
        System.out.println("===============================================");
        System.out.println("       OFFLINE FOOD ORDERING SYSTEM");
        System.out.println("       Local offline simulation | KES");
        System.out.println("===============================================");
        if (repository.loadWarning != null) {
            System.out.println("Storage notice: " + repository.loadWarning);
        }
        while (running) {
            showMainMenu();
            int choice = readInt("Select an option: ", 0, 7);
            if (!running) break;
            switch (choice) {
                case 1 -> showMenu();
                case 2 -> addToCart();
                case 3 -> viewCart();
                case 4 -> removeFromCart();
                case 5 -> placeOrder();
                case 6 -> trackOrder();
                case 7 -> updateOrderStatus();
                case 0 -> running = false;
                default -> System.out.println("Please select a listed option.");
            }
        }
        System.out.println("Thank you. This local offline ordering simulation has closed.");
    }

    private void showMainMenu() {
        System.out.println("\nMAIN MENU");
        System.out.println("1. Browse food menu");
        System.out.println("2. Add an item to cart");
        System.out.println("3. View cart");
        System.out.println("4. Remove an item from cart");
        System.out.println("5. Create and confirm order");
        System.out.println("6. Check order status");
        System.out.println("7. Staff demo: advance order status");
        System.out.println("0. Exit");
    }

    private void showMenu() {
        System.out.println("\nFOOD MENU");
        System.out.printf("%-4s %-16s %-30s %12s%n", "ID", "CATEGORY", "ITEM", "PRICE");
        System.out.println("------------------------------------------------------------------");
        for (MenuItem item : MENU) {
            System.out.printf(Locale.ROOT, "%-4d %-16s %-30s KES %,d%n",
                    item.id(), item.category(), item.name(), item.priceKes());
        }
        System.out.println("Prices are sample coursework data and can be changed in Main.java.");
    }

    private void addToCart() {
        showMenu();
        int id = readInt("Enter menu item ID (0 to cancel): ", 0, 10);
        if (!running || id == 0) return;
        if (findMenuItem(id) == null) {
            System.out.println("That menu item is not available.");
            return;
        }
        int quantity = readInt("Quantity (1-" + MAX_QUANTITY + "): ", 1, MAX_QUANTITY);
        if (!running) return;
        int existing = cart.getOrDefault(id, 0);
        if (existing + quantity > MAX_QUANTITY) {
            System.out.println("The cart limit is " + MAX_QUANTITY + " of one item.");
            return;
        }
        cart.put(id, existing + quantity);
        System.out.println("Added " + quantity + " x " + findMenuItem(id).name() + " to the cart.");
    }

    private void viewCart() {
        if (cart.isEmpty()) {
            System.out.println("Your cart is empty.");
            return;
        }
        System.out.println("\nYOUR CART");
        printCartLines(cart);
        System.out.println("Subtotal: KES " + money(subtotal(cart)));
        System.out.println("Delivery fee is added at checkout for delivery orders.");
    }

    private void removeFromCart() {
        if (cart.isEmpty()) {
            System.out.println("Your cart is empty.");
            return;
        }
        viewCart();
        int id = readInt("Enter item ID to remove (0 to cancel): ", 0, 10);
        if (!running || id == 0) return;
        if (cart.remove(id) == null) {
            System.out.println("That item is not in your cart.");
        } else {
            System.out.println("Item removed.");
        }
    }

    private void placeOrder() {
        if (cart.isEmpty()) {
            System.out.println("Add at least one menu item before checkout.");
            return;
        }
        System.out.println("\nCUSTOMER DETAILS");
        String name = readNonBlank("Customer name: ", 80);
        if (!running || name == null) return;
        String phone = readPhone();
        if (!running || phone == null) return;
        System.out.println("Fulfilment: 1. Pickup  2. Delivery");
        int fulfilmentChoice = readInt("Choose fulfilment: ", 1, 2);
        if (!running) return;
        String fulfilment = fulfilmentChoice == 1 ? "PICKUP" : "DELIVERY";
        String address = "";
        if (fulfilmentChoice == 2) {
            address = readNonBlank("Delivery address: ", 200);
            if (!running || address == null) return;
        }

        int sub = subtotal(cart);
        int deliveryFee = fulfilmentChoice == 2 ? DELIVERY_FEE_KES : 0;
        System.out.println("\nORDER REVIEW");
        printCartLines(cart);
        System.out.println("Customer: " + name);
        System.out.println("Phone: " + phone);
        System.out.println("Fulfilment: " + fulfilment);
        if (!address.isBlank()) System.out.println("Address: " + address);
        System.out.println("Subtotal: KES " + money(sub));
        System.out.println("Delivery fee: KES " + money(deliveryFee));
        System.out.println("TOTAL: KES " + money(sub + deliveryFee));
        System.out.println("Payment is not collected by this offline simulation.");
        if (!readYesNo("Confirm this order? (Y/N): ")) {
            System.out.println("Order not saved. Your cart is still available to edit.");
            return;
        }
        if (!running) return;

        String id = "FO" + System.currentTimeMillis();
        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        Order order = new Order(id, createdAt, name, phone, fulfilment, address,
                new LinkedHashMap<>(cart), sub, deliveryFee, sub + deliveryFee, "CONFIRMED");
        repository.orders.add(order);
        try {
            repository.saveAll();
            cart.clear();
            System.out.println("\nOrder confirmed. Your order number is " + id + ".");
            System.out.println("Current status: CONFIRMED");
            System.out.println("Keep this number to check the simulated order status.");
        } catch (IOException ex) {
            repository.orders.remove(order);
            System.out.println("The order could not be saved: " + ex.getMessage());
            System.out.println("Your cart has been kept. Please try again later.");
        }
    }

    private void trackOrder() {
        String id = readNonBlank("Enter order number: ", 40);
        if (!running || id == null) return;
        Order order = repository.findById(id.trim().toUpperCase(Locale.ROOT));
        if (order == null) {
            System.out.println("No saved order matches that number.");
            return;
        }
        System.out.println("\nORDER STATUS");
        printOrder(order, false);
    }

    private void updateOrderStatus() {
        if (repository.orders.isEmpty()) {
            System.out.println("There are no saved orders to update.");
            return;
        }
        System.out.println("\nSTAFF STATUS DEMO");
        for (Order order : repository.orders) {
            System.out.println(order.id + " | " + order.customerName + " | " + order.status);
        }
        String id = readNonBlank("Enter order number to update (0 to cancel): ", 40);
        if (!running || id == null || id.equals("0")) return;
        Order order = repository.findById(id.trim().toUpperCase(Locale.ROOT));
        if (order == null) {
            System.out.println("No saved order matches that number.");
            return;
        }

        List<String> nextStatuses = nextStatuses(order.status);
        if (nextStatuses.isEmpty()) {
            System.out.println("This order is already in a final status: " + order.status + ".");
            return;
        }
        System.out.println("Current status: " + order.status);
        for (int i = 0; i < nextStatuses.size(); i++) {
            System.out.println((i + 1) + ". " + nextStatuses.get(i));
        }
        int option = readInt("Choose next status (0 to cancel): ", 0, nextStatuses.size());
        if (!running || option == 0) return;
        String oldStatus = order.status;
        order.status = nextStatuses.get(option - 1);
        try {
            repository.saveAll();
            System.out.println("Order " + order.id + " status updated to " + order.status + ".");
        } catch (IOException ex) {
            order.status = oldStatus;
            System.out.println("Status could not be saved: " + ex.getMessage());
        }
    }

    private static List<String> nextStatuses(String current) {
        return switch (current) {
            case "CONFIRMED" -> List.of("PREPARING", "CANCELLED");
            case "PREPARING" -> List.of("READY", "CANCELLED");
            case "READY" -> List.of("COMPLETED");
            default -> List.of();
        };
    }

    private void printOrder(Order order, boolean includeContact) {
        System.out.println("Order number: " + order.id);
        System.out.println("Created: " + order.createdAt);
        System.out.println("Customer: " + order.customerName);
        if (includeContact) System.out.println("Phone: " + order.phone);
        System.out.println("Fulfilment: " + order.fulfilment);
        if (!order.address.isBlank()) System.out.println("Address: " + order.address);
        printCartLines(order.items);
        System.out.println("Subtotal: KES " + money(order.subtotalKes));
        System.out.println("Delivery fee: KES " + money(order.deliveryFeeKes));
        System.out.println("Total: KES " + money(order.totalKes));
        System.out.println("Status: " + order.status);
        System.out.println("Payment: not processed (simulation only)");
    }

    private void printCartLines(Map<Integer, Integer> items) {
        System.out.printf("%-30s %8s %12s %12s%n", "ITEM", "QTY", "UNIT", "LINE TOTAL");
        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            MenuItem item = findMenuItem(entry.getKey());
            if (item == null) continue;
            int lineTotal = item.priceKes() * entry.getValue();
            System.out.printf(Locale.ROOT, "%-30s %8d KES %,7d KES %,7d%n",
                    item.name(), entry.getValue(), item.priceKes(), lineTotal);
        }
    }

    private String readPhone() {
        while (running) {
            String value = readLine("Phone number (7-15 digits; spaces and + allowed): ");
            if (value == null) return null;
            value = value.trim();
            long digits = value.chars().filter(Character::isDigit).count();
            if (value.matches("[0-9+() -]{7,20}") && digits >= 7 && digits <= 15) return value;
            System.out.println("Enter a phone number containing 7 to 15 digits.");
        }
        return null;
    }

    private String readNonBlank(String prompt, int maxLength) {
        while (running) {
            String value = readLine(prompt);
            if (value == null) return null;
            value = value.trim();
            if (!value.isEmpty() && value.length() <= maxLength) return value;
            System.out.println("Enter a value between 1 and " + maxLength + " characters.");
        }
        return null;
    }

    private int readInt(String prompt, int min, int max) {
        while (running) {
            String value = readLine(prompt);
            if (value == null) return min;
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed >= min && parsed <= max) return parsed;
            } catch (NumberFormatException ignored) {
                // The user receives the same clear prompt for text and out-of-range values.
            }
            System.out.println("Enter a whole number from " + min + " to " + max + ".");
        }
        return min;
    }

    private boolean readYesNo(String prompt) {
        while (running) {
            String answer = readLine(prompt);
            if (answer == null) return false;
            if (answer.trim().equalsIgnoreCase("y") || answer.trim().equalsIgnoreCase("yes")) return true;
            if (answer.trim().equalsIgnoreCase("n") || answer.trim().equalsIgnoreCase("no")) return false;
            System.out.println("Please enter Y or N.");
        }
        return false;
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        if (!input.hasNextLine()) {
            running = false;
            return null;
        }
        return input.nextLine();
    }

    private static int subtotal(Map<Integer, Integer> items) {
        return items.entrySet().stream()
                .mapToInt(entry -> findMenuItem(entry.getKey()).priceKes() * entry.getValue())
                .sum();
    }

    private static MenuItem findMenuItem(int id) {
        return MENU.stream().filter(item -> item.id() == id).findFirst().orElse(null);
    }

    private static String money(int amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }

    private record MenuItem(int id, String category, String name, int priceKes) { }

    private static final class Order {
        private final String id;
        private final String createdAt;
        private final String customerName;
        private final String phone;
        private final String fulfilment;
        private final String address;
        private final Map<Integer, Integer> items;
        private final int subtotalKes;
        private final int deliveryFeeKes;
        private final int totalKes;
        private String status;

        private Order(String id, String createdAt, String customerName, String phone,
                      String fulfilment, String address, Map<Integer, Integer> items,
                      int subtotalKes, int deliveryFeeKes, int totalKes, String status) {
            this.id = id;
            this.createdAt = createdAt;
            this.customerName = customerName;
            this.phone = phone;
            this.fulfilment = fulfilment;
            this.address = address;
            this.items = new LinkedHashMap<>(items);
            this.subtotalKes = subtotalKes;
            this.deliveryFeeKes = deliveryFeeKes;
            this.totalKes = totalKes;
            this.status = status;
        }

        private String toCsv() {
            StringBuilder itemData = new StringBuilder();
            for (Map.Entry<Integer, Integer> item : items.entrySet()) {
                if (!itemData.isEmpty()) itemData.append(';');
                itemData.append(item.getKey()).append(':').append(item.getValue());
            }
            return csv(id) + "," + csv(createdAt) + "," + csv(customerName) + ","
                    + csv(phone) + "," + csv(fulfilment) + "," + csv(address) + ","
                    + csv(itemData.toString()) + "," + subtotalKes + "," + deliveryFeeKes
                    + "," + totalKes + "," + csv(status);
        }

        private static Order fromCsv(List<String> values) {
            if (values.size() != 11) throw new IllegalArgumentException("Expected 11 CSV fields");
            Map<Integer, Integer> itemMap = new LinkedHashMap<>();
            if (values.get(6).isBlank()) throw new IllegalArgumentException("Missing order items");
            for (String entry : values.get(6).split(";")) {
                String[] pair = entry.split(":", -1);
                if (pair.length != 2) throw new IllegalArgumentException("Invalid order item");
                int menuId = Integer.parseInt(pair[0]);
                int quantity = Integer.parseInt(pair[1]);
                if (findMenuItem(menuId) == null || quantity < 1 || quantity > MAX_QUANTITY) {
                    throw new IllegalArgumentException("Invalid saved menu item or quantity");
                }
                itemMap.put(menuId, quantity);
            }
            int subtotal = Integer.parseInt(values.get(7));
            int deliveryFee = Integer.parseInt(values.get(8));
            int total = Integer.parseInt(values.get(9));
            if (!List.of("PICKUP", "DELIVERY").contains(values.get(4))
                    || !List.of("CONFIRMED", "PREPARING", "READY", "COMPLETED", "CANCELLED")
                    .contains(values.get(10))
                    || subtotal < 0 || deliveryFee < 0 || total != subtotal + deliveryFee) {
                throw new IllegalArgumentException("Invalid saved order values");
            }
            return new Order(values.get(0), values.get(1), values.get(2), values.get(3),
                    values.get(4), values.get(5), itemMap, subtotal, deliveryFee, total, values.get(10));
        }
    }

    private static final class OrderRepository {
        private static final String HEADER = "order_id,created_at,customer_name,phone,fulfilment,address,items,subtotal_kes,delivery_fee_kes,total_kes,status";
        private final Path file;
        private final List<Order> orders = new ArrayList<>();
        private String loadWarning;

        private OrderRepository(Path file) {
            this.file = file;
            load();
        }

        private void load() {
            if (!Files.exists(file)) return;
            int lineNumber = 0;
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (lineNumber == 1 && line.startsWith("order_id,")) continue;
                    if (line.isBlank()) continue;
                    try {
                        orders.add(Order.fromCsv(parseCsv(line)));
                    } catch (RuntimeException ex) {
                        loadWarning = "Skipped unreadable saved-order row " + lineNumber + ".";
                    }
                }
            } catch (IOException ex) {
                loadWarning = "Could not read " + file + ": " + ex.getMessage();
            }
        }

        private Order findById(String id) {
            return orders.stream().filter(order -> order.id.equalsIgnoreCase(id)).findFirst().orElse(null);
        }

        private void saveAll() throws IOException {
            StringBuilder content = new StringBuilder(HEADER).append(System.lineSeparator());
            for (Order order : orders) content.append(order.toCsv()).append(System.lineSeparator());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, content.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static List<String> parseCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"' && current.isEmpty()) {
                quoted = true;
            } else if (ch == ',') {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unclosed CSV quote");
        values.add(current.toString());
        return values;
    }
}
