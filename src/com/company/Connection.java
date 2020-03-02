// Tento kod je soucast diplomove prace Vyuziti zranitelnosti Janus na operacnim systemu Android
// Autor: Bc. Vit Soucek

package com.company;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Connection implements Comparable<Connection> {

    private volatile boolean recording = false;
    private ByteRingBuffer buffer;

    private File recordFile;
    private String filename;
    private WaveFileWriter wfWriter;

    public long dataLastReceived;

    public String id;

    /**
     * Konstruktor.
     * Inicializuje ID, vytvori buffer a zaznamena, kdy bylo spojeni vytvoreno.
     *
     * @param connId String ve formatu "IP:port", ktery spojeni identifikuje.
     * @param bufferSize Velikost bufferu na prednahravani
     */
    public Connection(String connId, int bufferSize) {
        id = connId;
        buffer = new ByteRingBuffer(bufferSize);
        dataLastReceived = System.currentTimeMillis();
    }

    /**
     * Zacatek nahravani tohoto spojeni.
     * Je vytvoren novy soubor, inicializovan WaveFileWriter stream.
     * buffer je zapsan do souboru priznak Recording je nastaven na true.
     * Pokud je spojeni jiz nahravano, metoda pouze skonci.
     */
    public void startRecording() {
        if (isRecording()) {
            System.out.println("[-] This connection is already being recorded");
            return;
        }

        System.out.println("[-] Recording the connection " + id);

        filename = createFilename();

        try {
            recordFile = new File(filename);
            if (!recordFile.createNewFile())
                throw new FileAlreadyExistsException("File " + filename + " already exists.");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            wfWriter = new WaveFileWriter(recordFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        writeBufferToFile();

        recording = true;
    }

    /**
     * Aktualizuje cas poslednich prijatych dat.
     */
    public void updateTimestamp() {
        dataLastReceived = System.currentTimeMillis();
    }

    /**
     * Zapise buffer do souboru jeste pred jakakoliv nove nahrana data.
     */
    private void writeBufferToFile() {
        byte[] data;
        int read;
        data = new byte[buffer.getSize()];
        read = buffer.read(data);

        try {
            wfWriter.write(data, 0, read);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Zjisti, jestli toto spojeni je jiz nahravane do souboru.
     *
     * @return true pokud je spojeni jiz nahravano, jinak false.
     */
    public boolean isRecording() {
        return recording;
    }

    /**
     * Zastavi nahravani tohoto spojeni.
     * Priznak Recording je nastaven na false a je uzavren vystupni stream.
     */
    public void stopRecording() {
        if (!isRecording()) {
            System.out.println("[-] Tried to stop recording a connection that is not being recorded");
            return;
        }
        recording = false;
        System.out.println("[-] File " + filename + " created.");
        try {
            wfWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Zapise prijata data do bufferu.
     *
     * @param data Prijata data.
     * @param dataLength Delka dat.
     */
    public void writeToBuffer(byte[] data, int dataLength) {
        buffer.write(data, 0, dataLength);
    }

    /**
     * Zapise prijata data do souboru.
     * @param data Prijata data.
     * @param dataLength Delka prijatych dat.
     */
    public void writeToFile(byte[] data, int dataLength){
        try {
            wfWriter.write(data, 0, dataLength);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Vytvori jmeno pro novou nahravku.
     * Jmeno ma format "connectionID_timestamp.wav"
     *
     * @return The newly created filename.
     */
    private String createFilename() {
        String modifiedID = id.replace(":", ".");
        String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
        return modifiedID + "_" + timeStamp + ".wav";
    }

    /**
     * Porovnani objektu Connection (abecedne dle ID)
     */
    @Override
    public int compareTo(Connection connection) {
        return id.compareTo(connection.id);
    }
}
