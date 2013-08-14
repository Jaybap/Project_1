import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.BitSet;
import java.security.MessageDigest;

/**
 * This class consists of methods that each peer will be executing, such as 
 * building, sending, and verifying handshakes, receiving and sending bitfields,
 * and checking SHA. 
 * Each peer will be running on their own thread, sending their messages, 
 * receiving responses, downloading, and uploading torrents.
 * 
 * @author Alex DeOliveira  [126-00-8635]
 * @author Jason Baptista   [126-00-4630]
 * @author Elizabeth Sanz   [127-00-8628]
 * @version "project02"
 */
public class Peer extends Thread{

    /* Peer Information */
    public byte[] peerID;
    public String peerIP = null;
    public int peerPort = 0;
    public Socket peerSocket = null;
    public BitSet peerbitfield = null;
	public ArrayList<Integer> downloadList;
	public String output = "";

	public int ThreadID;
    
    /* Connection Information */
    public DataOutputStream client2peer = null;
    public DataInputStream peer2client = null;
    
    /* BOOLEAN Connection status */
    public boolean connected = false;
    public boolean handshakeConfirmed = false;
    public boolean[] booleanBitField = null;
    public boolean incoming = false;
    private boolean receiveBitfield;
    private boolean is_interested = false;
    private boolean keepalive_sent = false;
    
    /* BOOLEAN Peer Status */
    public boolean am_alive = false;
	public boolean start = false;
    public boolean isClientchoking = false;
    public boolean client_interested = false;
    public boolean peerInterested;
    public static boolean peerChoking;
    
    /* Messages */
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
   
    // =========================================================================
    // PEER CONSTRUCTOR 								
    // =========================================================================
    public Peer(String IpNum, int peerPortNum, boolean incoming, String id, int tid) {
        if (!(id == null) && !(id.equals(""))) 
        {
            this.peerID = id.getBytes();
        }
        this.peerIP = IpNum;
        this.peerPort = peerPortNum;
        this.booleanBitField = new boolean[RUBTClient.numPieces];
		this.downloadList = new ArrayList<Integer>();
        this.incoming = incoming;
		ThreadID = tid;
		am_alive = true;
        start();
    }

    // =========================================================================
    // METHODS  									
    // =========================================================================
    /**
     * This method adds a piece to the download list
     * @param newPiece is the piece that is to be adding to the download list
     */
	public void addPieceToDownloadList(int newPiece)
	{
		downloadList.add(new Integer(newPiece));
	}

    /**
     * This method creates/builds a handshake
     *
     * @param localPeerID: ID of local peer
     * @param infoHash: the 20 byte SHA1 hash of the bencoded form of the 
     *                  info value from the metainfo file
     * @return handshakeBytes a byte array containing info for the handshake
     * @throws UnsupportedEncodingException
     */
    public static byte[] buildHandshake(String localPeerID, ByteBuffer infoHash) {

        /* Variables */
        int i = 0;
        byte[] handshakeBytes;

        /* Create handshake byte array */
        handshakeBytes = new byte[68];

        /* Begin byte array with byte "nineteen" */
        handshakeBytes[i] = 0x13; //decimal: 19
        i++;

        try 
        {
            /* Put "BitTorrent protocol"-byte array */
            byte[] btBytes = {'B', 'i', 't', 'T', 'o', 'r', 'r', 'e', 'n', 't',
                ' ', 'p', 'r', 'o', 't', 'o', 'c', 'o', 'l'};
            System.arraycopy(btBytes, 0, handshakeBytes, i, btBytes.length);
            i = i + btBytes.length;

            /* Put zero-byte array */
            byte[] zeroBytes = new byte[8];
            System.arraycopy(zeroBytes, 0, handshakeBytes, i, zeroBytes.length);
            i = i + zeroBytes.length;

            /* Put 20-byte SHA-1 hash */
            System.arraycopy(infoHash.array(), 0, handshakeBytes, i, infoHash.array().length);
            i = i + infoHash.array().length;

            /* Put peer id (generated by client) */
            System.arraycopy(localPeerID.getBytes("ASCII"), 0, handshakeBytes, i, localPeerID.getBytes("ASCII").length);
        } 
        catch (UnsupportedEncodingException e) 
        {
            System.err.println("ERROR: Could not complete handshake. ");
            e.printStackTrace();
        }
        return handshakeBytes;
    }

