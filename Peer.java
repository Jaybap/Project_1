
import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.BitSet;

public class Peer {

    /**
     * It has methods to perform handshake, alive, interested, and other
     * messages.
     */
    /**
     * Peer Information
     */
    public String peerID = null;
    public String peerIP = null;
    public int peerPort = 0;
    public Socket peerSocket = null;
    public BitSet peerbitfield = null;
    /**
     * Connection Information
     */
    public DataOutputStream client2peer = null;
    public DataInputStream peer2client = null;
    /**
     * BOOLEAN Connection status
     */
    public boolean connected = false;
    public boolean handshakeConfirmed = false;
    public boolean[] booleanBitField = null;
    /**
     * BOOLEAN Peer Status
     */
    public boolean peerInterested;
    public boolean peerChoking;
    final static int KEY_CHOKE = 0;
    final static int KEY_UNCHOKE = 1;
    final static int KEY_INTERESTED = 2;
    final static int KEY_UNINTERESTED = 3;
    final static int KEY_HAVE = 4;
    final static int KEY_BITFIELD = 5;
    final static int KEY_REQUEST = 6;
    final static int KEY_PIECE = 7;
    final static int KEY_CANCEL = 8;
    final static int KEY_PORT = 9;
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

    /* ================================================================================ */
    /* 									Peer Constructor								*/
    /* ================================================================================ */
    public Peer(String IpNum, int peerPortNum) {
        this.peerID = ""; //not provided to us?
        this.peerIP = IpNum;
        this.peerPort = peerPortNum;
        this.booleanBitField = new boolean[RUBTClient.numPieces];
    }

    /* ================================================================================ */
    /* 										Methods										*/
    /* ================================================================================ */
    /**
     * METHOD: Create handshake
     */
    public static byte[] buildHandshake(String localPeerID, ByteBuffer infoHash) {
        /* Variables */
        int i = 0;
        byte[] handshakeBytes;

        /**
         * Create handshake byte array
         */
        handshakeBytes = new byte[68];

        /**
         * Begin byte array with byte "nineteen"
         */
        handshakeBytes[i] = 0x13; //decimal: 19
        i++;

        try {
            /**
             * Put "BitTorrent protocol"-byte array
             */
            byte[] btBytes = {'B', 'i', 't', 'T', 'o', 'r', 'r', 'e', 'n', 't',
                ' ', 'p', 'r', 'o', 't', 'o', 'c', 'o', 'l'};
            System.arraycopy(btBytes, 0, handshakeBytes, i, btBytes.length);
            i = i + btBytes.length;

            /**
             * Put zero-byte array
             */
            byte[] zeroBytes = new byte[8];
            System.arraycopy(zeroBytes, 0, handshakeBytes, i, zeroBytes.length);
            i = i + zeroBytes.length;

            /**
             * Put 20-byte SHA-1 hash
             */
            System.arraycopy(infoHash.array(), 0, handshakeBytes, i, infoHash.array().length);
            i = i + infoHash.array().length;

            /**
             * Put peer id (generated by client)
             */
            System.arraycopy(localPeerID.getBytes("ASCII"), 0, handshakeBytes, i, localPeerID.getBytes("ASCII").length);
        } catch (UnsupportedEncodingException e) {
            System.err.println("ERROR: Could not complete handshake. ");
            e.printStackTrace();
        }
        return handshakeBytes;
    }

    /**
     * METHOD: Send handshake
     */
    public void sendHandshake(String localPeerID, ByteBuffer info_hash) {
        try {
            byte[] handshake = buildHandshake(localPeerID, info_hash);
            if (!connected) {
                this.setPeerConnection();
            }
            client2peer.write(handshake);
            client2peer.flush();
        } catch (IOException e) {
            System.err.println("Problem sending handshake to " + peerID);
            e.printStackTrace();
        }
    }

    /**
     * METHOD: Verify handshake
     */
    public boolean verifyHandshake(ByteBuffer torrentInfoHash) {
        /* Variables */
        int index = 0;
        byte[] trueInfoHash = torrentInfoHash.array();
        byte[] handshakeInfoHash = new byte[20];
        byte[] handshakeResponse = new byte[68];

        /**
         * Read response
         */
        try {
            peer2client.read(handshakeResponse);

            /**
             * Extract peer ID from handshake response
             */
            //not yet
            /**
             * Extract info hash from handshake response
             */
            System.arraycopy(handshakeResponse, 28, handshakeInfoHash, 0, 20);

            /**
             * Verify if torrent info hash and handshake info hash are the
             * identical
             */
            while (index < 20) {
                if (handshakeInfoHash[index] != trueInfoHash[index]) {
                    peerSocket.close();
                    return false;
                } else {
                    ++index;
                }
            }
        } catch (Exception e) {
            System.err.println("Could not read handshakeResponse. ");

        }
        handshakeConfirmed = true;
        return true;
    }

    /**
     * METHOD: Close peer socket
     */
    public void closePeerSocket() {
        try {
            if (peerSocket != null) {
                peerSocket.close();
            }
        } catch (Exception e) {
            System.err.println("ERROR: Could not close peer socket. ");
        }
    }

    public void terminateSocketConnections() {
        try {
            if (!peerSocket.isClosed()) {
                peerSocket.close();
                client2peer.close();
                peer2client.close();
            }
        } catch (Exception e) {
            System.err.println("ERROR: Could not terminate open socket connections. ");
        }
    }
    /* ================================================================================ */
    /* 										Messages 									*/
    /* ================================================================================ */

