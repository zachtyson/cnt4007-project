import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;

public class SendHandler extends Thread {
    PeerConnection peerConnection;
    SendHandler(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
        this.lastMessageTime = Instant.now();
    }

    private volatile Instant lastMessageTime; //This does nothing currently, but I think I'm going to add a timeout feature

    @Override
    public void run() {
        peerProcess.printDebug("Starting send handler for peer");
        boolean hasAnyPiecesAtStart = checkForPieces();
        if (hasAnyPiecesAtStart) {
            // If it has any pieces, send bitfield message
            int numBytes = (int) Math.ceil(peerConnection.commonCfg.numPieces / 8.0);
            peerProcess.printDebug("Num bytes: " + numBytes);
            //byte[] headerAndType = Message.generateHeaderAndMessageType(numBytes,MsgType.bitfield);
            byte[] bitfieldMessage = Message.generateBitmapMessage(peerConnection.hostProcess.pieceMap, peerConnection.commonCfg.numPieces);

            try {
                //Print full message
                peerProcess.printDebug("Message: " + Arrays.toString(bitfieldMessage));
                sendMessage(bitfieldMessage);
                peerProcess.printDebug("Sent bitfield to peer");
            } catch (IOException e) {
                //e.printStackTrace();
            }
        }
        boolean hasAllPiecesAtStart = peerConnection.hostProcess.hasAllPieces.get();
        if(hasAllPiecesAtStart) {
            peerConnection.hostProcess.logger.logPeerCompletion(String.valueOf(peerConnection.peerId));
            peerConnection.hostProcess.hasAllPieces.set(true);
        }
        Random random = new Random();

        boolean sentHasBothNotInterested = false;
        //if both this peer and the peer it is connected to have all pieces, send not interested message

        while (!peerConnection.socket.isClosed()) {
            //check to see if peerConnection.socket is closed
            try {
                int sleepTime = random.nextInt(200) + 100;
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // First priority should be to send a request message if there is a piece that is not currently being requested
            // Each peerConnection should be able to have 1 outstanding request at a time, meaning that the entire peerProcess
            // should be able to have 1 outstanding request per peerConnection, aka 1 outstanding request per peer
            // Second priority should be to respond to any queued responses (have message, etc.)
            // Third priority should be to send any requested pieces
            // This can be subject to change, but this is the current plan
            boolean hasOutstandingRequest = peerConnection.currentlyRequestedPiece.get() != -1;
            boolean hasQueuedResponses = !peerConnection.sendResponses.isEmpty();
            boolean hasAllPieces = peerConnection.hostProcess.hasAllPieces.get();
            boolean peerHasAllPieces = peerConnection.peerHasAllPieces.get();
            boolean peerHasAnyPiecesWeDont = peerConnection.peerHasAnyPiecesWeDont();
            boolean hasChokeAndInterestedMessages = peerConnection.chokeAndInterestedMessages.isEmpty();

            if(!hasChokeAndInterestedMessages) {
                // Send out a queued choke or unchoke message
                byte[] chokeOrUnchokeMessage = peerConnection.chokeAndInterestedMessages.remove();
                try {
                    if(peerConnection.socket.isClosed()) {
                        break;
                    }
                    sendMessage(chokeOrUnchokeMessage);
                    peerProcess.printDebug("Sent message to peer (choke or unchoke)");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            //Check for conditions to send outstanding request
            else if(!hasOutstandingRequest && peerHasAnyPiecesWeDont) {
                //This branch means that if there are any pieces that the peer has that this peer doesn't have, request one of them
                //This branch should only be taken if there is not already an outstanding request
                List<Integer> eligiblePieces = new ArrayList<>();
                //List is used to randomly request a piece rather than just doing it in order
                for (int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                    // Check if the piece is neither downloaded nor currently being requested by this peer,
                    // and make sure that sendResponses is empty
//                    if ((peerConnection.hostProcess.pieceMap.get(i) == null || (peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.DOWNLOADED &&
//                                    peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.REQUESTING))
//                            && peerConnection.sendResponses.isEmpty()) {
                    if (peerConnection.isPieceIndexEligible(i)) {
                        eligiblePieces.add(i); // Add eligible piece index to the list
                    }
                }

                if (!eligiblePieces.isEmpty()) {
                    // Randomly select a piece from the eligible pieces
                    int randomIndex = new Random().nextInt(eligiblePieces.size());
                    int selectedPieceIndex = eligiblePieces.get(randomIndex);
                    peerConnection.currentlyRequestedPiece.set(selectedPieceIndex);
                    peerProcess.printDebug("Randomly added piece " + selectedPieceIndex + " to requested pieces");
                }
                else {
                    peerProcess.printDebug("No eligible pieces to request");
                    //This should in theory never happen, but if it does, it means that the peer has all the pieces that this peer has
                    //and this peer has all the pieces that the peer has, but logically this can't happen
                }
            }
            else if (hasQueuedResponses) {
                // Send out a queued response that ReceivedHandler has queued, this can be a have message, a piece message, or whatever
                byte[] pieceIndex = peerConnection.sendResponses.remove();
                try {
                    if(peerConnection.socket.isClosed()) {
                        break;
                    }
                    sendMessage(pieceIndex);
                    peerProcess.printDebug("Sent message to peer (have)");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if (hasOutstandingRequest) {
                // Send out the outstanding request
                peerProcess.printDebug("Sending request message");
                int pieceIndex = peerConnection.currentlyRequestedPiece.get();
                byte[] message = Message.generateRequestMessage(pieceIndex);
                try {
                    //Checks to see if a piece is already requested from this peer
                    //Check if socket is closed
                    if(peerConnection.socket.isClosed()) {
                        break;
                    }
                    sendMessage(message);
                    peerConnection.hostProcess.pieceMap.put(pieceIndex, peerProcess.pieceStatus.REQUESTING);
                    peerProcess.printDebug("Sent message to peer (request)");
                } catch (IOException e) {
                    peerProcess.printError("Error sending request message to peer: " + e.getMessage());
                }
                peerConnection.currentlyRequestedPiece.set(-1);
            }
            else if (hasAllPieces && peerHasAllPieces){
                // Send not interested message if both peers have all pieces
                if(!sentHasBothNotInterested) {
                    byte[] notInterestedMessage = Message.generateNotInterestedMessage();
                    try {
                        sendMessage(notInterestedMessage);
                        peerProcess.printDebug("Sent message to peer (not interested)");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    sentHasBothNotInterested = true;
                }
                //if both peers have all pieces, check to see if all peers have all pieces
                // and if so, close the connection
                boolean allPeersHaveWholeFile = true;
                for (Map.Entry<Integer, Boolean> entry : peerConnection.hostProcess.peerHasWholeFile.entrySet()) {
                    if (!entry.getValue()) {
                        allPeersHaveWholeFile = false;
                        break;
                    }
                }
                if (allPeersHaveWholeFile) {
                    if (peerConnection.sendResponses.isEmpty()) {
                        //System.err.println("Host " + peerConnection.hostProcess.selfPeerId + " has all pieces and detected that all peers have all pieces");
                        peerConnection.hostProcess.close();
                        break; //I think continue and break here would have the same effect since the loop checks for the socket being closed at the beginning
                        // continue worked here first try so I'm not going to change it
                    }
                    try {
                        sendMessage(peerConnection.sendResponses.remove());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    // System.out.println("Both peers have all pieces, closing connection");
                    //peerConnection.close();
                }
            }
        }
    }

    boolean checkForPieces() {
        if(peerConnection.hostProcess.pieceMap == null) {
            return false;
        }
        if(peerConnection.hostProcess.hasAllPieces.get()) {
            return true;
        }
        for (int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
            if (peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.EMPTY && peerConnection.hostProcess.pieceMap.get(i) != null) {
                return true;
            }
        }
        return false;
    }
    void sendMessage(byte[] msg) throws IOException{
        //stream write the message
        lastMessageTime = Instant.now();
        if(peerConnection.socket.isClosed() || peerConnection.out == null) {
            return;
        }

        try {
            peerConnection.out.write(msg);
            peerConnection.out.flush();
        } catch (SocketException e) {
            //e.printStackTrace();
            //The socket is closed, so don't do anything
        }


    }

}
