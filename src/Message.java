import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

enum MsgType {
    handshake,
    choke, // 0 // no payload
    unchoke, // 1 //no payload
    interested,// 2 // no payload
    notInterested,// 3 // no payload
    have,// 4 // 4-byte payload
    bitfield, // 5 // variable size, depending on the size of the bitfield
    request, // 6 // 4-byte payload
    piece // 7 // 4-byte payload
}

public class Message {

    public static final boolean DEBUG = false;

    static void printError(String message) {
        //Duplicate code from peerProcess, but I wanted to keep Message.java as self-contained as possible
        System.err.println("Error: " + message);
        //System.exit(1);
    }

    static void printDebug(String message) {
        if(DEBUG) {
            System.out.println("Debug: " + message);
        }
    }

    private Message() {
        //private constructor}
    }
    MsgType Msg;
    /*
 ‘bitfield’ messages is only sent as the first message right after handshaking is done when
a connection is established. ‘bitfield’ messages have a bitfield as its payload. Each bit in
the bitfield payload represents whether the peer has the corresponding piece or not. The
first byte of the bitfield corresponds to piece indices 0 – 7 from high bit to low bit,
respectively. The next one corresponds to piece indices 8 – 15, etc. Spare bits at the end
are set to zero. Peers that don’t have anything yet may skip a ‘bitfield’ message.
 */

//   Byte[] payload = null;
//   String messagepayload = null;
//   int payloadlength = -1;
//   overloaded constructors for message class
//   // makes payload
//    public Message(msgType Msg, String messagepayload, int payloadlength){
//        this.Msg = Msg;
//        this.messagepayload = messagepayload;
//        this.payloadlength = payloadlength;
//
//    }
//    public Message(msgType Msg, Byte[] payload){
//        this.Msg = Msg;
//        this.payload = payload;
//    }
//
//    public Message(int peerID) {
//        // Handshake message
//        this.Msg = msgType.handshake;
//        this.payload = createHandshakePayload(peerID);
//    }

    public static byte[] createHandshakePayload(int peerID) {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.put("P2PFILESHARINGPROJ".getBytes()); // 18 bytes of P2PFILESHARINGPROJ
        buffer.put(new byte[10]); // 10 bytes of 0 bits
        buffer.putInt(peerID); // 4 bytes of peerID
        byte[] handshakePayload = new byte[32];
        for (int i = 0; i < 32; i++) {
            handshakePayload[i] = buffer.array()[i];
        }
        return handshakePayload;
    }

    public static byte[] generateHeaderAndMessageType(int messageLength, MsgType msgType) {
        // 4-byte message length field, 1-byte message type field, and a message payload with variable size.
        byte[] headerAndMessageType = new byte[5];
        // 4-byte message length field
        byte[] length = ByteBuffer.allocate(4).putInt(messageLength).array();
        System.arraycopy(length, 0, headerAndMessageType, 0, 4);
        // 1-byte message type field
        switch (msgType) {
            case choke:
                headerAndMessageType[4] = 0;
                break;
            case unchoke:
                headerAndMessageType[4] = 1;
                break;
            case interested:
                headerAndMessageType[4] = 2;
                break;
            case notInterested:
                headerAndMessageType[4] = 3;
                break;
            case have:
                headerAndMessageType[4] = 4;
                break;
            case bitfield:
                headerAndMessageType[4] = 5;
                break;
            case request:
                headerAndMessageType[4] = 6;
                break;
            case piece:
                headerAndMessageType[4] = 7;
                break;
            default:
                printError("Invalid message type");
                System.exit(0);
        }
        return headerAndMessageType;
    }

    public static byte[] generateBitmapMessage(ConcurrentHashMap<Integer, peerProcess.pieceStatus> bitmap, int numPieces) {
        // 4-byte message length field, 1-byte message type field, and a message payload with variable size.
        // 4-byte message length field
        // Each value in the bitfield is represented as a single bit in the bitfield payload.
        int messageLength = (int) Math.ceil(numPieces / 8.0);
        //message type is 5 aka 00000101
        byte[] bitmapMessage = new byte[messageLength + 5];
        byte[] headerAndMessageType = generateHeaderAndMessageType(messageLength, MsgType.bitfield);
        System.arraycopy(headerAndMessageType, 0, bitmapMessage, 0, 5);

        // message payload
        for (int i = 0; i < numPieces; i++) {
            if (bitmap.get(i) == peerProcess.pieceStatus.DOWNLOADED) {
                bitmapMessage[5+(i / 8)] |= (1 << (7 - (i % 8)));
                // I hate that I can't read this and I wrote it - Zach
                // this should convert a bitmap into something like 11111000 (for a full bitmap, note the trailing zeroes to make a byte)
            }
        }
        // Converting the first 4 bytes into messageLength integer
        //            int value = 0;
        //            value |= (bitfieldMessage[5] & 0xFF) << 24; // Shift by 24 bits (3 bytes)
        //            value |= (bitfieldMessage[1] & 0xFF) << 16; // Shift by 16 bits (2 bytes)
        //            value |= (bitfieldMessage[2] & 0xFF) << 8;  // Shift by 8 bits (1 byte)
        //            value |= (bitfieldMessage[3] & 0xFF);       // No shift
        // You can do the same with the message type byte by
        //            int messageType = bitfieldMessage[4] & 0xFF;
        return bitmapMessage;
    }

