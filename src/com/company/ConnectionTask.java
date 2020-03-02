// Tento kod je soucast diplomove prace Vyuziti zranitelnosti Janus na operacnim systemu Android
// Autor: Bc. Vit Soucek


package com.company;

import javax.sound.sampled.SourceDataLine;

public class ConnectionTask implements Runnable{

    private Connection connection;
    private byte[] data;
    private int dataLength;
    private boolean activeConnection;
    private final SourceDataLine sourceDataLine;

    /**
     * Konstruktor.
     *
     * @param conn Aktualni spojeni
     * @param receivedData Data ke zpracovani
     * @param receivedDataLength Delka dat
     * @param active Je spojeni aktivni?
     * @param sdl SourceDataLine pro pripad, ze se spojeni ma prehrat
     */
    public ConnectionTask(Connection conn, byte[] receivedData, int receivedDataLength, boolean active, SourceDataLine sdl){
        connection = conn;
        data = receivedData;
        dataLength = receivedDataLength;
        activeConnection = active;
        sourceDataLine = sdl;
    }

    /**
     * Rozhodne, jak zpracovat prijata data.
     */
    @Override
    public void run() {
        if (connection.isRecording()) {
            toFile(data, dataLength, connection);
        } else {
            toRingBuffer(data, dataLength, connection);
        }
        if (activeConnection) {
            toSpeaker(data, dataLength);
        }
    }

    /**
     * Prehraje data v reproduktorech.
     * Inspirovano otazkou "Save live audio streaming to wave file in Java" na StackOverflow
     * a jeji prijatou odpovedi.
     * autori: Sadegh Bakhshandeh Sajjad, dieter
     * dostupne z: https://stackoverflow.com/questions/49811545/save-live-audio-streaming-to-wave-file-in-java
     *
     * @param soundbytes Data k prehravani.
     * @param length Delka dat.
     */
    public void toSpeaker(byte[] soundbytes, int length) {
        synchronized (sourceDataLine) {
            try {
                sourceDataLine.write(soundbytes, 0, length);
            } catch (Exception e) {
                System.out.println("Not working speakers...");
                e.printStackTrace();
            }
        }
    }


    /**
     * Zapise data do vystupniho souboru.
     * @param data Data k zapsani
     * @param dataLength Delka dat
     * @param connection Spojeni, kteremu data patri
     */
    private void toFile(byte[] data, int dataLength, Connection connection) {
        connection.writeToFile(data, dataLength);
    }

    /**
     * Prida data do bufferu spojeni, kteremu data patri.
     * @param data Data k zapsani
     * @param dataLength Delka dat
     * @param conn Spojeni, kteremu data patri
     */
    private void toRingBuffer(byte[] data, int dataLength, Connection conn) {
        conn.writeToBuffer(data, dataLength);
    }
}
