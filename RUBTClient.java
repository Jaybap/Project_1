import java.io.*;
import java.util.Random;
import java.util.Map;
import java.util.ArrayList;
import java.util.BitSet;
import javax.swing.*;

/**
 * RUBTClient Class Main Functions: 
 * 1) Capture torrent FILE and destination FILE
 * 2) Create Client 
 * 3) Initiate Tracker 
 * 4) Start Downloader
 *
 * @author Alex DeOliveira [126-00-8635]
 * @author Jason Baptista [126-00-4630]
 * @author Elizabeth Sanz [127-00-8628]
 * @version "project02"
 */
public class RUBTClient {

    /* Public variables */
    
    /* Torrent File Information */
    public static String torrentName;
    public static File torrentFile;
    public static TorrentInfo torrent;
    
    /* Destination File Information */
    public static String destinationName;
    public static File destinationFile;
    
    /* Client Information */
    public static String peerID;
    public static BitSet Bitfield;
    public static int[] intBitField;
    
    /* Peer Information */
    public static ArrayList<Peer> peer;
    
    /* Download Information  */
    public static ByteArrayOutputStream[] piecesDL;
    public static int bytesDownloaded;
    public int bytesUploaded;
    public int bytesRemaining;
    public static String event;
    public static final int blockLength = 16384;  /* 16384 = 2^14*/

    public static int numPieces = 0;
    public static int numBlocks = 0;
    public static int numBlkPieceRatio = 0;
    public static int numBlkLastPiece = 0;
    public static int lastBlkSize = 0;
    public static int lastPieceSize = 0;
    public static boolean startDownload = true;
    public static Incoming_Controller[] uploadconnections=new Incoming_Controller[3];
    public static ArrayList<Incoming_Controller> choked_peers;

