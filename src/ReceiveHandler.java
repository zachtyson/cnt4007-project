import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

//ReceiveHandler is in charge of receiving messages from the peer, and passing them to the host process
//and this happens by modifying the PeerConnection object's ConcurrentHashMap of byte[] and status enum
public class ReceiveHandler extends Thread{
    PeerConnection peerConnection;
    ArrayList<PeerConnection> interestedNeighbors = new ArrayList<PeerConnection>();
    ReceiveHandler(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }
    @Override
    public void run() {
        while (!peerConnection.socket.isClosed()) {
            //check if socket is closed

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
                    peerProcess.printDebug("Received keep alive message from peer");
                    // continue;
                }
                clearBuffer();
                peerProcess.printDebug("Peer+ " + peerConnection.hostProcess.selfPeerId +"Received message: " + Arrays.toString(message) + " from peer " + peerConnection.peerId);
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
                        //todo: implement choke
                        //choke means that you can't request pieces from the peer
                        peerProcess.printDebug("Received choke message from peer");
                        break;
                    case unchoke:
                        //todo: implement unchoke
                        //unchoke means that you can request pieces from the peer again
                        peerProcess.printDebug("Received unchoke message from peer");
                        break;
                    case interested:
                        //todo: implement interested
                        //honestly im not even sure what to put for interested and not interested
                        //like obviously they tell us what interested and not interested means, but I'm not sure what to do with that information
                        peerProcess.printDebug("Received interested message from peer");
                        peerConnection.hostProcess.logger.logReceiveInterested(String.valueOf(peerConnection.peerId));
                        peerConnection.setPeerInterested(true);
                        interestedNeighbors.add(peerConnection);
                        break;
                    case notInterested:
                        //todo: implement not interested
                        peerProcess.printDebug("Received not interested message from peer");
                        peerConnection.hostProcess.logger.logReceiveNotInterested(String.valueOf(peerConnection.peerId));
                        peerConnection.setPeerInterested(false);
                        interestedNeighbors.remove(peerConnection);
                        break;
                    case have:
                        peerProcess.printDebug("Received have message from peer");
                        peerConnection.hostProcess.logger.logReceiveHave(String.valueOf(peerConnection.peerId), interpretation.pieceIndex);
                        //Update bitmap to reflect that the peer has the piece
                        peerConnection.peerPieceMap.put(interpretation.pieceIndex, peerProcess.pieceStatus.DOWNLOADED);
                        //If entire bitfield is Downloaded, set hasAllPieces to true
                        boolean hasAllPiecesAfterHave = true;
                        for(int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            if(peerConnection.peerPieceMap.get(i) != peerProcess.pieceStatus.DOWNLOADED) {
                                //System.err.println("Peer " + peerConnection.peerId + " does not have piece " + i);
                                hasAllPiecesAfterHave = false;
                                break;
                            }
                        }
                        if(hasAllPiecesAfterHave) {
                            //System.out.println("Peer has all pieces");
                            boolean previousValue = peerConnection.peerHasAllPieces.getAndSet(true);
                            peerConnection.hostProcess.peerHasWholeFile.put(peerConnection.peerId, true);
                            if(!previousValue) {
                                peerConnection.hostProcess.logger.logPeerCompletion(String.valueOf(peerConnection.peerId));
                                //Checking to prevent duplicate log messages
                            }
                        }
                        peerConnection.setSelfInterested(peerConnection.peerHasAnyPiecesWeDont());
                        break;
                    case request:
                        peerProcess.printDebug("Received request message from peer");
                        peerProcess.printDebug("Piece index: " + interpretation.pieceIndex);
                        byte[] pieceRequested = peerConnection.hostProcess.pieceData.get(interpretation.pieceIndex);
                        byte[] messageToPeer = Message.generatePieceMessage(pieceRequested, interpretation.pieceIndex);
                        peerConnection.sendResponses.add(messageToPeer);
                        break;
                    case piece:
                        peerProcess.printDebug("Received piece message from peer");

                        //add to bytes received, this is for measuring download speed
                        int messageLength = message.length;
                        peerConnection.addToMessageBytesReceived(LocalDateTime.now(), messageLength);

                        int pieceIndex = interpretation.pieceIndex;
                        byte[] piece = interpretation.messagePayload;
                        peerConnection.currentlyRequestedPiece.set(-1); //Set to -1 to indicate that no piece is currently being requested
                        peerProcess.printDebug("Currently requested piece set to -1");
                        peerConnection.hostProcess.pieceMap.put(pieceIndex, peerProcess.pieceStatus.DOWNLOADED);
                        peerConnection.hostProcess.pieceData.put(pieceIndex, piece);
                        int numPiecesLeft = 0;
                        for(int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            if(peerConnection.hostProcess.pieceMap.get(i) == peerProcess.pieceStatus.DOWNLOADED) {
                                numPiecesLeft++;
                            }
                        }
                        peerConnection.hostProcess.logger.logDownloadedPiece(String.valueOf(peerConnection.peerId), pieceIndex, numPiecesLeft);
                        //send message to host process that piece has been received
                        byte[] messageToHost = Message.generateHasPieceMessage(pieceIndex);
                        peerProcess.printDebug("Sending message that piece " + pieceIndex + " has been received");
                        //peerConnection.sendResponses.add(messageToHost); //dont think this is necessary
                        for(PeerConnection peerConnection: peerConnection.hostProcess.peerConnectionVector) {
                            peerConnection.sendResponses.add(messageToHost);
                        }
                        //Put messageToHost in the front of the queue

                        boolean hasAllPiecesAfterReceieve = true;
                        for(int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            if(peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.DOWNLOADED) {
                                hasAllPiecesAfterReceieve = false;
                                break;
                            }
                        }
                        //TODO: THIS IS A HACKY WAY TO DO THIS, FIX THIS LATER
                    {
                        if(hasAllPiecesAfterReceieve) {
                            boolean previousValue = peerConnection.hostProcess.hasAllPieces.getAndSet(true);
                            if(!previousValue) {
                                peerConnection.hostProcess.logger.logCompletion();
                                byte[] bitmapMessage = Message.generateBitmapMessage(peerConnection.hostProcess.pieceMap, peerConnection.commonCfg.numPieces);
                                peerConnection.sendResponses.add(bitmapMessage);
                            }
                        }
                    }

                    break;
                    case bitfield:
                        peerProcess.printDebug("Received bitfield message from peer");
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
                            peerProcess.printDebug("Piece " + i + " is " + peerConnection.peerPieceMap.get(i));
                        }
                        if(hasAllPieces) {
                            peerProcess.printDebug("Peer has all pieces");
                            peerConnection.hostProcess.peerHasWholeFile.put(peerConnection.peerId, true);
                            peerConnection.hostProcess.logger.logPeerCompletion(String.valueOf(peerConnection.peerId));
                        }
                        peerConnection.setSelfInterested(peerConnection.peerHasAnyPiecesWeDont());

                        break;
                    default:
                        System.out.println("Received unknown message from peer");
                        break;
                }
            } catch (IOException e) {
                //e.printStackTrace();
                //peerProcess.printError("Connection closed");
                //peerProcess.printError("Peer+ " + peerConnection.hostProcess.selfPeerId +" Connection closed");
                peerConnection.close();
                break;
            }
        }
        //peerConnection.hostProcess.logger.logShutdown();
    }

    byte[] receiveMessageLength() throws IOException {
        byte[] expectedLength = peerConnection.in.readNBytes(4);
        if (expectedLength.length < 4) {
            throw new IOException("Connection closed or insufficient data read for message length");
        }

        ByteBuffer wrapped = ByteBuffer.wrap(expectedLength);
        int expectedLengthInt = wrapped.getInt();
        //Type = 5th byte
        byte[] type = peerConnection.in.readNBytes(1);


        if (expectedLengthInt < 0) {
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
