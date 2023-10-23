import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;

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

    public static byte[] generateBitmapMessage(boolean[] bitmap) {
        // 4-byte message length field, 1-byte message type field, and a message payload with variable size.
        // 4-byte message length field
        // Each value in the bitfield is represented as a single bit in the bitfield payload.
        int messageLength = (int) Math.ceil(bitmap.length / 8.0);
        //message type is 5 aka 00000101
        byte[] bitmapMessage = new byte[messageLength + 5];
        // 4-byte message length field
        byte[] length = ByteBuffer.allocate(4).putInt(messageLength).array();
        System.arraycopy(length, 0, bitmapMessage, 0, 4);
        // 1-byte message type field
        bitmapMessage[4] = 5;
        // message payload
        for (int i = 0; i < bitmap.length; i++) {
            if (bitmap[i]) {
                bitmapMessage[5+(i / 8)] |= (1 << (i % 8));
                // I hate that I can't read this and I wrote it - Zach
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

    // makes rest
//    public Message(Byte[] payload){
//        this.payload = payload;
//        msgInterpret();
//    }
    //checks that the msg is valid and has the correct payload for the msg type
    private Interpretation msgInterpret(byte[] payload){
        //4-byte message length field, 1-byte message type field, and a message payload with variable size.
        byte[] temp = new byte[4];
        System.arraycopy(payload, 0, temp, 0, 4);

        int payloadLength = ByteBuffer.wrap(temp).getInt();
        if(payload.length < 5){
            //message is too short and doesn't even have a message type
            msgMisinterpreter(payload);
        }

        switch (payload[4]){
            case 0 : //choke
                if(payloadLength != 1){
                    msgMisinterpreter(payload);
                }
                else{
                    Msg = MsgType.choke;
                }
                break;
            case 1 : //unchoke
                if(payloadLength != 1){
                    msgMisinterpreter(payload);
                }
                else{
                    Msg = MsgType.unchoke;
                }
                break;
            case 2 : //interested
                if(payloadLength != 1){
                    msgMisinterpreter(payload);
                }
                else{
                    Msg = MsgType.interested;
                }
                break;
            case 3 : //notInterested
                if(payloadLength != 1){
                    msgMisinterpreter(payload);
                }
                else{
                    Msg = MsgType.notInterested;
                }
                break;
            case 4 : //have
                if(payloadLength != 5){
                    msgMisinterpreter(payload);
                }
                else{
                    Msg = MsgType.have;
                }
                break;
            case 5 : //bitfield
                if(payloadLength != 5){
                    msgMisinterpreter(payload);
                }
                else{
                    Msg = MsgType.bitfield;
                }
                break;
            case 6 : //request
                if(payloadLength != 5){
                    msgMisinterpreter(payload);
                }
                else{
                    Msg = MsgType.request;
                }
                break;
            case 7 : //piece
                if(payloadLength != 5){
                    msgMisinterpreter(payload);
                }
                else{
                    Msg = MsgType.piece;
                }
                break;
            default:
                msgMisinterpreter(payload);
                break;
        }
        //First 5 bytes are the length (4) and the type (1) so the payload is the rest
        String messagePayload;
        if(payloadLength > 5){
            temp = new byte[payloadLength - 5];
            for(int i = 5,x= 0; i < payloadLength; i++,x++){
                temp[x] = payload[i];
            }
            messagePayload = ByteBuffer.wrap(temp).toString();
        }
        else{
            messagePayload = null;
        }
        Interpretation interpretation = new Interpretation();
        interpretation.Msg = Msg;
        interpretation.messagePayload = messagePayload;
        interpretation.payloadLength = payloadLength;
        return interpretation;
    }

    private void msgMisinterpreter(byte[] payload){
        System.out.println("Bad Message");
        //Print each byte
        for(byte b : payload){
            System.out.println(b);
        }
        System.exit(0);
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
        public String messagePayload;
        public int payloadLength;
    }
}
/*
After handshaking, each peer can send a stream of actual messages. An actual message
consists of 4-byte message length field, 1-byte message type field, and a message
payload with variable size.
 */


// NEXT STEP: create a flipped version of the interpreter to convert information to bitwise to be sent.
