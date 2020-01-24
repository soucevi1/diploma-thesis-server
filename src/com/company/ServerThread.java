package com.company;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

public class ServerThread implements Runnable {

    private int port = 50005;

    volatile boolean status = true;

    Set<String> connections;
    String activeConnection = "";

    private Thread t;

    private int sampleRate = 44100;//16000;//8000;//44100;//44100;
    private int bufferLen = 26880 * 20;//960*20;//512*2;
    private byte[] receiveData = new byte[bufferLen];

    private SourceDataLine sourceDataLine;
    private AudioFormat format;
    private DataLine.Info dataLineInfo;
    private FloatControl volumeControl;

    private DatagramSocket serverSocket = null;

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

        /*
         * Postup pro vicero zarizeni:
         *
         * - centralni seznam dostupnych spojeni
         * - pokud prijmu paket, ktery nemam, pridan ho
         * - na prikaz uzivatele vypisu dostupna pripojeni
         * - prehraje se jenom zvuk, ktery odpovida danemu spojeni
         */

        // Initialize Audio player
       initializeAudioPlayer();

        // Initialize connection list
        connections = new TreeSet<>();

        // Run until user changes status
        while (status) {

            // Receive packet with some data
            DatagramPacket packet = receivePacket();

            // Find who sent the packet
            String senderID = getSenderID(packet);
            if(connections.add(senderID)){
                System.out.println("[-] New connection: " + senderID);
            }

            // If no connection is active, make this one active
            if(activeConnection.equals("")){
                activeConnection = senderID;
                System.out.println("[-] Active connection: " + activeConnection);
            }

            // If this connection is supposed to be active,
            // play the received sound on the speakers
            if(senderID.equals(activeConnection)) {
                toSpeaker(packet.getData(), packet.getLength());
                Arrays.fill(receiveData, (byte) 0);
            }
        }

        // Exit the application
        System.out.println("[-] Thread exiting");
        sourceDataLine.drain();
        sourceDataLine.close();
    }

    /**
     * Start the thread.
     */
    void start() {
        System.out.println("[-] Starting the server thread.");
        if (t == null) {
            t = new Thread (this);
            t.start();
        }
    }

    /**
     * Receive packet from any source.
     * @return DatagramPacket with received data.
     */
    private DatagramPacket receivePacket(){
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
     * Inspired by: https://stackoverflow.com/questions/15349987/stream-live-android-audio-to-server
     */
    private void initializeAudioPlayer(){
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
     * @param packet Received DatagramPacket with data.
     * @return IP and port as String in format IP:port
     */
    private String getSenderID(DatagramPacket packet){
        String addr = packet.getAddress().toString();
        if(addr.charAt(0) == '/'){
            addr = addr.substring(1);
        }
        int port = packet.getPort();
        return addr + ":" + port;
    }

    /**
     * Debug print the received data.
     * @param data Data to be printed.
     * @param length Length of the data.
     */
    public void printData(byte[] data, int length){
        System.out.print("Received: ");
        for (byte datum : data) {
            System.out.print(String.format("%02X", datum));
        }
        System.out.println("");
    }

    /**
     * Play the received data on the speaker.
     * @param soundbytes Data to play.
     * @param length Length of the data o play/
     */
    private void toSpeaker(byte[] soundbytes, int length) {
        try {
            sourceDataLine.write(soundbytes, 0, length);
        } catch (Exception e) {
            System.out.println("Not working in speakers...");
            e.printStackTrace();
        }
    }
}