    /**
     * This method sends handshake
     *
     * @param   localPeerID: ID of local peer
     * @param   infoHash the 20 byte SHA1 hash of the bencoded form of the 
     *                   info value from the metainfo file
     * @throws  IOException
     */
    public void sendHandshake(String localPeerID, ByteBuffer info_hash) {
        try 
        {
            byte[] handshake = buildHandshake(localPeerID, info_hash);
            if (!connected) {
                this.setPeerConnection();
            }
            client2peer.write(handshake);
            client2peer.flush();
        } 
        catch (IOException e) 
        {
			am_alive = false;
            System.err.println("Problem sending handshake to " + peerID);
            e.printStackTrace();
        }
    }

    /**
     * This method verifies handshake between peers
     *
     * @param   torrentInfoHash, a ByteBuffer that contains the info hash
     * @return  boolean, true if the handshake could be verified,
     *                   false otherwise 
     * @throws  Exception
     */
    public boolean verifyHandshake(ByteBuffer torrentInfoHash) {
        /* Variables */
        int index = 0;
        byte[] trueInfoHash = torrentInfoHash.array();
        byte[] handshakeInfoHash = new byte[20];
        byte[] handshakeResponse = new byte[68];

        /* Read response */
        try {
            peer2client.read(handshakeResponse);

            /* Extract the peer id from the handshake response */
            byte[] buffer = new byte[20];
            //System.arraycopy(handshakeResponse, handshakeResponse.length - 21, buffer, 0, 20);
            //this.setPeerID(buffer);

            /* Extract info hash from handshake response */
            System.arraycopy(handshakeResponse, 28, handshakeInfoHash, 0, 20);

            /*  Verify if torrent info hash and handshake info hash are the identical */
            while (index < 20) 
            {
                if (handshakeInfoHash[index] != trueInfoHash[index]) {
                    peerSocket.close();
					am_alive = false;
                    return false;
                } else {
                    ++index;
                }
            }
        } 
        catch (Exception e) 
        {
			am_alive = false;
            System.err.println("Could not read handshakeResponse. ");

        }
        handshakeConfirmed = true;
        return true;
    }