    public static boolean checkHandshake(byte[] handshake, int expectedPeerID) {
        if (handshake.length != 32) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.wrap(handshake);
        byte[] p2pHeader = new byte[18];
        buffer.get(p2pHeader, 0, 18);
        if (!Arrays.equals(p2pHeader, "P2PFILESHARINGPROJ".getBytes())) {
            return false;
        }
        // Check bytes 19-27 for zeros
        for (int i = 0; i < 10; i++) {
            if (buffer.get() != 0) {
                return false;
            }
        }
        int peerID = buffer.getInt();
        printDebug("Given Peer ID: " + peerID);
        printDebug("Expected Peer ID: " + expectedPeerID);
        return peerID == expectedPeerID;
    }

    public static int getIDFromHandshake(byte[] handshake) {
        if (handshake.length != 32) {
            return -1;
        }
        ByteBuffer buffer = ByteBuffer.wrap(handshake);
        byte[] p2pHeader = new byte[18];
        buffer.get(p2pHeader, 0, 18);
        if (!Arrays.equals(p2pHeader, "P2PFILESHARINGPROJ".getBytes())) {
            return -1;
        }
        // Check bytes 19-27 for zeros
        for (int i = 0; i < 10; i++) {
            if (buffer.get() != 0) {
                return -1;
            }
        }
        return buffer.getInt();
    }

    // makes rest
//    public Message(Byte[] payload){
//        this.payload = payload;
//        msgInterpret();
//    }
    //checks that the msg is valid and has the correct payload for the msg type
    public static Interpretation msgInterpret(byte[] payload){
        //4-byte message length field, 1-byte message type field, and a message payload with variable size.
        byte[] temp = new byte[4];
        System.arraycopy(payload, 0, temp, 0, 4);
        Interpretation interpretation = new Interpretation();
        printDebug("Payload: " + Arrays.toString(payload));
        int payloadLengthTemp = ByteBuffer.wrap(temp).getInt();
        long payloadLengthTempLong = payloadLengthTemp & 0xffffffffL; //converts to unsigned int
        int payloadLength = (int) payloadLengthTempLong; //converts back to int
        if(payload.length < 5){
            //message is too short and doesn't even have a message type
            msgMisinterpreter(payload);
        }


        byte messageType = payload[4];
        printDebug("Message Type: " + messageType);
        switch (messageType){
            case 0 : //choke
                if(payloadLength != 1){
                    msgMisinterpreter(payload);
                }
                else{
                    interpretation.Msg = MsgType.choke;
                }
                break;
            case 1 : //unchoke
                if(payloadLength != 1){
                    msgMisinterpreter(payload);
                }
                else{
                    interpretation.Msg = MsgType.unchoke;
                }
                break;
            case 2 : //interested
                if(payloadLength != 1){
                    msgMisinterpreter(payload);
                }
                else{
                    interpretation.Msg = MsgType.interested;
                }
                break;
            case 3 : //notInterested
                if(payloadLength != 1){
                    msgMisinterpreter(payload);
                }
                else{
                    interpretation.Msg = MsgType.notInterested;
                }
                break;
            case 4 : //have
                printDebug("Have message");
                interpretation.Msg = MsgType.have;
                printDebug(Arrays.toString(payload));
                //index is the 4 bytes after the message type
                int haveIndex = ByteBuffer.wrap(payload, 5, 4).getInt();
                long haveIndexLong = haveIndex & 0xffffffffL; //converts to unsigned int
                interpretation.pieceIndex = (int) haveIndexLong; //converts back to int

                break;
            case 5 : //bitfield
                interpretation.Msg = MsgType.bitfield;
                break;
            case 6 : //request
                if(payloadLength != 4){
                    msgMisinterpreter(payload);
                }
                else{
                    interpretation.Msg = MsgType.request;
                    interpretation.pieceIndex = ByteBuffer.wrap(payload, 5, 4).getInt();
                }
                break;
            case 7 : //piece
                interpretation.Msg = MsgType.piece;
                interpretation.pieceIndex = ByteBuffer.wrap(payload, 5, 4).getInt();
                interpretation.messagePayload = new byte[payloadLength - 4];
                System.arraycopy(payload, 9, interpretation.messagePayload, 0, payloadLength - 4);
                break;
            default:
                printError("Invalid message type");
                msgMisinterpreter(payload);
                break;
        }
        //First 5 bytes are the length (4) and the type (1) so the payload is the rest
        printDebug("Payload Length: " + payloadLength);
        byte[] messagePayload = new byte[payloadLength];
        System.arraycopy(payload, 5, messagePayload, 0, payloadLength);
        interpretation.messagePayload = messagePayload;
        interpretation.payloadLength = payloadLength;
        return interpretation;
    }

