import java.nio.ByteBuffer;

enum msgType{
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
    msgType Msg;
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

    private Byte[] createHandshakePayload(int peerID) {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.put("P2PFILESHARINGPROJ".getBytes()); // 18 bytes of P2PFILESHARINGPROJ
        buffer.put(new byte[10]); // 10 bytes of 0s
        buffer.putInt(peerID); // 4 bytes of peerID
        Byte[] handshakePayload = new Byte[32];
        for (int i = 0; i < 32; i++) {
            handshakePayload[i] = buffer.array()[i];
        }
        return handshakePayload;
    }

    // makes rest
//    public Message(Byte[] payload){
//        this.payload = payload;
//        msgInterpret();
//    }
    //checks that the msg is valid and has the correct payload for the msg type
    private Interpretation msgInterpret(Byte[] payload){
        //4-byte message length field, 1-byte message type field, and a message payload with variable size.
        byte[] temp = new byte[4];
        for(int i = 0; i < 4; i++){
            temp[i] = payload[i];
        }

        int payloadLength = ByteBuffer.wrap(temp).getInt();

        switch (payload[4].intValue()){
            case 0 : //choke
                if(payloadLength != 1){
                    msgMisinterpreter();
                }
                else{
                    Msg = msgType.choke;
                }
                break;
            case 1 : //unchoke
                if(payloadLength != 1){
                    msgMisinterpreter();
                }
                else{
                    Msg = msgType.unchoke;
                }
                break;
            case 2 : //interested
                if(payloadLength != 1){
                    msgMisinterpreter();
                }
                else{
                    Msg = msgType.interested;
                }
                break;
            case 3 : //notInterested
                if(payloadLength != 1){
                    msgMisinterpreter();
                }
                else{
                    Msg = msgType.notInterested;
                }
                break;
            case 4 : //have
                if(payloadLength != 5){
                    msgMisinterpreter();
                }
                else{
                    Msg = msgType.have;
                }
                break;
            case 5 : //bitfield
                if(payloadLength != 5){
                    msgMisinterpreter();
                }
                else{
                    Msg = msgType.bitfield;
                }
                break;
            case 6 : //request
                if(payloadLength != 5){
                    msgMisinterpreter();
                }
                else{
                    Msg = msgType.request;
                }
                break;
            case 7 : //piece
                if(payloadLength != 5){
                    msgMisinterpreter();
                }
                else{
                    Msg = msgType.piece;
                }
                break;
            default:
                msgMisinterpreter();
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

    private void msgMisinterpreter(){
        System.out.println("Message Misinterpreted");
        System.exit(0);
    }

    public String ToString(Byte[] payload){
        //break down the payload into the message type, the payload, and the payload length

        Interpretation msg = msgInterpret(payload);
        String temp = "";
        temp += "Message Type: " + Msg + "\n";
        temp += "Message Payload Length: " + msg.payloadLength + "\n";
        temp += "Message Payload: " + msg.messagePayload + "\n";
        return temp;
    }

    public static class Interpretation {
        public msgType Msg;
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