    /**
     * Function will compute the SHA-1 hash of a piece and compare it to the
     * SHA-1 from the meta-info torrent file
     *
     * @param   piece The byte array of the piece which was downloaded from the
     *          peer
     * @param   index The index number of the piece.
     * @return  True if the piece matches the SHA-1 hash from the meta-info
     *          torrent file otherwise false.
     */
    public boolean verifySHA(byte[] piece, int index) {
        MessageDigest digest = null;
        try 
        {
            digest = MessageDigest.getInstance("SHA");
        } 
        catch (Exception e) 
        {
            output += "\n"+ ("Bad SHA algorithm");
            return false;
        }
        byte[] hash = digest.digest(piece);
        byte[] info_hash = RUBTClient.torrent.piece_hashes[index].array();
        for (int i = 0; i < hash.length; i++) {
            if (hash[i] != info_hash[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Method: Running the program
     */
    public void run()
	{
		/* Set up connection with Peer */
		setPeerConnection();

		/* Establish handshake */
		sendHandshake(RUBTClient.peerID, RUBTClient.torrent.info_hash);
		output += "\n"+ ("Handshake sent");

		/* Receive and verify handshake */
		if (!verifyHandshake(RUBTClient.torrent.info_hash))
		{
			am_alive = false;
			System.err.println("ERROR: Unable to verify handshake. ");
		}
		else
		{
			//print statement
			output += "\n"+ ("Attempting to Download");
			
			int len = getPeerResponseInt();
			output += "\n"+ (len);
			byte message = getPeerResponseByte();
			output += "\n"+ (message);

			/* Capture the peer response (bits) */
			byte[] peerbits = getPeerResponse(len - 1);
			receiveBitfield(peerbits);
			output += "\n"+ (peerbitfield.toString());

			// wait until told to start downloading. Rarity of the pieces must be determined before attempting to download.
			while(start)
			{
				try
				{
					Thread.sleep(5000);
				}
				catch(Exception e)
				{
					//System.err.println("Your thread is having trouble sleeping give it some sleeping pills please.");
				}
			}

			/* Check to see if interested or uninterested */
			for (int i = 0; i < RUBTClient.getNumPieces(); i++) {
				/*When am I interested?: 
				 * 1) peerbitfield contains piece client does not have 
				 * 
				 * When am I UNinterested?
				 * 1) Neither client or peer has piece
				 * 2) Client and peer has piece
				 * 3) Client has piece and peer does not 
				 **/
				if ((peerbitfield.get(i) == true) && (RUBTClient.getBitfield().get(i) == false)) {
					output += "\n"+ ("Interested in downloading with peer: " + peerIP);
					is_interested = Messages.interested(peerSocket, client2peer, peer2client);
					break;
				} else if ((peerbitfield.get(i) == false) && (RUBTClient.getBitfield().get(i) == false)) {
					output += "\n"+ ("UNinterested in downloading with peer: " + peerIP);
					is_interested = Messages.uninterested(peerSocket, client2peer, peer2client);
				}
			}

			int numBlks = RUBTClient.numBlkPieceRatio;

			while (am_alive && is_interested)
			{
				ArrayList<Integer> threadProtect = new ArrayList<Integer>();
				while(downloadList.size() == 0)
				{
					try
					{
						Thread.sleep(20000);
					}
					catch(InterruptedException e)
					{}
				}
				synchronized(downloadList)
				{
					threadProtect.addAll(downloadList);
					downloadList.clear();
				}

				for(Integer piecenum : threadProtect)
				{
					int i = piecenum.intValue();
					ByteArrayOutputStream currentPiece = new ByteArrayOutputStream();
					output += "\n"+ ("Request Piece " + i);
					if (i == RUBTClient.numPieces - 1)
					{
						numBlks = (int) Math.ceil((double) RUBTClient.lastPieceSize / (double) RUBTClient.blockLength);
						output += "\n"+ ("Blocks for last piece " + numBlks);
					}
					/* blocks */
					for (int j = 0; j < numBlks; j++)
					{
						output += "\n"+ ("Request Block " + j);
						if (j == numBlks - 1) {
							if (i == RUBTClient.numPieces - 1)
							{
								Messages.request(i, j * RUBTClient.blockLength, RUBTClient.lastBlkSize, client2peer, peer2client, peerSocket);
							}
							else
							{
								Messages.request(i, j * RUBTClient.blockLength, RUBTClient.torrent.piece_length - (j * RUBTClient.blockLength), client2peer, peer2client, peerSocket);
							}
						}
						else
						{
							Messages.request(i, j * RUBTClient.blockLength, RUBTClient.blockLength, client2peer, peer2client, peerSocket);
						}
						int length = getPeerResponseInt();
						if (length == 0)
						{
							output += "\nSend Keep Alive";
							Messages.keepAlive(client2peer);
							length = getPeerResponseInt();
						}
						output += "\n"+ ("Length of the last read int is : " + length);
						byte[] block = new byte[length - 9];
						output += "\n"+ (", and the message type was : " + block[0]);
						System.arraycopy(getPeerResponse(length), 9, block, 0, length - 9);

						try
						{
							currentPiece.write(block);
						}
						catch (IOException e)
						{
							System.err.println("Error: Problem saving block " + j + " of piece " + i);
						}
					}
					if (verifySHA(currentPiece.toByteArray(), i))
					{
						output += "\nSaving piece " + i + "\n\n";
						DownloadManager.savePiece(currentPiece, i, this);
					}
				}//end of for loop
			}//end of while loop
		}//end of else
		am_alive = false;
    }//end of run
    
    
    
    // =========================================================================
    // BITFIELD METHODS                                                         
    // =========================================================================

    /**
     * This method receives the response bitfield 
     * @param length 
     */
    private void receiveBitField(int length) throws Exception {
        int l = 0;
        BitSet bs = new BitSet(RUBTClient.numPieces);
        byte bitfield_array[] = getPeerResponse(length - 1);

        byte bit_mask = (byte) 0x80;
        
        //reading in bitfield bit by bit
        for (int k = 0; k < bitfield_array.length; k++) 
        {
            byte bitfield = bitfield_array[k];

            for (int i = 0; i < 8; i++) 
            {
                if (l < RUBTClient.numPieces) {
                    bs.set(k * 8 + i, ((bitfield & bit_mask) == bit_mask) ? true : false);
                    bitfield = (byte) (bitfield >>> 2);
                    l++;
                }
            }//end of innter for loop
        }//end of outer for loop

        if (l == RUBTClient.numPieces) {
            //update the global bitset
            output += "\n"+ ("BitField successfully received");
            peerbitfield = bs;
            output += "\n"+ ("Bitfield received from " + this.peerID);
        } else {
            throw new Exception("BitField Error: Size does not match");
        }
    }
    
    /**
     * This method receives the response and stores the bitfield 
     * @param response, the byte array of the response received
     */
    public void receiveBitfield(byte[] response) {
        /* Variables */
        peerbitfield = new BitSet();
        int index = 0;
        
        /* Set bitfield */
        for (byte b : response) 
        {
            int h = (int) b;
            for (int j = 7; j >= 0; j--, index++) 
            {
                int shifted = h >> j;
                peerbitfield.set(index, ((shifted & 1) == 1));
            }
        }
        receiveBitfield = true;
    }

    /**
     * This method sends the bitfield. Upon success, it returns true. 
     * Upon failure, it returns false
     *
     * @param bitfield: The BitSet bitfield we are trying to send
     * @return boolean, to check/verify that the bitfield was sent
     * @throws Exception
     */
    public synchronized boolean sendBitfield(BitSet bitfield) {
        try {
            /*Variables */
            byte[] bitfield_bytes = new byte[(bitfield.length() - 1) / 8 + 1];

            /* Initialize to zero */
            for (int x = 0; x < bitfield_bytes.length; x++) {
                bitfield_bytes[x] = (byte) 0;
            }

            /* Converts BitSet to byte array */
            for (int i = 0; i < bitfield.length(); i++) {
                if (bitfield.get(i)) {
                    bitfield_bytes[i / 8] |= 1 << (7 - (i % 8));
                }
            }

            ByteBuffer bitfield_buffer = 
                    ByteBuffer.allocate(5 + bitfield_bytes.length);
            bitfield_buffer.putInt(1 + bitfield_bytes.length);
            bitfield_buffer.put((byte) 5);
            bitfield_buffer.put(bitfield_bytes);
            client2peer.write(bitfield_buffer.array());
            client2peer.flush();
            
            //print statement
            output += "\n"+ ("Bitfield message sent"); 
            return true;
        } 
        catch (Exception ioe) 
        {
            System.err.println(ioe.getMessage());
            System.err.println("Error: Could not send bitfield message.");
            return false;
        }
    }

    /**
     * This method sends the piece. 
     *
     * @param index: index at which to pick up the desired piece
     * @param offset: the offset needed to get block
     * @param length: the length of the block
     */
    private void sendPiece(int index, int offset, int length) {
        try {

            //peer already has the piece but still requesting it
            if (peerbitfield.get(index)) {
                System.err.println("Peer " + peerID + " violated protocol. Requested piece that it already has");
                this.suicide();
                return;
            }
            //if we have the piece
            if (RUBTClient.Bitfield.get(index)) {
                byte[] block = new byte[length];
                System.arraycopy(RUBTClient.piecesDL[index].toByteArray(), offset, block, 0, length);
                //send the piece
                if (!Messages.piece(peerSocket, client2peer, index, offset, block)) {
                }
                output += "\n"+ ("Uploaded bytes to peer " + peerID + " for i=" + index + " o=" + offset);

            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.toString());
            System.err.println("Could not send i=" + index + " o=" + offset + " to peer " + peerID);
        }
    }

    /**
     * This method receives have. 
     */
    private void receiveHave() {
        int piece_index = getPeerResponseInt();
        output += "\n"+ ("Have message for piece " + piece_index + " received from peer " + peerID);

        if (piece_index < 0 || piece_index >= RUBTClient.numPieces) {
            output += "\n"+ ("Invalid have message received hence closing connection.");
            this.suicide();
        }


        //if peer has piece that not in global bit set and we are not interested then
        //send interested message
        if (!RUBTClient.Bitfield.get(piece_index) && !client_interested) {
            is_interested = Messages.interested(peerSocket, client2peer, peer2client);
        }

        if ((peerbitfield.get(piece_index))) {
            System.err.println("Peer " + peerID + " sent a have message for piece it already had before\n Violated protocol so closing connection");
            this.suicide();
        }

        peerbitfield.set(piece_index, true);

    }
   
    // =========================================================================
    // SET METHODS								
    // =========================================================================
   /**
     * This method sets the boolean "start" to true if set, otherwise, 
     * closes the peer connection
     * @param set 
     */
	public void setStart(boolean set)
	{
		if (set)
			start = true;
		else
			this.suicide();
	}

    /**
     * This method sets a connection via a socket for peer with given IP address
     * and Port number
     * 
     * @throws Exception
     */
    public void setPeerConnection() {
        try 
        {
            /* Create socket */
            this.peerSocket = new Socket(this.peerIP, this.peerPort);
            if (peerSocket != null) 
            {
                /* Store from peer to client */
                this.peer2client = new DataInputStream(this.peerSocket.getInputStream());
                /* Store from client to peer */
                this.client2peer = new DataOutputStream(this.peerSocket.getOutputStream());
                connected = true;
            } 
            else {
                connected = false;
            }
        } 
        catch (Exception e) 
        {
            System.err.println("ERROR: Could not create socket connection with "
                    + "IP: " + peerIP + " and Port: " + peerPort + "\n\n"+e+"\n\n");
        }
    }

    /** 
     * This method sets this peers ID to the given value  
     * @param newID
     */
    public void setPeerID(byte[] newID) {
        peerID = newID;
    }

    
    
    // =========================================================================
    // GET METHODS								
    // =========================================================================
    
    /**
     * This method is used to capture the byte array of the peer response  
     * 
     * @return the byte array of the peer response
     * @throws IOException
     * @throws Exception
     */
    public byte[] getPeerResponse(int length) {
        try 
        {
            byte[] peerResponse = new byte[length];
            peer2client.readFully(peerResponse);
            return peerResponse;
        } 
        catch (IOException e) 
        {
            terminateSocketConnections();
            output += "\n"+ ("first " + e.toString());
            System.err.println("ERROR: Unable to receive peer byte array response. ");
            return null;
        } 
        catch (Exception e) 
        {
            output += "\n"+ ("second " + e.toString());
            System.err.println("ERROR: Unable to receive peer byte array response. ");
            return null;
        }
    }

     /**
     * This method is used to capture the byte of the peer response  
     *
     * @return the byte of the peer response
     * @throws IOException
     * @throws Exception
     */
     public synchronized byte getPeerResponseByte() {
        try 
        {
            return peer2client.readByte();
        } 
        catch (IOException e) 
        {
            terminateSocketConnections();
            output += "\n"+ ("first " + e.toString());
            System.err.println("ERROR: Unable to receive peer byte response. ");
            return -1;
        } 
        catch (Exception e) 
        {
            output += "\n"+ ("second " + e.toString());
            System.err.println("ERROR: Unable to receive peer byte response. ");
            return -1;
        }
    }
    
    /**
     * This method is used to capture the length of the peer response  
     *
     * @return the length of the peer response
     * @throws IOException
     * @throws Exception
     */
    public synchronized int getPeerResponseInt() {
        try 
        {
            return peer2client.readInt();
        } 
        catch (IOException e) 
        {
            terminateSocketConnections();
            output += "\n"+ ("first " + e.toString());
            System.err.println("ERROR: Unable to receive peer int response. ");
            return -1;
        } 
        catch (Exception e) 
        {
            output += "\n"+ ("second " + e.toString());
            System.err.println("ERROR: Unable to receive peer int response. ");
            return -1;
        }
    }
    
    /**
     * This method returns the peer ID
     * @return peerID: The ID of the peer
     */
    public byte[] getPeerID() {
        return peerID;
    }

    /**
     * This method returns the peer IP address
     * @return peerIP: The IP address of the peer 
     */
    public String getPeerIP() {
        return peerIP;
    }

	public BitSet getBitField()
	{
		return peerbitfield;
	}

    /**
     * This method returns the peer port.
     * @return peerPort: The port in which the peer connects through 
     */
    public int getPeerPort() {
        return peerPort;
    }
    
    
    
    //=========================================================================
    // CLOSE
    //=========================================================================
    /**
     * Safely closes this PeerController
     */
    public void suicide() {
        output += "\n"+ ("Peer " + peerID + " is committing suicide");
        this.terminateSocketConnections();
        am_alive = false;
    }
     
    /*
    public void closePeerSocket() {
        try 
        {
            if (peerSocket != null) {
                peerSocket.close();
            }
        } catch (Exception e) 
        {
            System.err.println("ERROR: Could not close peer socket. ");
        }
    }
    * */
    
    /**
     * This method will close peer socket connections
     */
    private void closeConnection() {
        try {
            peerSocket.close();
        } catch (IOException e) {
            System.err.println("ERROR: Could not close connections. ");
        }
    }
    
    /**
     * This method will terminate all socket connections
     */
    public void terminateSocketConnections() {
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
