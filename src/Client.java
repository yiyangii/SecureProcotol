import java.io.IOException;
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

        // Rest of the code remains unchanged...
        // ...

        ChatClient client = new ChatClient(host, port, username);
        // ...

        // Create a Scanner object to handle user input
        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter your password:");
        String password = scanner.nextLine();


        client.startConnection();

        // Connect using the username and password
        client.connect(username, password);

        // Start a new thread for receiving messages
        new Thread(() -> {
            while (true) {
                try {
                    Message msg = client.receiveMessage();
                    System.out.println(msg.getPayload());
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();

        long lastInputTime = System.currentTimeMillis();  // Timestamp of the last input
        boolean isHeartbeatOn = false;  // Flag indicating if heartbeat is currently on

        while (true) {
            // If more than 10 seconds have passed since the last input, stop the heartbeat
            if (isHeartbeatOn && System.currentTimeMillis() - lastInputTime > 10_000) {
                client.stopHeartbeat();
                isHeartbeatOn = false;
            }

            System.out.println("Enter your command:");
            String command = scanner.nextLine();
            client.userInputReceived();

            // Update the last input time
            lastInputTime = System.currentTimeMillis();

            // Start the heartbeat if it's currently off
            if (!isHeartbeatOn) {
                new Thread(client::startHeartbeat).start();
                isHeartbeatOn = true;
            }

            // If the user wants to disconnect
            if (command.equalsIgnoreCase("disconnect")) {
                client.disconnect();
                break;
            }
            // If the user wants to send a message
            else if (command.startsWith("send ")) {
                System.out.println("Enter the type of the message (private/group):");
                String messageType = scanner.nextLine();

                System.out.println("Enter the receiver of the message:");
                String receiver = scanner.nextLine();

                System.out.println("Enter your message:");
                String message = scanner.nextLine();

                if (!messageType.equals("private") && !messageType.equals("group")) {
                    System.out.println("Invalid message type. Expected: private or group");
                } else {
                    if (messageType.equals("private")) {
                        client.sendMessageToUser(receiver, message);
                    } else if (messageType.equals("group")) {
                        client.sendMessageToGroup(receiver, message);
                    }
                }
            }
            else if (command.equalsIgnoreCase("createGroup")) {
                System.out.println("Enter the name of the group:");
                String groupName = scanner.nextLine();
                client.createGroup(groupName);
            }
            else if (command.equalsIgnoreCase("addMember")) {
                System.out.println("Enter the group name:");
                String groupName = scanner.nextLine();

                System.out.println("Enter the username of the member to add:");
                String memberName = scanner.nextLine();

                client.addMemberToGroup(groupName, memberName);
            }
            else if (command.equalsIgnoreCase("removeMember")) {
                System.out.println("Enter the group name:");
                String groupName = scanner.nextLine();

                System.out.println("Enter the username of the member to remove:");
                String memberName = scanner.nextLine();

                client.removeMemberFromGroup(groupName, memberName);
            }
            else if (command.equalsIgnoreCase("exit")) {
                break;
            }
            // Unrecognized command
            else {
                System.out.println("Unrecognized command: " + command);
            }
        }

        // Close the connection when done
        client.stopConnection();

        // Close the scanner
        scanner.close();
    }
}
