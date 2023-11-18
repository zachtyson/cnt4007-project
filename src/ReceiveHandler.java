import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

//ReceiveHandler is in charge of receiving messages from the peer, and passing them to the host process
//and this happens by modifying the PeerConnection object's ConcurrentHashMap of byte[] and status enum
public class ReceiveHandler extends Thread{
    PeerConnection peerConnection;
    ReceiveHandler(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }
    @Override
    public void run() {
        while (true) {
            try {
                byte[] message = receiveMessageLength();
                if (message.length == 0) {
                    System.out.println("Received keep alive message from peer");
                    // continue;
                }
                //System.out.println(message.length);
                clearBuffer();
                System.out.println("Received message: " + Arrays.toString(message));
                Message.Interpretation interpretation = Message.msgInterpret(message);
//                if(interpretation.Msg == MsgType.bitfield) {
//                    //Bitwise, set the pieces that the peer has
//                    for(int i = 0; i < peerConnection.numPieces; i++) {
//                        int nthBit = Message.getNthBit(interpretation.messagePayload, i);
//                        if(nthBit == 1) {
//                            peerConnection.pieceMap.put(i, Main.pieceStatus.DOWNLOADED);
//                        }
//                    }
//                }
                switch(interpretation.Msg) {
                    case choke:
                        System.out.println("Received choke message from peer");
                        break;
                    case unchoke:
                        System.out.println("Received unchoke message from peer");
                        break;
                    case interested:
                        System.out.println("Received interested message from peer");
                        break;
                    case have:
                        System.out.println("Received have message from peer");
                        break;
                    case request:
                        System.out.println("Received request message from peer");
                        System.out.println("Piece index: " + interpretation.pieceIndex);

                        break;
                    case piece:
                        System.out.println("Received piece message from peer");
                        break;
                    case bitfield:
                        System.out.println("Received bitfield message from peer");
                        for(int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            int nthBit = Message.getNthBit(interpretation.messagePayload, i);
                            if(nthBit == 1) {
                                peerConnection.peerPieceMap.put(i, peerProcess.pieceStatus.DOWNLOADED);
                            }
                        }
                        //If entire bitfield is Downloaded, set hasAllPieces to true
                        //Honestly not entirely familiar with the specs so not sure if a peer would ever send a bitfield if they don't have all the pieces
                        //But I guess it's possible so it'll make this code a bit less efficient
                        boolean hasAllPieces = true;
                        for(int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            if(peerConnection.peerPieceMap.get(i) != peerProcess.pieceStatus.DOWNLOADED) {
                                hasAllPieces = false;
                                break;
                            }
                        }
                        peerConnection.peerHasAllPieces.set(hasAllPieces);
                        for(int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            System.out.println("Piece " + i + " is " + peerConnection.peerPieceMap.get(i));
                        }
                        if(hasAllPieces) {
                            System.out.println("Peer has all pieces");
                        }
                        break;
                    default:
                        System.out.println("Received unknown message from peer");
                        break;
                }
            } catch (IOException e) {
                //e.printStackTrace();
            }
        }
    }
    byte[] receiveMessageLength() throws IOException {
        // Each message (given from specifications) begins with a 4 byte length header
        // This method reads the length header and returns the message
        System.out.println("Received message from peerS " + peerConnection.peerId);
        byte[] expectedLength = peerConnection.in.readNBytes(4);
        ByteBuffer wrapped = ByteBuffer.wrap(expectedLength);
        int expectedLengthInt = -1;
        try {
            expectedLengthInt = wrapped.getInt();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println("Error: " + e.getStackTrace());
            System.out.println("Error: " + e.getCause());
            System.out.println("Error: " + e.getLocalizedMessage());
            System.out.println("Error: " + e.getSuppressed());
        }
                // Assumes a 4-byte length header
        if(expectedLengthInt == -1) {
            throw new IOException("Connection was terminated before message was complete.");
        }
        if(expectedLengthInt == 0) {
            return new byte[0];
        }
        System.out.println("Message length: " + expectedLengthInt);
        return receiveMessage(expectedLengthInt);
    }

    byte[] receiveMessage(int expectedLength) throws IOException {
        // Read message of length expectedLength bytes
        byte[] message = new byte[expectedLength+1]; //add +1 later for message type?
        int offset = 0;
        byte messageType = (byte) peerConnection.in.read();
        while (offset < expectedLength) {
            int bytesRead = peerConnection.in.read(message, offset, expectedLength - offset);
            if (bytesRead == -1) {
                throw new IOException("Connection was terminated before message was complete.");
            }
            offset += bytesRead;
        }

        return message;
    }

    void clearBuffer() throws IOException {
        int bytesAvailable = peerConnection.in.available();
        if (bytesAvailable > 0) {
            peerConnection.in.skip(bytesAvailable);
        }
    }
}
