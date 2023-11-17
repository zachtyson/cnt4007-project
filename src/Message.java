import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.ArrayList;
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
                System.out.println("Invalid message type");
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
        System.out.println("Given Peer ID: " + peerID);
        System.out.println("Expected Peer ID: " + expectedPeerID);
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

        int payloadLength = ByteBuffer.wrap(temp).getInt();
        if(payload.length < 5){
            //message is too short and doesn't even have a message type
            msgMisinterpreter(payload);
        }

        byte messageType = payload[4];
        System.out.println("Message Type: " + messageType);
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
                if(payloadLength != 5){
                    msgMisinterpreter(payload);
                }
                else{
                    interpretation.Msg = MsgType.have;
                }
                break;
            case 5 : //bitfield
                if(payloadLength != 5){
                    msgMisinterpreter(payload);
                }
                else{
                    interpretation.Msg = MsgType.bitfield;
                }
                break;
            case 6 : //request
                if(payloadLength != 5){
                    msgMisinterpreter(payload);
                }
                else{
                    interpretation.Msg = MsgType.request;
                }
                break;
            case 7 : //piece
                    interpretation.Msg = MsgType.piece;
                    interpretation.pieceIndex = ByteBuffer.wrap(payload, 5, 4).getInt();
                break;
            default:
                System.out.println("Invalid message type");
                msgMisinterpreter(payload);
                break;
        }
        //First 5 bytes are the length (4) and the type (1) so the payload is the rest
        System.out.println("Payload Length: " + payloadLength);
        byte[] messagePayload = new byte[payloadLength];
        System.arraycopy(payload, 5, messagePayload, 0, payloadLength);
        interpretation.messagePayload = messagePayload;
        interpretation.payloadLength = payloadLength;
        return interpretation;
    }

    private static void msgMisinterpreter(byte[] payload){
        System.out.println("Bad Message");
        //Print each byte
//        for(byte b : payload){
//            System.out.println(b);
//        }
        System.exit(0);
    }

    public static byte[] generatePieceMessage(byte[] payload) {
        // 4-byte message length field, 1-byte message type field, and a message payload with variable size.
        // 4-byte message length field
        int messageLength = payload.length;
        byte[] pieceMessage = new byte[messageLength + 5];
        byte[] headerAndMessageType = generateHeaderAndMessageType(messageLength, MsgType.piece);
        System.arraycopy(headerAndMessageType, 0, pieceMessage, 0, 5);

        // message payload
        System.arraycopy(payload, 0, pieceMessage, 5, messageLength);
        System.out.println("Piece message length: " + pieceMessage.length);
        return pieceMessage;
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

}
/*
After handshaking, each peer can send a stream of actual messages. An actual message
consists of 4-byte message length field, 1-byte message type field, and a message
payload with variable size.
 */


// NEXT STEP: create a flipped version of the interpreter to convert information to bitwise to be sent.
