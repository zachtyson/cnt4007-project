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
        //todo: currently how it works is that it requests all the pieces at once, but what it SHOULD do is
        //todo: request a piece, wait for it, send a has message, and then request another piece
        while (true) {
            //check to see if peerConnection.socket is closed
            if(peerConnection.socket.isClosed()) {
                break;
            }
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
            if(noRequestedPieces && !hasAllPieces && peerHasAllPieces) {
                //If all pieces have been downloaded, respond to queue of requests
                //If no requests, I guess just busy wait?
                //Queue requests to send
                for(int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                    if (peerConnection.peerPieceMap.get(i) == peerProcess.pieceStatus.DOWNLOADED) {
                        // Check if the piece is neither downloaded nor currently being requested by this peer,
                        // and also not already in the requested pieces set. Includes a null check.
                        if ((peerConnection.hostProcess.pieceMap.get(i) == null ||
                                (peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.DOWNLOADED &&
                                        peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.REQUESTING))
                                && !peerConnection.requestedPieces.contains(i)) {

                            peerConnection.requestedPieces.add(i);
                            peerConnection.hostProcess.pieceMap.put(i, peerProcess.pieceStatus.REQUESTING);
                            System.out.println("Added piece " + i + " to requested pieces");
                        }
                    }

                    else {
                        System.out.println("Did not add piece " + i + " to requested pieces");
                    }
                }
            } else {
                boolean allPeersHaveWholeFile = true;
                for(Map.Entry<Integer,Boolean> entry: peerConnection.hostProcess.peerHasWholeFile.entrySet()) {
                    if(!entry.getValue()) {
                        allPeersHaveWholeFile = false;
                        break;
                    }
                }
                if(hasAllPieces && allPeersHaveWholeFile) {
                    System.out.println(Arrays.toString(peerConnection.sendResponses.peek()));
                    try {
                        sendMessage(peerConnection.sendResponses.remove());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if(peerConnection.sendResponses.isEmpty()) {
                        break;
                    }
                    // System.out.println("Both peers have all pieces, closing connection");
                    //peerConnection.close();
                }
//                if(hasAllPieces && allPeersHaveWholeFile) {
//                    break;
//                    // System.out.println("Both peers have all pieces, closing connection");
//                    //peerConnection.close();
//                }
                if(hasAllPieces && !peerHasAllPieces) {
                    //Check the request queue to see if there are any pieces that the peer has requested
                    //If so, send them
                    if(!peerConnection.sendResponses.isEmpty()) {
                        //Send piece message
                        byte[] pieceIndex = peerConnection.sendResponses.remove();
                        try {
                            sendMessage(pieceIndex);
                            System.out.println("Sent message to peer");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            if (!peerConnection.requestedPieces.isEmpty()) {
                //Send piece message
                int pieceIndex = peerConnection.requestedPieces.remove();
                byte[] message = Message.generateRequestMessage(pieceIndex);
                try {
                    sendMessage(message);
                    System.out.println("Sent message to peer");
                } catch (IOException e) {
                    e.printStackTrace();
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