    /* ================================================================================ */
    /* RUBTClient Constructor								*/
    /* ================================================================================ */
    RUBTClient(String firstArg, String secondArg) {
        try {
            /* Extract information as String */
            torrentName = firstArg;
            destinationName = secondArg;

            /* Capture torrent File */
            torrentFile = new File(torrentName);

            /* Parse torrent and return TorrentInfo */
            torrent = torrent_parser(torrentFile);

            /* Initialize upload/download/remaining Bytes */
            bytesDownloaded = 0;
            bytesUploaded = 0;
            bytesRemaining = torrent.file_length;
            event = null;

            /* Set number of pieces */
            if (bytesRemaining == 0) {
                numPieces = torrent.file_length / torrent.piece_length;
            } else {
                numPieces = torrent.file_length / torrent.piece_length + 1;
            }

            /* Set number of blocks */
            numBlocks = (int) Math.ceil((double) torrent.file_length / (double) blockLength);

            /* Set blocks per piece */
            numBlkPieceRatio = (int) Math.ceil((double) torrent.piece_length / (double) blockLength);

            /* Set the last piece size */
            lastPieceSize = torrent.file_length - (numPieces - 1) * torrent.piece_length;

            /* Set the last block size */
            lastBlkSize = lastPieceSize - ((int) Math.ceil((double) lastPieceSize / (double) blockLength) - 1) * blockLength;

            /* Create peerID */
            peerID = setPeerId();

            /* Initialize BitSet */
            Bitfield = new BitSet(numPieces);
            intBitField = new int[numPieces];

            /* Initialize ByteArrayOutputStream */
            piecesDL = new ByteArrayOutputStream[numPieces];
        } catch (NullPointerException e) {
            System.err.println("ERROR: Torrent File does not exist.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("ERROR: Unable to initialize Client.");
            System.exit(1);
        }
    }

    /* ================================================================================ */
    /* MAIN                                                                             */
    /* ================================================================================ */
    public static void main(String[] args) throws IOException, BencodingException {

        /* Global Variables */
        RUBTClient client;
        Tracker tracker;
        String localPeer = "";

        ////////////////////////////////////////Uncomment me please!!!///////////////////////////////////////////

        // Optimistic_Unchoker unchoker= new Optimistic_Unchoker();

        ////////////////////////////////////////Uncomment me please!!!///////////////////////////////////////////
        // the previous line was commented to work around some bugs while i worked on a different part of the code. ~ Alex

        /* Check if valid number of arguments in the command line */
        if (!validateNumArgs(args)) {
            System.out.println("USAGE: RUBTClient [torrent-file-to-read] [file-name-to-save]");
            System.exit(1);
        }

        /* Initialize Client */
        client = new RUBTClient(args[0], args[1]);

        /* Check for saved state files */
        client.findSavedState();

        /* ================ */
        /* Print Statements */
        /* ================ */
        System.out.println("Initializing RUBTClient. ");
        System.out.println("Torrent File Name: " + torrentName);
        System.out.println("Destination File Name: " + destinationName);
        System.out.println("Peer ID: " + client.getPeerId());
        System.out.println("bytesDownloaded: " + client.bytesDownloaded);
        System.out.println("bytesUploaded: " + client.bytesUploaded);
        System.out.println("bytesRemaining: " + client.bytesRemaining);
        System.out.println("numPieces: " + client.getNumPieces());
        System.out.println("numBlocks: " + client.getNumBlocks());
        System.out.println("numBlkPieceRatio: " + client.getNumBlkPieceRatio());
        System.out.println("numBlkLastPiece: " + client.getNumBlkLastPiece());

        /**
         * Initialize Tracker
         */
        tracker = new Tracker(client, torrent);

        /* ================ */
        /* Print Statements */
        /* ================ */
        System.out.println("Initializing Tracker. ");
        System.out.println("trackerUrl: " + tracker.getTrackerUrl());
        System.out.println("trackerIP: " + tracker.getTrackerIP());
        System.out.println("trackerPort: " + tracker.getTrackerPort());


        /* ================ */
        /* Print Statements */
        /* ================ */
        System.out.println("Connecting to Tracker. ");

        /* Start DownloadManager ("downloader" application thread) */
        while (startDownload) {
            try {
                System.out.println("Waiting for the tracker...");
                Thread.sleep(100);
            } catch (InterruptedException e) {
                System.err.println("Error: Problem sleeping main thread");
            }
        }
        DownloadManager downloader = new DownloadManager(client, tracker);
        downloader.start();

        while (JOptionPane.showConfirmDialog(null, "Would you like to end the program?", "Bit Torrent", JOptionPane.YES_NO_OPTION) != 0) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                System.err.println("Error: Problem sleeping main thread");
            }
        }
        downloader.outputtofile(false);
        System.exit(0);
    }

    /* ================================================================================ */
    /* METHODS  									*/
    /* ================================================================================ */
    /**
     * Method: Updates the global variable bytesDownloaded.
     * @param numBytes The amount by which bytesDownloaded should be
     * incremented.
     */
    public static void updateBytesDownloaded(int numBytes) {
        bytesDownloaded += numBytes;
    }

    /**
     * Method: Checks to see if there is a previously saved download state for
     * this torrent file
     */
    public void findSavedState() {
        File f = new File(torrentName + ".save");

        try {
            FileInputStream fileIn = new FileInputStream(f);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            for (int i = 0; i < numPieces; i++) {
                byte[] buffer = (byte[]) in.readObject();
                if (buffer.length == 0) {
                    piecesDL[i] = null;
                } else {
                    piecesDL[i] = new ByteArrayOutputStream();
                    piecesDL[i].write(buffer);
                    if (piecesDL[i].size() > 0) {
                        intBitField[i] = 2; // has piece
                        Bitfield.set(i);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("No save file");
        } catch (ClassNotFoundException e) {
            System.out.println("No save file");
        }
    }

    /**
     * Method: Checks number of arguments in command line
     * @param args, the arguments in the command line
     * @return a boolean. true if valid number of arguments otherwise false
     */
    public static boolean validateNumArgs(String[] args) {
        if ((args.length != 2)) {
            System.err.println("ERROR: Invalid number of arguments");
            return false;
        } else {
            return true;
        }
    }

    /**
     * Method: Parses Torrent File
     * @param: torrent, the torrent file to be parsed
     * @return a TorrentInfo object that gives more information on the torrent
     */
    public TorrentInfo torrent_parser(File torrent) {

        /* Variables */
        DataInputStream torrentReader;
        long torrentSize;
        byte[] torrentByteArray;
        TorrentInfo torrentData;

        try {
            /* Reads from torrent File into byte array */
            torrentReader = new DataInputStream(new FileInputStream(torrent));
            torrentSize = torrent.length();

            /* Checks size of the torrent File */
            if ((torrentSize > Integer.MAX_VALUE) || (torrentSize < Integer.MIN_VALUE)) {
                torrentReader.close();
                throw new IllegalArgumentException(torrentSize + " Torrent File is too large to process. ");
            }

            /* Reads bencoded torrent meta info into a byte array */
            torrentByteArray = new byte[(int) torrentSize];
            torrentReader.readFully(torrentByteArray);

            /* Creates and populates new TorrentInfo object  */
            torrentData = new TorrentInfo(torrentByteArray);
            torrentReader.close();

            return torrentData;
        } catch (FileNotFoundException e) {
            System.err.println("ERROR: Torrent File not found. ");
            return null;
        } catch (IOException e) {
            System.err.println("ERROR: Could not process torrent File. ");
            return null;
        } catch (BencodingException e) {
            System.err.println("ERROR: Unable to Bencode. ");
            return null;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to parse torrent File. ");
            return null;
        }
    }

    /**
     * METHOD: Saves the final completed file to disk and removes any existing
     * save files.
     */
    public static void writeFile() {
        try {
            BufferedOutputStream fileOutput = new BufferedOutputStream(new FileOutputStream(destinationName));
            byte[] toFile = null;
            for (ByteArrayOutputStream piece : piecesDL) {
                toFile = piece.toByteArray();
                fileOutput.write(toFile);
            }
            fileOutput.flush();
            fileOutput.close();
        } catch (IOException e) {
            System.err.println("it broke");
        }

        /* delete the save file */
        try {
            File file = new File(torrentName + ".save");
            if (!file.delete()) {
                System.out.println("failed to delete");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ================================================================================ */
    /* SET-METHODS  									*/
    /* ================================================================================ */
    /**
     * METHOD: Set random peerID
     * @return the peer ID as a String
     */
    public String setPeerId() {

        /* Variables */
        String randomPeerID = "";
        Random randomSequence;

        /* Bank of appropriate peer ID characters */
        char[] appropriateChars = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

        /* Initiate random sequence */
        randomSequence = new Random();

        /* First letter cannot be R */
        randomPeerID += appropriateChars[randomSequence.nextInt(61)];
        while (randomPeerID.equalsIgnoreCase("r")) {
            randomPeerID = "";
            randomPeerID += appropriateChars[randomSequence.nextInt(61)];
        }

        /* Complete random sequence */
        for (int i = 1; i < 20; i++) {
            randomPeerID += appropriateChars[randomSequence.nextInt(61)];
        }

        return randomPeerID;
    }

    /* ================================================================================ */
    /* GET-METHODS  									*/
    /* ================================================================================ */
    /**
     * Method: Retrieves TorrentInfo
     */
    public TorrentInfo getTorrentInfo() {
        return torrent;
    }

    /**
     * Method: Retrieves bytes downloaded
     */
    public int getBytesDownloaded() {
        return bytesDownloaded;
    }

    /**
     * Method: Retrieves bytes uploaded
     */
    public int getBytesUploaded() {
        return bytesUploaded;
    }

    /**
     * Method: Retrieves bytes left
     */
    public int getBytesRemaining() {
        return bytesRemaining;
    }

    /**
     * Method: Retrieves event
     */
    public String getEvent() {
        return event;
    }

    /**
     * Method: Retrieves number of pieces
     */
    public static int getNumPieces() {
        return numPieces;
    }

    /**
     * Method: Retrieves number of blocks
     */
    public int getNumBlocks() {
        return numBlocks;
    }

    /**
     * Method: Retrieves number of blocks per piece
     */
    public int getNumBlkPieceRatio() {
        return numBlkPieceRatio;
    }

    /**
     * Method: Retrieves number of blocks in last piece
     */
    public int getNumBlkLastPiece() {
        return numBlkLastPiece;
    }

    /**
     * Method: Retrieves peer ID
     */
    public String getPeerId() {
        return peerID;
    }

    /**
     * Method: Retrieves bit-field
     */
    public static BitSet getBitfield() {
        return Bitfield;
    }
}
