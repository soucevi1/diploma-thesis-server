// Tento kod je soucast diplomove prace Vyuziti zranitelnosti Janus na operacnim systemu Android
// Autor: Bc. Vit Soucek
//
// Pouzite zdroje:
//    - zminene primo v kodu v dokumentacnich komentarich


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
    private final int connectionTimeout = 2000; // 2 seconds

    private Thread t;
    private ExecutorService pool;
    ScheduledExecutorService timeoutChecker;

    private int sampleRate = 44100;//16000;//8000;//44100;//44100;
    private int bufferLen = 26880 * 20;//960*20;//512*2;
    private byte[] receiveData = new byte[bufferLen];

    private SourceDataLine sourceDataLine;

    private DatagramSocket serverSocket;

    private int ringBufferSize;

    /**
     * Konstruktor.
     * Inicializuje maximalni velikost bufferu a threadpool pro zpracovavani prijatych dat.
     *
     * @param maxMemory Maximalni velikost bufferu (v MB) pro prednahravani spojeni.
     * @param threadCnt Pocet vlaken pro zpracovavani prijatych dat.
     */
    public ServerThread(int maxMemory, int threadCnt) {
        ringBufferSize = maxMemory * 1000000;
        pool = Executors.newFixedThreadPool(threadCnt);
        activeConnection.set("");

        System.out.println("[-] Buffers size set to: " + ringBufferSize + " B. (~ prerecorded " +
                ((ringBufferSize) / (2 * sampleRate)) / 60 + " minutes and " + ((ringBufferSize) / (2 * sampleRate)) % 60 +
                " seconds per connection)");
    }

    /**
     * Hlavni metoda teto tridy.
     * Inicializuje prvky pro prehravani zvuku a pak v cyklu:
     * - prijme data
     * - vytvori objekt ConnectionTask a necha pool, aby ho zpracoval
     */
    @Override
    public void run() {

        try {
            int port = 50005;
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

        initializeAudioPlayer();

        connections = new ConcurrentHashMap<>();

        startTimeoutChecker();

        while (status) {
            DatagramPacket packet = receivePacket();
            String senderID = getSenderID(packet);

            if (!connections.containsKey(senderID)) {
                System.out.println("[-] New connection: " + senderID);
                connections.put(senderID, new Connection(senderID, ringBufferSize));
            }

            Connection currentConnection = connections.get(senderID);

            currentConnection.updateTimestamp();

            // Pokud neexistuje aktivni spojeni, bude aktivni toho aktualni
            if (activeConnection.get().equals("")) {
                activeConnection.set(senderID);
                System.out.println("[-] Active connection: " + activeConnection);
            }

            byte[] data = packet.getData();
            int dataLength = packet.getLength();
            Runnable task = new ConnectionTask(currentConnection, data, dataLength, senderID.equals(activeConnection.get()), sourceDataLine);
            pool.execute(task);
        }

        System.out.println("[-] Thread exiting");
        timeoutChecker.shutdown();
        pool.shutdown();
        sourceDataLine.drain();
        sourceDataLine.close();
    }

    /**
     * Kontrola, jestli nejake spojeni neni hluche.
     * Kazde 3 vteriny se zkontroluje, jestli od nektereho ze spojeni
     * neprestala po nejakou dobu chodit data. Pokud ano, spojeni je odstraneno.
     * <p>
     * Periodicke spousteni vlakna bylo inspirovano odpovedi na StackOverflow otazku "Java Thread every X seconds"
     * autori: Matt Ball, cletus
     * dostupne z: https://stackoverflow.com/a/3541686/6136143
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
     * Odstrani spojeni ze seznamu.
     * Pokud se spojeni zrovna nahrava, je nahravani zastaveno.
     *
     * @param connectionID ID spojeni, ktere se ma odstranit.
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
            System.out.println("[-] Connection does not exist");
    }

    /**
     * Spusti kod objektu v oddelenem vlakne.
     */
    void start() {
        System.out.println("[-] Starting the server thread.");
        if (t == null) {
            t = new Thread(this);
            t.start();
        }
    }

    /**
     * Prijme paket od jakehokoliv zdroje.
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
     * Inicializuje vsechny objekty potrebne pro prehravani zvuku.
     * Inspirovano otazkou na StackOverflow "Stream Live Android Audio To Server" a odpovedi na ni.
     * autor otazky i odpovedi: chuckliddell0
     * dostupne z: https://stackoverflow.com/questions/15349987/stream-live-android-audio-to-server
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
     * Ziska zdrojovou IP adresu a port z
     * prijateho paketu.
     *
     * @param packet Prijaty DatagramPacket.
     * @return IP a port jako String ve formatu IP:port
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
