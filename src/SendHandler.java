import java.io.IOException;
import java.nio.ByteBuffer;

public class SendHandler extends Thread {
    PeerConnection peerConnection;
    SendHandler(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }
    @Override
    public void run() {
        System.out.println("Starting send handler for peer " + peerConnection.peerId);
        int numMessages = 0;
        while (true) {
            if(!peerConnection.sendResponses.isEmpty()) {
                //Send have message
                int pieceIndex = peerConnection.sendResponses.remove();
                byte[] message = Message.generateHaveMessage(pieceIndex);
                try {
                    sendMessage(message);
                    System.out.println("Sent message to peer " + peerConnection.peerId);
                    numMessages++;
                    System.out.println("Number of messages sent to peer " + peerConnection.peerId + ": " + numMessages);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            boolean pieceWeHaveButTheyDont = false;
            for(int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                boolean hasPiece = false;
                if(peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.EMPTY) {
                    hasPiece = true;
                }
                if(hasPiece) {
                    //Check to see if other peer has piece
                    if (peerConnection.pieceMap.get(i) == peerProcess.pieceStatus.EMPTY) {
                        pieceWeHaveButTheyDont = true;
                        //Send interested message
//                    byte[] message = Message.generateInterestedMessage();
//                    sendMessage(message);
//                    System.out.println("Sent message to peer " + peerConnection.peerId);
//                    numMessages++;
//                    System.out.println("Number of messages sent to peer " + peerConnection.peerId + ": " + numMessages);
                    }
                }
            }
            if(!pieceWeHaveButTheyDont) {
                return;
            }
            for(int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                boolean hasPiece = false;
                if(peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.EMPTY) {
                    hasPiece = true;
                }
                if(hasPiece) {
                    //Check to see if other peer has piece
                    if (peerConnection.pieceMap.get(i) == peerProcess.pieceStatus.EMPTY) {
                        peerConnection.requestedPieces.add(i);
                    }
                }
            }
            //Check peer's bitfield to see if there are any pieces that we don't have
            //If there are, send an interested message
            try {
                // Wait 1 second
                Thread.sleep(10000);
//                byte[] message = Message.generateBitmapMessage(peerConnection.hostProcess.pieceMap, peerConnection.commonCfg.numPieces);
//                sendMessage(message);
//                System.out.println("Sent message to peer " + peerConnection.peerId);
//                numMessages++;
//                System.out.println("Number of messages sent to peer " + peerConnection.peerId + ": " + numMessages);
                //Send requestedPiece top of queue
                int requestedPiece = peerConnection.requestedPieces.remove();
                byte[] piece = peerConnection.hostProcess.pieceData.get(requestedPiece);
                byte[] message = Message.generatePieceMessage(piece);
                byte[] lengthAndType = Message.generateHeaderAndMessageType(message.length, MsgType.piece);
//                int messageLength = message.length;
//                byte[] messageLengthBytes = ByteBuffer.allocate(4).putInt(messageLength).array();
//                System.out.println("Sending piece " + requestedPiece + " to peer " + peerConnection.peerId);
//                System.out.println(message.length);
//                byte[] overallMessage = new byte[messageLength + 4];
//                System.arraycopy(messageLengthBytes, 0, overallMessage, 0, 4);
//                System.arraycopy(message, 0, overallMessage, 4, messageLength);
//                Message.Interpretation interpretation = Message.msgInterpret(overallMessage);
//                System.out.println("Message type: " + interpretation.Msg);
//                System.out.println("Payload length: " + interpretation.payloadLength);
//                System.out.println("Payload: " + interpretation.messagePayload);
                byte[] overallMessage = new byte[lengthAndType.length + message.length+4]; //+ 4 for the 4-byte piece index field
                System.arraycopy(lengthAndType, 0, overallMessage, 0, lengthAndType.length);
                System.arraycopy(message, 0, overallMessage, lengthAndType.length, message.length);
                byte[] pieceIndex = ByteBuffer.allocate(4).putInt(requestedPiece).array();
                System.arraycopy(pieceIndex, 0, overallMessage, lengthAndType.length + message.length, 4);
                byte[] firstFourBytes = new byte[4];
                System.arraycopy(overallMessage, 0, firstFourBytes, 0, 4);
                int test = (int) ByteBuffer.wrap(firstFourBytes).getInt();
                byte test2 = overallMessage[4];
                Message.Interpretation interpretation = Message.msgInterpret(overallMessage);
                System.out.println("Sending piece " + requestedPiece + " to peer " + peerConnection.peerId);

                sendMessage(overallMessage);
                // Process the message here
            } catch (Exception e) {
                return;
            }

        }
    }
    void sendMessage(byte[] msg) throws IOException{
        //stream write the message
        peerConnection.out.write(msg);
        peerConnection.out.flush();

    }

}
