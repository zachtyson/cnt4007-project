import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

public class SendHandler extends Thread {
    PeerConnection peerConnection;
    SendHandler(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }
    @Override
    public void run() {
        System.out.println("Starting send handler for peer");
        boolean hasAnyPieces = checkForPieces();
        if (hasAnyPieces) {
            // If it has any pieces, send bitfield message
            int numBytes = (int) Math.ceil(peerConnection.commonCfg.numPieces / 8.0);
            System.out.println("Num bytes: " + numBytes);
            //byte[] headerAndType = Message.generateHeaderAndMessageType(numBytes,MsgType.bitfield);
            byte[] bitfieldMessage = Message.generateBitmapMessage(peerConnection.hostProcess.pieceMap, peerConnection.commonCfg.numPieces);

            try {
                //Print full message
                System.out.println("Message: " + Arrays.toString(bitfieldMessage));
                sendMessage(bitfieldMessage);
                System.out.println("Sent bitfield to peer");
            } catch (IOException e) {
                //e.printStackTrace();
            }
        }

        while (!peerConnection.socket.isClosed()) {
            //check to see if peerConnection.socket is closed
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            boolean noRequestedPieces = peerConnection.requestedPieces.isEmpty();
            boolean hasAllPieces = peerConnection.hostProcess.hasAllPieces.get();
            boolean peerHasAllPieces = peerConnection.peerHasAllPieces.get();
//            System.out.println("No requested pieces: " + noRequestedPieces);
//            System.out.println("Has all pieces: " + hasAllPieces);
//            System.out.println("Peer has all pieces: " + peerHasAllPieces);
            if (noRequestedPieces && !hasAllPieces && peerHasAllPieces) {
                //If all pieces have been downloaded, respond to queue of requests
                //If no requests, I guess just busy wait?
                //Queue requests to send
                //todo: only add one request at a time
                for (int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                    // Check if the piece is neither downloaded nor currently being requested by this peer,
                    // along with that, make sure that sendResponses is empty, requests one piece at a time
                    // this is so that each peer has a chance to request a piece from any other peer
                    // so basically this is picks a random piece to request
                    if ((peerConnection.hostProcess.pieceMap.get(i) == null ||
                            (peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.DOWNLOADED &&
                                    peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.REQUESTING))
                            && !peerConnection.requestedPieces.contains(i) && peerConnection.sendResponses.isEmpty()) {

                        peerConnection.requestedPieces.add(i);
                        peerConnection.hostProcess.pieceMap.put(i, peerProcess.pieceStatus.REQUESTING);
                        System.out.println("Added piece " + i + " to requested pieces");
                        break;
                    }
                }
            } else {
                boolean allPeersHaveWholeFile = true;
                for (Map.Entry<Integer, Boolean> entry : peerConnection.hostProcess.peerHasWholeFile.entrySet()) {
                    if (!entry.getValue()) {
                        allPeersHaveWholeFile = false;
                        break;
                    }
                }
                if (hasAllPieces && allPeersHaveWholeFile) {
                    if (peerConnection.sendResponses.isEmpty()) {
                        //System.err.println("Host " + peerConnection.hostProcess.selfPeerId + " has all pieces and detected that all peers have all pieces");
                        peerConnection.hostProcess.close();
                        continue; //I think continue and break here would have the same effect since the loop checks for the socket being closed at the beginning
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
//                if(hasAllPieces && allPeersHaveWholeFile) {
//                    break;
//                    // System.out.println("Both peers have all pieces, closing connection");
//                    //peerConnection.close();
//                }
                if (hasAllPieces && !peerHasAllPieces) {
                    //Check the request queue to see if there are any pieces that the peer has requested
                    //If so, send them
                }
            }
            //Prioritize sending have messages over sending requests and pieces
            if (!peerConnection.sendResponses.isEmpty()) {
                //Send piece message
                byte[] pieceIndex = peerConnection.sendResponses.remove();
                try {
                    sendMessage(pieceIndex);
                    System.out.println("Sent message to peer (have)");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (!peerConnection.requestedPieces.isEmpty()) {
                //Send piece message
                if (peerConnection.currentlyRequestedPiece.get() == -1) {
                    int pieceIndex = peerConnection.requestedPieces.remove();
                    byte[] message = Message.generateRequestMessage(pieceIndex);
                    try {
                        //Checks to see if a piece is already requested from this peer
                        sendMessage(message);
                        peerConnection.currentlyRequestedPiece.set(pieceIndex);
                        System.out.println("Sent message to peer");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            // Add logic to check for other types of messages to send
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
        peerConnection.out.write(msg);
        peerConnection.out.flush();

    }

}
