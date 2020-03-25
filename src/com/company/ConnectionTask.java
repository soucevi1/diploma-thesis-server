// Tento kód je součást diplomové práce "Využití zranitelnosti Janus na operačním systému Android"
// Autor: Bc. Vít Souček (soucevi1@fit.cvut.cz)

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
     * @param conn Aktualní spojení
     * @param receivedData Data ke zpracování
     * @param receivedDataLength Délka dat
     * @param active Je spojení aktivní?
     * @param sdl SourceDataLine pro případ, že se spojení má přehrát
     */
    public ConnectionTask(Connection conn, byte[] receivedData, int receivedDataLength, boolean active, SourceDataLine sdl){
        connection = conn;
        data = receivedData;
        dataLength = receivedDataLength;
        activeConnection = active;
        sourceDataLine = sdl;
    }

    /**
     * Rozhodne, jak zpracovat přijatá data.
     */
    @Override
    public void run() {
        if (connection.isRecording()) {
            toFile(data, dataLength);
        } else {
            toRingBuffer(data, dataLength);
        }
        if (activeConnection) {
            toSpeaker(data, dataLength);
        }
    }

    /**
     * Přehraje data v reproduktorech.
     * Inspirováno otázkou "Save live audio streaming to wave file in Java" na StackOverflow.com
     * a její přijatou odpovědí.
     * autoři: Sadegh Bakhshandeh Sajjad, dieter
     * dostupné z: https://stackoverflow.com/questions/49811545/save-live-audio-streaming-to-wave-file-in-java
     *
     * @param soundbytes Data k přehrávání.
     * @param length Délka dat.
     */
    private void toSpeaker(byte[] soundbytes, int length) {
        synchronized (sourceDataLine) {
            try {
                sourceDataLine.write(soundbytes, 0, length);
            } catch (Exception e) {
                System.out.println("[X] Unable to play sounds on the data line");
                e.printStackTrace();
            }
        }
    }


    /**
     * Zapíše data do výstupního souboru.
     * @param data Data k zapsání
     * @param dataLength Délka dat
     */
    private void toFile(byte[] data, int dataLength) {
        connection.writeToFile(data, dataLength);
    }

    /**
     * Přidá data do bufferu spojení, od kterého data přišla.
     * @param data Data k zapsání
     * @param dataLength Délka dat
     */
    private void toRingBuffer(byte[] data, int dataLength) {
        connection.writeToBuffer(data, dataLength);
    }
}
