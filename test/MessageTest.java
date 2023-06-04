import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MessageTest {

    private Message message;

    @BeforeEach
    public void setUp() {
        message = new Message(1, Message.SEND_MESSAGE, 0, "Hello, world!");
    }

    @Test
    public void testSerialize() {
        String serialized = message.serialize();
        assertEquals("1|2|13|0|Hello, world!", serialized);
    }

    @Test
    public void testDeserialize() {
        String serialized = "1|3|5|0|Group1";
        Message deserialized = Message.deserialize(serialized);
        assertEquals(1, deserialized.getVersion());
        assertEquals(Message.CREATE_GROUP, deserialized.getType());
        assertEquals(6, deserialized.getLength());
        assertEquals(0, deserialized.getReserved());
        assertEquals("Group1", deserialized.getPayload());
    }

    @Test
    public void testEncryptionAndDecryption() throws Exception {
        String originalPayload = message.getPayload();
        message.encryptPayload();
        assertNotEquals(originalPayload, message.getPayload()); // Check that payload is encrypted
        message.decryptPayload();
        assertEquals(originalPayload, message.getPayload()); // Check that decrypted payload is same as original
    }

    @Test
    public void testCreateHeartbeatMessage() {
        Message heartbeatMessage = message.createHeartbeatMessage("user1");
        assertEquals(1, heartbeatMessage.getVersion());
        assertEquals(Message.HEARTBEAT, heartbeatMessage.getType());
        assertEquals(0, heartbeatMessage.getReserved());
        assertEquals("user1", heartbeatMessage.getPayload());
    }
}
