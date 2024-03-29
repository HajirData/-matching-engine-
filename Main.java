import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class Main {
    private static final String LOG_FILE_PATH = "trades.log";
    private static final int DEFAULT_MAX_POSITION = 100000;
    private static int maxPosition;
    private static int currentPosition = 0;
    private static Map<String, List<Order>> orders = new HashMap<>();

    public static void main(String[] args) {
        if (args.length != 1 || !args[0].startsWith("maximum-position=")) {
            System.err.println("Usage: java Main maximum-position=<maximum-position>");
            System.exit(1);
        }

        try {
            maxPosition = Integer.parseInt(args[0].substring("maximum-position=".length()));
        } catch (NumberFormatException e) {
            System.err.println("Invalid maximum position provided.");
            System.exit(1);
        }

        try (Scanner scanner = new Scanner(System.in);
             FileWriter fileWriter = new FileWriter(LOG_FILE_PATH)) {

            InputReader inputReader = new InputReader(scanner);
            InputParser inputParser = new InputParser();
            OrderProcessor orderProcessor = new OrderProcessor(fileWriter);

            while (inputReader.hasNext()) {
                String input = inputReader.next();
                if (input.equals("FINISH")) {
                    orderProcessor.printTrades();
                    break;
                }
                Order order = inputParser.parseInput(input);
                if (order != null) {
                    orderProcessor.processOrder(order);
                }
            }
        } catch (IOException e) {
            System.err.println("Error occurred while writing to log file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static class InputReader {
        private Scanner scanner;

        public InputReader(Scanner scanner) {
            this.scanner = scanner;
        }

        public boolean hasNext() {
            return scanner.hasNextLine();
        }

        public String next() {
            return scanner.nextLine();
        }
    }

    static class InputParser {
        public Order parseInput(String input) {
            String[] parts = input.split("\t");
            if (parts.length < 6) {
                System.err.println("Invalid input: " + input);
                return null;
            }

            String originatorStr = parts[0];
            Originator originator = Originator.valueOf(originatorStr);
            String messageID = parts[1];
            String sideStr = parts[2];
            Side side = Side.valueOf(sideStr);
            int size = Integer.parseInt(parts[3]);
            double price = Double.parseDouble(parts[4]);
            String productID = parts[5];

            return new Order(originator, messageID, side, size, price, productID);
        }
    }

    static class OrderProcessor {
        private FileWriter fileWriter;

        public OrderProcessor(FileWriter fileWriter) {
            this.fileWriter = fileWriter;
        }

        public void processOrder(Order order) {
            List<Order> orderList = orders.computeIfAbsent(order.getProductID(), k -> new ArrayList<>());
            orderList.add(order);

            if (order.getOriginator() == Originator.DF) {
                if (order.getSide() == Side.SELL) {
                    currentPosition -= order.getSize();
                } else {
                    currentPosition += order.getSize();
                }
            } else {
                if (order.getSide() == Side.BUY && currentPosition >= maxPosition) {
                    System.err.println("Maximum position reached. Cannot accept buy orders.");
                    orderList.remove(order);
                    return;
                }
                matchOrders(order);
            }
        }

        private void matchOrders(Order venueOrder) {
            List<Order> dfOrders = orders.getOrDefault(venueOrder.getProductID(), new ArrayList<>());
            Iterator<Order> iterator = dfOrders.iterator();
            while (iterator.hasNext()) {
                Order dfOrder = iterator.next();
                if ((dfOrder.getSide() == Side.BUY && venueOrder.getSide() == Side.SELL && dfOrder.getPrice() >= venueOrder.getPrice()) ||
                        (dfOrder.getSide() == Side.SELL && venueOrder.getSide() == Side.BUY && dfOrder.getPrice() <= venueOrder.getPrice())) {
                    int quantity = Math.min(Math.abs(dfOrder.getSize()), venueOrder.getSize());
                    Trade trade = new Trade(dfOrder.getMessageID(), venueOrder.getMessageID(), quantity, venueOrder.getPrice(), venueOrder.getProductID());
                    printTrade(trade);
                    dfOrder.setSize(dfOrder.getSize() - quantity);
                    venueOrder.setSize(venueOrder.getSize() - quantity);
                    iterator.remove();
                    if (venueOrder.getSize() == 0) {
                        break;
                    }
                }
            }
        }

        private void printTrade(Trade trade) {
            try {
                fileWriter.write(trade.toString() + "\n");
                fileWriter.flush();
            } catch (IOException e) {
                System.err.println("Error occurred while writing to log file: " + e.getMessage());
                e.printStackTrace();
            }
        }

        public void printTrades() {
            try {
                fileWriter.close();
            } catch (IOException e) {
                System.err.println("Error occurred while closing log file: " + e.getMessage());
                e.printStackTrace();
            }
            for (List<Order> orderList : orders.values()) {
                for (Order order : orderList) {
                    System.out.println(order.toString());
                }
            }
        }
    }

    enum Side {
        BUY, SELL
    }

    enum Originator {
        DF, VE
    }

    static class Order {
        private Originator originator;
        private String messageID;
        private Side side;
        private int size;
        private double price;
        private String productID;

        public Order(Originator originator, String messageID, Side side, int size, double price, String productID) {
            this.originator = originator;
            this.messageID = messageID;
            this.side = side;
            this.size = size;
            this.price = price;
            this.productID = productID;
        }

        public Originator getOriginator() {
            return originator;
        }

        public String getMessageID() {
            return messageID;
        }

        public Side getSide() {
            return side;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public double getPrice() {
            return price;
        }

        public String getProductID() {
            return productID;
        }

        @Override
        public String toString() {
            return originator + "\t" + messageID + "\t" + side + "\t" + size + "\t" + price + "\t" + productID;
        }
    }

    static class Trade {
        private String dfMessageID;
        private String veMessageID;
        private int size;
        private double price;
        private String productID;

        public Trade(String dfMessageID, String veMessageID, int size, double price, String productID) {
            this.dfMessageID = dfMessageID;
            this.veMessageID = veMessageID;
            this.size = size;
            this.price = price;
            this.productID = productID;
        }

        @Override
        public String toString() {
            return "TRADE\t" + dfMessageID + "\t" + veMessageID + "\t" + size + "\t" + price + "\t" + productID;
        }
    }
}