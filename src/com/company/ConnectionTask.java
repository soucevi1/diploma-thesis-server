// Tento kod je soucast diplomove prace Vyuziti zranitelnosti Janus na operacnim systemu Android
// Autor: Bc. Vit Soucek


package com.company;

import javax.sound.sampled.SourceDataLine;
import java.io.IOException;

public class ConnectionTask implements Runnable{

    private Connection connection;
    private byte[] data;
    private int dataLength;
    private boolean activeConnection;
    private final SourceDataLine sourceDataLine;

    /**
     * Constructor
     *
     * @param conn Current connection
     * @param receivedData Data to be handled
     * @param receivedDataLength Length of the data
     * @param active Is the connection active?
     * @param sdl SourceDataLine in case the connection is active
     */
    public ConnectionTask(Connection conn, byte[] receivedData, int receivedDataLength, boolean active, SourceDataLine sdl){
        connection = conn;
        data = receivedData;
        dataLength = receivedDataLength;
        activeConnection = active;
        sourceDataLine = sdl;
    }

    /**
     * Decide what to do with received data and than do it.
     */
    @Override
    public void run() {
        if (connection.isRecording()) {
            toFile(data, dataLength, connection);
        } else {
            toRingBuffer(data, dataLength, connection);
        }

        // If this connection is supposed to be active,
        // play the received sound on the speakers
        if (activeConnection) {
            toSpeaker(data, dataLength);
        }
    }

    /**
     * Play the received data on the speaker.
     *
     * @param soundbytes Data to play.
     * @param length     Length of the data o play/
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
}
