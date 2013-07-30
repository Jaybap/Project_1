import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.BitSet;

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
     * Message: KEEP ALIVE
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
     * Message: INTERESTED
     */
    public static boolean interested(DataOutputStream client2peer, DataInputStream peer2client) {
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
            //terminateSocketConnections();
            System.err.println("ERROR: Unable to send interested message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send interested message. ");
            return false;
        }
    }

    /**
     * Message: Uninterested
     */
    public static boolean uninterested(DataOutputStream client2peer) {
        try {
            client2peer.write(uninterested);
            client2peer.flush();

            //System.out.println("Sent uninterested message to " + peerID);

            /* variable not used */
            //peerInterested = false;
            
            return true;
        } catch (IOException e) {
            //terminateSocketConnections();
            System.err.println("ERROR: Unable to send uninterested message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send uninterested message. ");
            return false;
        }
    }

    /**
     * Message: CHOKE
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
     * Message: UNCHOKE
     */
    public static boolean unchoke(DataOutputStream client2peer) {
        try {
            client2peer.write(unchoke);
            client2peer.flush();

            /* boolean not used */
            //peerChoking = false;
            
            return true;
        } catch (IOException e) {
            //terminateSocketConnections();
            System.err.println("ERROR: Unable to send unchoke message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send choke message. ");
            return false;
        }
    }

    /**
     * Message: Have
     */
    public boolean have(Socket peerSocket, DataOutputStream client2peer, int piece_index) {
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
            //terminateSocketConnections();
            //System.err.println("ERROR: Unable to send have message to " + peerID);
            return false;
        } catch (Exception e) {
            //System.err.println("ERROR: Unable to send have message to " + peerID);
            return false;
        }
    }

    //Note: working on this
    public static boolean request(int position, int start, int size, DataOutputStream client2peer) {
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
            //terminateSocketConnections();
            System.err.println("ERROR: Unable to sent request message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to sent request message. ");
            return false;
        }
    }

    //Note: working on this
    public boolean piece() {
        return false;
    }    
        
}
