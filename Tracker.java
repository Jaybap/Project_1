import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.*;

/**
 * Tracker Class Main Functions: 
 * 1) Store Tracker Information 
 * 2) Connect Client to Tracker through socket 
 * 3) Connect Client to Tracker through HTTP Connection 
 * 4) Receive Tracker Response 
 * 5) Decode Tracker Response 
 * 6) Extract Interval 
 * 7) Extract Peers 
 * 8) Create an ArrayList of Peers (in this case we are only connecting to one)
 */
public class Tracker extends Thread{

    /*  The Constant requestSize */
    public static final int requestSize = 16000;
    public static final char[] HEX_CHARS = 
    {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F'};
    
    /* Torrent Information: infoHash announceURL torrent_file_bytes piece_length
       piece_hashes */
    private static TorrentInfo torrentData;
    
    /* Client Information: destinationFile, bytesDownloaded, bytesUploaded,
       bytesRemaining, event; */
    private static RUBTClient client;
    
    /* Tracker Information */
    private static URL trackerUrl;
    private static String trackerIP;
    private static int trackerPort;
    private static int trackerInterval;
    private static boolean am_alive;
    
    /* Connection Information */
    private static URL requestedURL;
    private static ArrayList<String> peerIPList = new ArrayList<String>();
    static int listeningPort = -1;
   
    /* keyINTERVAL */
    public static final ByteBuffer keyINTERVAL =
            ByteBuffer.wrap(new byte[]{'i', 'n', 't', 'e', 'r', 'v', 'a', 'l'});
    
    /* keyPEERS */
    public static final ByteBuffer keyPEERS =
            ByteBuffer.wrap(new byte[]{'p', 'e', 'e', 'r', 's'});
    
    /* keyPEER_IP */
    public static final ByteBuffer keyPEER_IP =
            ByteBuffer.wrap(new byte[]{'i', 'p'});
    
    /* keyPEER_ID */
    public static final ByteBuffer keyPEER_ID =
            ByteBuffer.wrap(new byte[]{'p', 'e', 'e', 'r', ' ', 'i', 'd'});
    
    /* keyPEER_PORT */
    public static final ByteBuffer keyPEER_PORT =
            ByteBuffer.wrap(new byte[]{'p', 'o', 'r', 't'});
    
    /* keyFAILURE */
    public static ByteBuffer keyFAILURE =
            ByteBuffer.wrap(new byte[]{'f', 'a', 'i', 'l', 'u', 'r', 'e', ' ', 'r', 'e', 'a', 's', 'o', 'n'});

    /* ================================================================================ */
    /*  Tracker Constructor								*/
    /* ================================================================================ */
    Tracker(RUBTClient c, TorrentInfo t) {

        /* Fill in Client Information */
        client = c; 			

        /* Fill in Client Information */
        torrentData = t; 		 
        
        /* Fill in Tracker Information */
        trackerUrl = torrentData.announce_url; 				
        trackerIP = trackerUrl.getHost(); 					
        trackerPort = trackerUrl.getPort(); 				
        am_alive = true;
        start();
    }

    /**
     * Method: Starts up a Tracker thread that sleeps for the tracker interval amount of
     * time and then contacts the tracker to get an updated peers list
     */
    public void run() {
        while (am_alive) {
            try {
                if (am_alive) {
                    connect(client.bytesDownloaded, client.bytesUploaded, client.bytesRemaining, null);
					RUBTClient.startDownload = false;
                }
				Thread.sleep(trackerInterval * 1000);
            } catch (IOException e) {
                System.err.println("Caught IOException: " + e.getMessage());

            } catch (InterruptedException e) {
                System.err.println("Caught InterruptedException: " + e.getMessage());
            }
        }
    }

    /* ================================================================================ */
    /* METHODS                                                                          */
    /* ================================================================================ */
    
    /**
     * Method: Creates a connection with Tracker.
     *
     * @param bytesDown The amount of bytes downloaded
     * @param bytesUp The amount of bytes uploaded
     * @param bytesRemaining The amount of bytes remaining
     * @param event The state of download
     * @return a Map of the tracker's response
     * 
     */
    public synchronized Map connect(int bytesDown, int bytesUp, int bytesRemaining, String event) throws IOException {
        
        /* Variables */
        Socket trkSocket = null;
        URL trkURL = null;
        HttpURLConnection trkConnection = null;
        DataInputStream trackerData;
        int size;
        byte[] trkDataByteArray = null;
        Map<ByteBuffer, Object> trkMapResponse = null;

        /* Verify Tracker was initialized */
        if (trackerUrl == null) {
            System.err.println("Tracker was not created properly. ");
            return null;
        }

        /* Open socket in order to communicate with tracker */
        try {
            trkSocket = new Socket(trackerIP, trackerPort);
        } catch (Exception e) {
            System.err.println("ERROR: Unable to create socket at " + trackerIP + ":" + trackerPort);
            return null;
        }

        /* Create tracker HTTP URL connection */
        try {
            trkURL = newURL(bytesDown, bytesUp, bytesRemaining, trackerUrl);
            trkConnection = (HttpURLConnection) trkURL.openConnection();
        } catch (Exception e) {
            System.err.println("ERROR: Unable to create HTTP URL Connection with tracker. ");
            return null;
        }

        /* Receiving tracker response */
        try {
            trackerData = new DataInputStream(trkConnection.getInputStream());
            size = trkConnection.getContentLength();
            trkDataByteArray = new byte[size];
            trackerData.readFully(trkDataByteArray);
            trackerData.close();

        } catch (IOException e) {
            System.err.println("Caught IOException: " + e.getMessage());
            return null;
        }

        /* Decoding tracker byte Array response to Map */
        try {
            trkMapResponse = (Map<ByteBuffer, Object>) Bencoder2.decode(trkDataByteArray);
            if (trkMapResponse.get(keyFAILURE) != null) {
                return null;
            }
        } catch (BencodingException e) {
            System.err.println("Unable to decode tracker response. ");
        }

        /* Extract and set Info from tracker response */
        setpeerIPList(trkMapResponse);

        /* Close Socket */
        if (trkSocket != null) {
            try {
                trkSocket.close();
            } catch (Exception e) {
                System.err.println("Could not terminate connection with socket.");
            }
        }
        return trkMapResponse;
    }

    /**
     * Method: set peer list 
     * @param trackerResponse the response of tracker
     */
    public static void setpeerIPList(Map<ByteBuffer, Object> trackerResponse) {

        /* Variables */
        String[] decodedTrkResponse;
        String delims = "[:]";

        /* Extract interval */
        trackerInterval = (Integer) trackerResponse.get(keyINTERVAL);
        System.out.println("trackerInterval: " + trackerInterval);

        /* Decode tracker Map response to String[] */
        decodedTrkResponse = decodeCompressedPeers(trackerResponse);

        System.out.println("NumPeers: " + decodedTrkResponse.length);

        for (int i = 0; i < decodedTrkResponse.length; i++) {
            String[] peerString = decodedTrkResponse[i].split(delims);
			peerIPList.add(decodedTrkResponse[i]);
			System.out.println("Found Peer : " + decodedTrkResponse[i]);
        }
    }

    /**
     * Method: Create and return requested URL
     * @param bytesDown The amount of bytes downloaded
     * @param bytesUp The amount of bytes uploaded
     * @param bytesRemaining The amount of bytes remaining
     * @param announceURL URL extracted from parsed torrent file
     * 
     */
    public static URL newURL(int bytesDown, int bytesUp, int bytesRemaining, URL announceURL) {
        /* Variables */
        String newUrlString = "";

        /* Find a random port to connect */
        listeningPort = setPortNum();

        /*  Create requestedURL  */
        newUrlString += trackerUrl
                + "?info_hash=" + toHexString(torrentData.info_hash.array())
                + "&peer_id=" + toHexString((client.getPeerId()).getBytes())
                + "&port=" + listeningPort
                + "&uploaded=" + bytesUp
                + "&downloaded=" + bytesDown
                + "&left=" + bytesRemaining;

        if ((client.getEvent()) != null) {
            newUrlString += "&event=" + (client.getEvent());
        }

        /* Return requested URL */
        try {
            requestedURL = new URL(newUrlString);
            return requestedURL;
        } catch (MalformedURLException e) {
            System.out.println("Unable to create URL");
            return null;
        }
    }

    /**
     * Method: Turn bytes to HexStrings
     * @param bytes 
     */
    public static String toHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        if (bytes.length == 0) {
            return "";
        }

        StringBuilder hex = new StringBuilder(bytes.length * 3);

        for (byte b : bytes) {
            byte hi = (byte) ((b >> 4) & 0x0f);
            byte lo = (byte) (b & 0x0f);

            hex.append('%').append(HEX_CHARS[hi]).append(HEX_CHARS[lo]);
        }
        return hex.toString();
    }

