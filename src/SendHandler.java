import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

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
        while (true) {
            try {
                Thread.sleep(500);
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
                System.out.println("Peer has all pieces, requesting pieces");
                //If all pieces have been downloaded, respond to queue of requests
                //If no requests, I guess just busy wait?
                //Queue requests to send
                for(int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                    if(peerConnection.peerPieceMap.get(i) == peerProcess.pieceStatus.DOWNLOADED && (peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.DOWNLOADED || peerConnection.hostProcess.pieceMap.get(i) == null)) {
                        peerConnection.requestedPieces.add(i);
                        System.out.println("Added piece " + i + " to requested pieces");
                    }
                    else {
                        System.out.println("Did not add piece " + i + " to requested pieces");
                    }
                }
            } else {
                //System.out.println("Uh oh");
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
