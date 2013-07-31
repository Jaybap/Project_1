import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.BitSet;

/*
 * This class contains all the static messages that peers will communicate 
 * with one another.
 * 
 * @author Alex DeOliveira  [126-00-8635]
 * @author Jason Baptista   [126-00-4630]
 * @author Elizabeth Sanz   [127-00-8628]
 * @version "project01"
 */

public class Messages {

    /**
     * Set the byte arrays for the static peer messages
     */
    final static byte[] keep_alive = {0, 0, 0, 0};
    final static byte[] choke = {0, 0, 0, 1, 0};
    final static byte[] unchoke = {0, 0, 0, 1, 1};
    final static byte[] interested = {0, 0, 0, 1, 2};
    final static byte[] uninterested = {0, 0, 0, 1, 3};
    final static byte[] have = {0, 0, 0, 5, 4};
    final static byte[] request = {0, 0, 1, 3, 6};
    final static byte[] piece = {0, 0, 0, 9, 7};
    final static byte[] empty_bitfield = {0, 0, 0, 2, 5, 0};

    
    /**
     * Message: KEEP ALIVE, used to keep connection alive
     * @param client2peer a DataOutputStream used to write the messages
     * @return boolean, true if the keep_alive message has been sent
     *         otherwise false
     */
    public static boolean keepAlive(DataOutputStream client2peer) {
        try {
            client2peer.write(keep_alive);
            client2peer.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Message: INTERESTED, client is interested in connecting with peer
     * @param peerSocket a socket connect that was established for connection
     * @param client2peer a DataOutputStream used to write the messages
     * @param peer2client a DataInputStream used to read messages from 
     * @return boolean, true if interested message was sent, false otherwise
     */
    public static boolean interested(Socket peerSocket, DataOutputStream client2peer, DataInputStream peer2client) {
        try {
            client2peer.write(interested);
            client2peer.flush();
            int length = peer2client.readInt();
            if ((int) peer2client.readByte() == 1) {
                System.out.println("Unchoke received");
            } else {
                return false;
            }
            return true;
        } catch (IOException e) {
            terminateSocketConnections(peerSocket, client2peer, peer2client);
            System.err.println("ERROR: Unable to send interested message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send interested message. ");
            return false;
        }
    }

    /**
     * Message: Uninterested, client is not interested from connecting with peer
     * @param peerSocket a socket connect that was established for connection
     * @param client2peer a DataOutputStream used to write the messages
     * @param peer2client a DataInputStream used to read messages from 
     * @return boolean, true if uninterested message was sent, false otherwise
     */
    public static boolean uninterested(Socket peerSocket, DataOutputStream client2peer, DataInputStream peer2client) {
        try {
            client2peer.write(uninterested);
            client2peer.flush();

            //print statement
            //System.out.println("Sent uninterested message to " + peerID);

            /* variable not used here */
            //peerInterested = false;
            
            return true;
        } catch (IOException e) {
            terminateSocketConnections(peerSocket, client2peer, peer2client);
            System.err.println("ERROR: Unable to send uninterested message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send uninterested message. ");
            return false;
        }
    }

    /**
     * Message: CHOKE, peer cannot communicate with client
     * @param client2peer a DataOutputStream used to write the messages
     * @return boolean, true if choke message was sent, false otherwise
     */
    public static boolean choke(DataOutputStream client2peer) {
        try {
            client2peer.write(choke);
            client2peer.flush();

            //System.out.println("Sent choke message to " + peerID);

            /* boolean not used */
            //peerChoking = true;
            
            return true;
        } catch (IOException e) {
            //terminateSocketConnections();
            System.err.println("ERROR: Unable to send choke message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send choke message. ");
            return false;
        }
    }

    /**
     * Message: UNCHOKE, peer can communicate with client
     * @param peerSocket a socket connect that was established for connection
     * @param client2peer a DataOutputStream used to write the messages
     * @param peer2client a DataInputStream used to read messages from 
     * @return boolean, true if unchoke message was sent, false otherwise
     */
    public static boolean unchoke(Socket peerSocket, DataOutputStream client2peer, DataInputStream peer2client) {
        try {
            client2peer.write(unchoke);
            client2peer.flush();

            //print statement
            //System.out.println("Sent unchoke message to " + peerID);
            
           // Variables not used here
           // peerChoking = false;
            return true;
        } catch (IOException e) {
            terminateSocketConnections(peerSocket, client2peer, peer2client);
            System.err.println("ERROR: Unable to send unchoke message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send choke message. ");
            return false;
        }
    }

    /**
     * Message: Have, to notify other peers that one has completed downloading a piece
     * @param peerSocket a socket connect that was established for connection
     * @param client2peer a DataOutputStream used to write the messages
     * @param peer2client a DataInputStream used to read messages from 
     * @param piece_index the index at which the last piece was downloaded
     * @return boolean, true if have message was sent, false otherwise
     */
    public static boolean have(Socket peerSocket, DataOutputStream client2peer, DataInputStream peer2client, int piece_index) {
        try {
            if (!peerSocket.isClosed()) {
                ByteBuffer hByteBuffer = ByteBuffer.allocate(9);
                hByteBuffer.put(new byte[]{0, 0, 0, 5, 4});
                hByteBuffer.putInt(piece_index);
                client2peer.write(hByteBuffer.array());
                client2peer.flush();

                //System.out.println("Have message for piece " + piece_index + " sent to peer " + peerID);
                return true;
            } else {
                //System.err.println("Socket closed at " + peerID);
                return false;
            }
        } catch (IOException e) {
            terminateSocketConnections(peerSocket, client2peer, peer2client);
            //System.err.println("ERROR: Unable to send have message to " + peerID);
            return false;
        } catch (Exception e) {
            //System.err.println("ERROR: Unable to send have message to " + peerID);
            return false;
        }
    }

    /**
     * Message: Request, used to send other peer a request for a piece
     * @param position integer specifying the zero-based piece index
     * @param start integer specifying the zero-based byte offset within the piece
     * @param size integer specifying the requested length
     * @param peerSocket a socket connect that was established for connection
     * @param client2peer a DataOutputStream used to write the messages
     * @param peer2client a DataInputStream used to read messages from 
     * @return boolean, true if request message was sent, false otherwise
     */
    public static boolean request(int position, int start, int size, DataOutputStream client2peer, DataInputStream peer2client, Socket peerSocket) {
        try {
            ByteBuffer rByteBuffer = ByteBuffer.allocate(17);
            rByteBuffer.put(new byte[]{0, 0, 0, 13, 6});
            rByteBuffer.putInt(position);
            rByteBuffer.putInt(start);
            rByteBuffer.putInt(size);
            client2peer.write(rByteBuffer.array());
            client2peer.flush();

            System.out.println("Sent request message. ");
            return true;
        } catch (IOException e) {
            terminateSocketConnections(peerSocket, client2peer, peer2client);
            System.err.println("ERROR: Unable to sent request message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to sent request message. ");
            return false;
        }
    }

    /**
     * Message: Piece
     * @param peerSocket a socket connect that was established for connection
     * @param client2peer a DataOutputStream used to write the messages
     * @param index integer specifying the zero-based piece index
     * @param begin integer specifying the zero-based byte offset within the piece
     * @param block block of data; subset of piece
     * @return boolean, true if piece message was sent, false otherwise
     */
    public static synchronized boolean piece(Socket peerSocket, DataOutputStream client2peer, int index, int begin, byte[] block) {
        try {
            ByteBuffer piece_buffer = ByteBuffer.allocate(13 + block.length);
            piece_buffer.putInt(9 + block.length);
            piece_buffer.put((byte) 7);
            piece_buffer.putInt(index);
            piece_buffer.putInt(begin);
            piece_buffer.put(block);
            client2peer.write(piece_buffer.array());
            client2peer.flush();
            System.out.println("Piece message sent");
            return true;
        } catch (IOException ioe) {
            closeConnection(peerSocket);
            System.err.println(ioe.getMessage());
            System.err.println("COULD NOT SEND PIECE MESSAGE TO PEER!");
            return false;
        } catch (Exception ioe) {
            System.err.println(ioe.getMessage());
            System.err.println("COULD NOT SEND PIECE MESSAGE TO PEER!");
            return false;
        }
    }
    
    
    
    
    
    
    
    
    
    
    
     /**
     * Method: closeConnection, used to close peer socket
     */
    
    private static void closeConnection(Socket peerSocket) {
        try {
            peerSocket.close();
        } catch (IOException ex) {
            
        }
    }
    
    /**
     * Method: Will terminate all socket connections
     */
    public static void terminateSocketConnections(Socket peerSocket, DataOutputStream client2peer, DataInputStream peer2client) {
        try 
        {
            if (!peerSocket.isClosed()) {
                peerSocket.close();
                client2peer.close();
                peer2client.close();
            }
        } 
        catch (Exception e) 
        {
            System.err.println("ERROR: Could not terminate open socket connections. ");
        }
    }
        
}
