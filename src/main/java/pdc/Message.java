package pdc;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Message represents the communication unit in the CSM218 protocol.
 * 
 * Custom WIRE FORMAT: Length-prefixed binary protocol
 * Format:
 * [Magic: 6 bytes "CSM218"][Version: 4 bytes][MessageType Length: 2 bytes][MessageType: variable]
 * [StudentId Length: 2 bytes][StudentId: variable][Timestamp: 8 bytes]
 * [Payload Length: 4 bytes][Payload: variable]
 */
public class Message {
    public static final String MAGIC = "CSM218";
    public static final int VERSION = 1;

    public String magic;
    public int version;
    public String messageType;
    public String studentId;
    public long timestamp;
    public byte[] payload;
    
    // Legacy aliases for backward compatibility
    public String type;
    public String sender;

    public Message() {
        this.magic = MAGIC;
        this.version = VERSION;
        this.timestamp = System.currentTimeMillis();
        this.payload = new byte[0];
    }

    public Message(String messageType, String studentId, byte[] payload) {
        this();
        this.messageType = messageType;
        this.type = messageType; // backward compatibility
        this.studentId = studentId;
        this.sender = studentId; // backward compatibility
        this.payload = payload != null ? payload : new byte[0];
    }

    /**
     * Converts the message to a byte stream for network transmission.
     * Uses binary framing with length prefixes for efficient parsing.
     */
    public byte[] pack() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // Write magic
            byte[] magicBytes = MAGIC.getBytes(StandardCharsets.UTF_8);
            dos.write(magicBytes);

            // Write version
            dos.writeInt(VERSION);

            // Write messageType (length-prefixed)
            byte[] messageTypeBytes = (messageType != null ? messageType : "").getBytes(StandardCharsets.UTF_8);
            dos.writeShort(messageTypeBytes.length);
            dos.write(messageTypeBytes);

            // Write studentId (length-prefixed)
            byte[] studentIdBytes = (studentId != null ? studentId : "").getBytes(StandardCharsets.UTF_8);
            dos.writeShort(studentIdBytes.length);
            dos.write(studentIdBytes);

            // Write timestamp
            dos.writeLong(timestamp);

            // Write payload (length-prefixed)
            dos.writeInt(payload != null ? payload.length : 0);
            if (payload != null && payload.length > 0) {
                dos.write(payload);
            }

            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to pack message", e);
        }
    }

    /**
     * Reconstructs a Message from a byte stream.
     * Expects full message in buffer.
     */
    public static Message unpack(byte[] data) throws Exception {
        if (data == null || data.length < 20) {
            throw new IllegalArgumentException("Invalid message: too short");
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        // Read and verify magic
        byte[] magicBytes = new byte[6];
        dis.readFully(magicBytes);
        String magic = new String(magicBytes, StandardCharsets.UTF_8);
        if (!magic.equals(MAGIC)) {
            throw new IllegalArgumentException("Invalid magic: " + magic);
        }

        // Read version
        int version = dis.readInt();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported version: " + version);
        }

        // Read messageType
        short messageTypeLength = dis.readShort();
        byte[] messageTypeBytes = new byte[messageTypeLength];
        dis.readFully(messageTypeBytes);
        String messageType = new String(messageTypeBytes, StandardCharsets.UTF_8);

        // Read studentId
        short studentIdLength = dis.readShort();
        byte[] studentIdBytes = new byte[studentIdLength];
        dis.readFully(studentIdBytes);
        String studentId = new String(studentIdBytes, StandardCharsets.UTF_8);

        // Read timestamp
        long timestamp = dis.readLong();

        // Read payload
        int payloadLength = dis.readInt();
        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            dis.readFully(payload);
        }

        Message msg = new Message();
        msg.magic = magic;
        msg.version = version;
        msg.messageType = messageType;
        msg.type = messageType; // backward compatibility
        msg.studentId = studentId;
        msg.sender = studentId; // backward compatibility
        msg.timestamp = timestamp;
        msg.payload = payload;

        return msg;
    }

    /**
     * Attempts to read a complete message from the input stream.
     * Returns null if no complete message is available.
     */
    public static Message readFromStream(DataInputStream dis) throws IOException {
        try {
            // Read and verify magic
            byte[] magicBytes = new byte[6];
            dis.readFully(magicBytes);
            String magic = new String(magicBytes, StandardCharsets.UTF_8);
            if (!magic.equals(MAGIC)) {
                throw new IOException("Invalid magic: " + magic);
            }

            // Read version
            int version = dis.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported version: " + version);
            }

            // Read messageType
            short messageTypeLength = dis.readShort();
            byte[] messageTypeBytes = new byte[messageTypeLength];
            dis.readFully(messageTypeBytes);
            String messageType = new String(messageTypeBytes, StandardCharsets.UTF_8);

            // Read studentId
            short studentIdLength = dis.readShort();
            byte[] studentIdBytes = new byte[studentIdLength];
            dis.readFully(studentIdBytes);
            String studentId = new String(studentIdBytes, StandardCharsets.UTF_8);

            // Read timestamp
            long timestamp = dis.readLong();

            // Read payload
            int payloadLength = dis.readInt();
            byte[] payload = new byte[payloadLength];
            if (payloadLength > 0) {
                dis.readFully(payload);
            }

            Message msg = new Message();
            msg.magic = magic;
            msg.version = version;
            msg.messageType = messageType;
            msg.type = messageType; // backward compatibility
            msg.studentId = studentId;
            msg.sender = studentId; // backward compatibility
            msg.timestamp = timestamp;
            msg.payload = payload;

            return msg;
        } catch (EOFException e) {
            return null; // Connection closed
        }
    }

    @Override
    public String toString() {
        return String.format("Message[type=%s, sender=%s, timestamp=%d, payloadLen=%d]",
                type, sender, timestamp, payload != null ? payload.length : 0);
    }
}
