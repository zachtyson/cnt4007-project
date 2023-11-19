import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

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
            boolean allPeersHaveWholeFile = true;
            boolean hasAllPiecesCheckExit = peerConnection.hostProcess.hasAllPieces.get();

            for(Map.Entry<Integer,Boolean> entry: peerConnection.hostProcess.peerHasWholeFile.entrySet()) {
                if(!entry.getValue()) {
                    allPeersHaveWholeFile = false;
                    break;
                }
            }
            if(hasAllPiecesCheckExit && allPeersHaveWholeFile) {
                break;
                // System.out.println("Both peers have all pieces, closing connection");
                //peerConnection.close();
            }
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
                        peerConnection.sendResponses.add(interpretation.pieceIndex);
                        break;
                    case piece:
                        System.out.println("Received piece message from peer");
                        int pieceIndex = interpretation.pieceIndex;
                        byte[] piece = interpretation.messagePayload;
                        peerConnection.hostProcess.pieceMap.put(pieceIndex, peerProcess.pieceStatus.DOWNLOADED);
                        peerConnection.hostProcess.pieceData.put(pieceIndex, piece);
                        boolean hasAllPiecesAfterReceieve = true;
                        for(int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            if(peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.DOWNLOADED) {
                                hasAllPiecesAfterReceieve = false;
                                break;
                            }
                        }
                        peerConnection.hostProcess.hasAllPieces.set(hasAllPiecesAfterReceieve);
                        if(hasAllPiecesAfterReceieve) {
                            System.out.println("Host has all pieces");
                            peerConnection.hostProcess.peerHasWholeFile.put(peerConnection.peerId, true);
                        }
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
                            peerConnection.hostProcess.peerHasWholeFile.put(peerConnection.peerId, true);
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
        byte[] expectedLength = peerConnection.in.readNBytes(4);

        ByteBuffer wrapped = ByteBuffer.wrap(expectedLength);
        int expectedLengthInt = wrapped.getInt();
        //Type = 5th byte
        byte[] type = peerConnection.in.readNBytes(1);


        if (expectedLengthInt <= 0) {
            throw new IOException("Invalid message length: " + expectedLengthInt);
        }

        byte[] overallMessage = new byte[expectedLengthInt + expectedLength.length + 1];
        System.arraycopy(expectedLength, 0, overallMessage, 0, expectedLength.length);
        System.arraycopy(type, 0, overallMessage, 4, 1);
        byte[] message = receiveMessage(expectedLengthInt);
        System.arraycopy(message, 0, overallMessage, 5, message.length);
        //parseBitmapMessage(overallMessage);
        return overallMessage;
    }

    public static void parseBitmapMessage(byte[] bitmapMessage) {
        // Extracting the message length
        int messageLength = 0;
        for (int i = 0; i < 4; i++) {
            messageLength |= (bitmapMessage[i] & 0xFF) << (24 - 8 * i);
        }

        // Extracting the message type
        int messageType = bitmapMessage[4] & 0xFF;

        // Outputting the extracted information
        System.out.println("Message Length: " + messageLength);
        System.out.println("Message Type: " + messageType);

        // Extracting and interpreting the payload
        System.out.println("Payload:");
        for (int i = 0; i < bitmapMessage.length; i++) {
            for (int j = 7; j >= 0; j--) {
                boolean isDownloaded = (bitmapMessage[i] & (1 << j)) != 0;
                System.out.print(isDownloaded ? "1" : "0");
            }
            System.out.println(); // New line for each byte
        }
    }


    byte[] receiveMessage(int expectedLength) throws IOException {
        byte[] message = new byte[expectedLength];
        int offset = 0;
        while (offset < expectedLength) {
            int bytesRead = peerConnection.in.read(message, offset, expectedLength - offset);
            if (bytesRead == -1) {
                throw new IOException("Connection terminated before message completion.");
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