    /**
     * Method: Decode Map to String[]
     * @param map the tracker response 
     * @return String[] decoded array of peers from tracker response
     */
    public static String[] decodeCompressedPeers(Map<ByteBuffer, Object> map) {
        ByteBuffer peers = (ByteBuffer) map.get(ByteBuffer.wrap("peers".getBytes()));
        ArrayList<String> peerURLs = new ArrayList<String>();
        try {
            while (true) {
                String ip = String.format("%d.%d.%d.%d",
                        peers.get() & 0xff,
                        peers.get() & 0xff,
                        peers.get() & 0xff,
                        peers.get() & 0xff);
                int port = peers.get() * 256 + peers.get();
                peerURLs.add(ip + ":" + port);
            }
        } catch (BufferUnderflowException e) {
            // done
        }
        return peerURLs.toArray(new String[peerURLs.size()]);
    }

    /* ================================================================================ */
    /* SET-METHODS  									*/
    /* ================================================================================ */
    /**
     * Method: Returns a port to connect on
     */
    public static int setPortNum() {
        /* Variables */
        ServerSocket serverPort;
        int listenPort;

        for (int i = 6881; i <= 6889; i++) {
            try {
                serverPort = new ServerSocket(i);
                return listenPort = i;
            } catch (IOException e) {
                System.out.println("Unable to create Socket at port " + i);
            }
        }

        System.out.println("Unable to create Socket. Stopping Now!");
        return -1;
    }

    /* ================================================================================ */
    /* GET-METHODS  									*/
    /* ================================================================================ */
     /**
     * Method: Returns an array list of peers
     */
    public ArrayList<String> getpeerIPList() {
        return peerIPList;
    }

     /**
     * Method: Returns tracker url
     */
    public URL getTrackerUrl() {
        return trackerUrl;
    }

     /**
     * Method: Returns a tracker ip address
     */
    public String getTrackerIP() {
        return trackerIP;
    }

     /**
     * Method: Returns a port to tracker
     */
    public int getTrackerPort() {
        return trackerPort;
    }

     /**
     * Method: Returns a tracker interval
     */
    public int getTrackerInterval() {
        return trackerInterval;
    }
}