    public static byte[] generateHasPieceMessage(int pieceIndex) {
        printDebug("Generating have message for piece " + pieceIndex);
        // 4-byte message length field, 1-byte message type field, and 4 bytes for piece index
        int messageLength = 4;
        byte[] haveMessage = new byte[messageLength + 5];
        byte[] headerAndMessageType = generateHeaderAndMessageType(messageLength, MsgType.have);
        System.arraycopy(headerAndMessageType, 0, haveMessage, 0, 5);

        printDebug("Generate piece message: " + pieceIndex);
        // message payload
        byte[] pieceIndexBytes = ByteBuffer.allocate(4).putInt(pieceIndex).array();
        System.arraycopy(pieceIndexBytes, 0, haveMessage, 5, 4);
        printDebug("Generate piece message: " + Arrays.toString(haveMessage));

        return haveMessage;
    }

    private static void msgMisinterpreter(byte[] payload){
        printError("Bad Message");
        //Print each byte
        for(byte b : payload){
            printError("Byte: " + b);
        }
        System.exit(0);
    }

    public static byte[] generatePieceMessage(byte[] payload,int index) {
        if(payload == null){
            printError("Payload is null at index " + index);
            System.exit(0);
        }
        // 4-byte message length field, 1 byte message type field, 4 bytes for piece index, and a message payload with variable size.
        int messageLength = payload.length + 4;
        byte[] pieceMessage = new byte[messageLength + 5];
        byte[] headerAndMessageType = generateHeaderAndMessageType(messageLength, MsgType.piece);
        System.arraycopy(headerAndMessageType, 0, pieceMessage, 0, 5);

        // message payload
        byte[] pieceIndex = ByteBuffer.allocate(4).putInt(index).array();
        System.arraycopy(pieceIndex, 0, pieceMessage, 5, 4);
        System.arraycopy(payload, 0, pieceMessage, 9, payload.length);
        printDebug("Piece message length: " + pieceMessage.length);
        return pieceMessage;
    }

    public static byte[] generateRequestMessage(int index) {
        // 4-byte message length field, 1 byte message type field, and a 4 byte message payload
        // 4-byte message length field
        int messageLength = 4;
        byte[] requestMessage = new byte[messageLength + 5];
        byte[] headerAndMessageType = generateHeaderAndMessageType(messageLength, MsgType.request);
        System.arraycopy(headerAndMessageType, 0, requestMessage, 0, 5);

        // message payload
        byte[] pieceIndex = ByteBuffer.allocate(4).putInt(index).array();
        System.arraycopy(pieceIndex, 0, requestMessage, 5, 4);
        return requestMessage;
    }

    public static byte[] generateChokeMessage() {
        //4 byte message length field, 1 byte message type field, and no message payload
        int messageLength = 0;
        byte[] chokeMessage = new byte[messageLength + 5];
        byte[] headerAndMessageType = generateHeaderAndMessageType(messageLength, MsgType.choke);
        System.arraycopy(headerAndMessageType, 0, chokeMessage, 0, 5);

        // message payload
        return chokeMessage;
    }

    public String ToString(byte[] payload){
        //break down the payload into the message type, the payload, and the payload length

        Interpretation msg = msgInterpret(payload);
        String temp = "";
        temp += "Message Type: " + Msg + "\n";
        temp += "Message Payload Length: " + msg.payloadLength + "\n";
        temp += "Message Payload: " + msg.messagePayload + "\n";
        return temp;
    }

    public static class Interpretation {
        public MsgType Msg;
        public byte[] messagePayload;
        public int payloadLength;
        Integer pieceIndex = null;
    }

    private static List<byte[]> splitFileIntoMemory(String sourceFile, int pieceSize) {
        // Split file into pieces of size pieceSize
        // This should really only be used for peers that start with the file
        // Other peers should receive pieces from other peers
        // pieceSize is in bytes
        Path sourcePath = Paths.get(sourceFile);
        List<byte[]> filePieces = new ArrayList<>();

        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(sourcePath))) {
            byte[] buffer;
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer = new byte[pieceSize])) > 0) {
                if (bytesRead < pieceSize) {
                    // Trim buffer if not full
                    buffer = Arrays.copyOf(buffer, bytesRead);
                }
                filePieces.add(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return filePieces;
    }

    public static int getNthBit(byte[] byteArray, int n) {
        if (byteArray == null || byteArray.length * 8 <= n) {
            throw new IllegalArgumentException("Bit position out of range");
        }

        int byteIndex = n / 8; // Find the index of the byte containing the nth bit
        int bitPosition = n % 8; // Find the position of the bit in that byte

        // Extract the bit using a bitwise AND operation, then shift right
        return (byteArray[byteIndex] >> (7 - bitPosition)) & 1;
    }

}
/*
After handshaking, each peer can send a stream of actual messages. An actual message
consists of 4-byte message length field, 1-byte message type field, and a message
payload with variable size.
 */


// NEXT STEP: create a flipped version of the interpreter to convert information to bitwise to be sent.
