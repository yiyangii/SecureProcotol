import java.io.IOException;

public class Server {
    public static void main(String[] args) throws IOException {
        ChatServer server = new ChatServer(12345);
        server.start();
    }

}
