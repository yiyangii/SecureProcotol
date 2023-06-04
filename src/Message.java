import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Message {
    public static final int CONNECT_REQUEST = 0;
    public static final int DISCONNECT = 1;
    public static final int SEND_MESSAGE = 2;
    public static final int CREATE_GROUP = 3;
    public static final int AUTH_SUCCESS = 4;
    public static final int AUTH_FAILURE = 5;
    public static final int ADD_MEMBER = 6;
    public static final int REMOVE_MEMBER = 7;

    public static final int HEARTBEAT = 8;

    public static final int AUTH_REQUEST = 9;
    public static final int AUTH_ACK = 10;
    public static final int AUTH_NACK = 11;

    public static final int CHANGE_STATE = 12;
    public static final int RECEIVE_MESSAGE = 13;
    public static final int GROUP_ACK = 15;
    public static final int GROUP_NACK = 16;

    public static final int CONNECT_ACK = 17;

    public static final int STATE_WAITING_FOR_CONNECTION = 18;
    public static final int STATE_AUTHENTICATING = 19;
    public static final int STATE_AUTHENTICATED = 20;
    public static final int STATE_IDLE = 21;
    public static final int STATE_SENDING_MESSAGE = 22;
    public static final int STATE_CREATING_GROUP = 23;
    public static final int STATE_DISCONNECTED = 24;







    private static final String AES = "AES";
    private static final byte[] keyValue =
            new byte[]{'T', 'h', 'i', 's', 'I', 's', 'A', '2', '5', '6', 'B', 'i', 't', 'L', 'o', 'n', 'g', 'S', 'e', 'c', 'r', 'e', 't', 'K', 'e', 'y', 'A', 'B', 'C', 'D', 'E', 'F'};

    private int version;
    private int type;
    private int length;
    private int reserved;
    private String payload;

    public static final int ERROR_VERSION_MISMATCH = 500;
    public static final int CLOSE_CONNECTION = 501;

    public Message(int version, int type, int reserved, String payload) {
        this.version = version;
        this.type = type;
        this.reserved = reserved;
        this.payload = payload;
        this.length = payload.getBytes(StandardCharsets.UTF_8).length;
    }

    public int getVersion() {
        return version;
    }

    public int getType() {
        return type;
    }

    public int getLength() {
        return length;
    }

    public int getReserved() {
        return reserved;
    }

    public void setType(int type){
        this.type = type;
    }
    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String serialize() {
        return version + "|" + type + "|" + length + "|" + reserved + "|" + payload;
    }

    public static Message deserialize(String message) {
        String[] parts = message.split("\\|", 5);
        int version = Integer.parseInt(parts[0]);
        int type = Integer.parseInt(parts[1]);
        int length = Integer.parseInt(parts[2]);
        int reserved = Integer.parseInt(parts[3]);
        String payload = parts[4];
        return new Message(version, type, reserved, payload);
    }

    public Message createHeartbeatMessage(String username) {
        return new Message(1, HEARTBEAT, 0, username);
    }

    public void encryptPayload() throws Exception {
        this.payload = encrypt(this.payload);
        this.length = this.payload.length();
    }

    // Decrypt payload
    public void decryptPayload() throws Exception {
        this.payload = decrypt(this.payload);
    }

    private static String encrypt(String Data) throws Exception {
        Cipher c = Cipher.getInstance(AES);
        SecretKeySpec key = new SecretKeySpec(keyValue, AES);
        c.init(Cipher.ENCRYPT_MODE, key);
        byte[] encVal = c.doFinal(Data.getBytes());
        return Base64.getEncoder().encodeToString(encVal);
    }

    private static String decrypt(String encryptedData) throws Exception {
        Cipher c = Cipher.getInstance(AES);
        SecretKeySpec key = new SecretKeySpec(keyValue, AES);
        c.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedValue = Base64.getDecoder().decode(encryptedData);
        return new String(c.doFinal(decodedValue), StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "Message{" +
                "version=" + version +
                ", type=" + type +
                ", length=" + length +
                ", reserved=" + reserved +
                ", payload='" + payload + '\'' +
                '}';
    }


}
