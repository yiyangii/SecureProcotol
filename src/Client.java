import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws Exception {
        // Parse command line arguments
        if (args.length < 3) {
            System.out.println("Usage: java Client <host> <port> <username>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String username = args[2];

        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter your password:");
        String password = scanner.nextLine();

        ChatClient client = new ChatClient(host, port, username, password);
        client.startConnection();

        // Rest of the code...

        // Close the scanner
        scanner.close();
    }
}