    /**
     * Message: KEEP ALIVE
     */
    public boolean keepAlive() {
        try {
            client2peer.write(keep_alive);
            client2peer.flush();

            /* ================ */
            /* Print Statements */
            /* ================ */
            System.out.println("Sent keepAlive message to " + peerIP+":"+peerPort);

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Message: INTERESTED
     */
    public boolean interested() {
        try {
            client2peer.write(interested);
            client2peer.flush();
            System.out.println("Sent interested message to " + peerIP+":"+peerPort);
            int length = peer2client.readInt();
            if ((int) peer2client.readByte() == 1) {
                System.out.println("Unchoke received");
            } else {
                return false;
            }


            /* ================ */
            /* Print Statements */
            /* ================ */
           // System.out.println("Sent interested message to " + peerID);

            peerInterested = true;
            return true;
        } catch (IOException e) {
            terminateSocketConnections();
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
    public boolean uninterested() {
        try {
            client2peer.write(uninterested);
            client2peer.flush();

            /* ================ */
            /* Print Statements */
            /* ================ */
            System.out.println("Sent uninterested message to " + peerID);

            peerInterested = false;
            return true;
        } catch (IOException e) {
            terminateSocketConnections();
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
    public boolean choke() {
        try {
            client2peer.write(choke);
            client2peer.flush();

            /* ================ */
            /* Print Statements */
            /* ================ */
            System.out.println("Sent choke message to " + peerID);

            peerChoking = true;
            return true;
        } catch (IOException e) {
            terminateSocketConnections();
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
    public boolean unchoke() {
        try {
            client2peer.write(unchoke);
            client2peer.flush();

            /* ================ */
            /* Print Statements */
            /* ================ */
            System.out.println("Sent unchoke message to " + peerID);

            peerChoking = false;
            return true;
        } catch (IOException e) {
            terminateSocketConnections();
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
    public boolean have(int piece_index) {
        try {
            if (!peerSocket.isClosed()) {
                ByteBuffer hByteBuffer = ByteBuffer.allocate(9);
                hByteBuffer.put(new byte[]{0, 0, 0, 5, 4});
                hByteBuffer.putInt(piece_index);
                client2peer.write(hByteBuffer.array());
                client2peer.flush();

                /* ================ */
                /* Print Statements */
                /* ================ */
                System.out.println("Have message for piece " + piece_index + " sent to peer " + peerID);
                return true;
            } else {
                System.err.println("Socket closed at " + peerID);
                return false;
            }
        } catch (IOException e) {
            terminateSocketConnections();
            System.err.println("ERROR: Unable to send have message to " + peerID);
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send have message to " + peerID);
            return false;
        }
    }

    //Note: working on this
    public boolean request(int position, int start, int size) {
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
            terminateSocketConnections();
            System.err.println("ERROR: Unable to sent request message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to sent request message. ");
            return false;
        }
    }

    public void receiveBitfield(byte[] response) {
        peerbitfield = new BitSet();
        int index = 0;
        for (byte b : response) {
            int h = (int) b;
            for (int j = 7; j >= 0; j--, index++) {
                int shifted = h >> j;
                peerbitfield.set(index, ((shifted & 1) == 1));
            }
        }
    }

    //Note: working on this
    public boolean piece() {
        return false;
    }

    /* ================================================================================ */
    /* 									Set Methods										*/
    /* ================================================================================ */
    /**
     * METHOD: Create socket for peer with given IP and Port
     */
    public void setPeerConnection() {
        try {
            /**
             * Create socket
             */
            this.peerSocket = new Socket(this.peerIP, this.peerPort);
            if (peerSocket != null) {
                /**
                 * store from peer to client
                 */
                this.peer2client = new DataInputStream(this.peerSocket.getInputStream());

                /**
                 * store from client to peer
                 */
                this.client2peer = new DataOutputStream(this.peerSocket.getOutputStream());
                connected = true;
            } else {
                connected = false;
            }
        } catch (Exception e) {
            System.err.println("ERROR: Could not create socket connection with IP: " + peerIP + " and Port: " + peerPort);
        }
    }

    /* ================================================================================ */
    /* 									Get Methods										*/
    /* ================================================================================ */
    public String getPeerID() {
        return peerID;
    }

    public String getPeerIP() {
        return peerIP;
    }

    public int getPeerPort() {
        return peerPort;
    }

    /**
     * Method: get complete peer response
     */
    public byte[] getPeerResponse(int length) {
        try {
            byte[] peerResponse = new byte[length];
            peer2client.readFully(peerResponse);

            return peerResponse;
        } catch (IOException e) {
            terminateSocketConnections();
            System.err.println("ERROR: Unable to receive peer response. ");
            return null;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to receive peer response. ");
            return null;
        }
    }

    /**
     * Method: get byte response
     */
    public synchronized byte getPeerResponseByte() {
        try {
            return peer2client.readByte();
        } catch (IOException e) {
            terminateSocketConnections();
            System.err.println("ERROR: Unable to receive peer response. ");
            return -1;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to receive peer response. ");
            return -1;
        }
    }

    /**
     * Method: get int response
     */
    public synchronized int getPeerResponseInt() {
        try {
            return peer2client.readInt();
        } catch (IOException e) {
            terminateSocketConnections();
			System.out.println("first "+e.toString());
            System.err.println("ERROR: Unable to receive peer response. ");
            return -1;
        } catch (Exception e) {
			System.out.println("second "+e.toString());
            System.err.println("ERROR: Unable to receive peer response. ");
            return -1;
        }
    }

    /* ================================================================================ */
    /* 									Is Methods										*/
    /* ================================================================================ */
    public boolean isPeerConnected() {
        return connected;
    }
}
