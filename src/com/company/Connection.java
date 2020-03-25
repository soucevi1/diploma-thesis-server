// Tento kód je součást diplomové práce "Využití zranitelnosti Janus na operačním systému Android"
// Autor: Bc. Vít Souček (soucevi1@fit.cvut.cz)

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
     * Inicializuje ID, vytvoří buffer a zaznamená, kdy bylo spojení vytvořeno.
     *
     * @param connId String ve formátu "IP:port", který spojení identifikuje.
     * @param bufferSize Velikost bufferu na přednahrávání
     */
    public Connection(String connId, int bufferSize) {
        id = connId;
        buffer = new ByteRingBuffer(bufferSize);
        dataLastReceived = System.currentTimeMillis();
    }

    /**
     * Začátek nahrávání tohoto spojení.
     * Je vytvořen nový soubor, inicializován WaveFileWriter stream.
     * buffer je zapsán do souboru, příznak Recording je nastaven na true.
     * Pokud je spojení již nahráváno, metoda pouze skončí.
     */
    public void startRecording() {
        if (isRecording()) {
            System.out.println("[X] This connection is already being recorded");
            return;
        }

        System.out.println("[*] Recording the connection " + id);

        filename = createFilename();

        try {
            recordFile = new File(filename);
            if (!recordFile.createNewFile())
                throw new FileAlreadyExistsException("[X] File " + filename + " already exists.");
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
     * Aktualizuje čas posledních přijatých dat.
     */
    public void updateTimestamp() {
        dataLastReceived = System.currentTimeMillis();
    }

    /**
     * Zapíše buffer do souboru ještě před jakákoliv nově nahraná data.
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
     * Zjistí, jestli toto spojení je již nahrávané do souboru.
     *
     * @return true pokud je spojení již nahráváno, jinak false.
     */
    public boolean isRecording() {
        return recording;
    }

    /**
     * Zastaví nahrávání tohoto spojení.
     * Příznak Recording je nastaven na false a je uzavřen výstupní stream.
     */
    public void stopRecording() {
        if (!isRecording()) {
            System.out.println("[X] Tried to stop recording a connection that is not being recorded");
            return;
        }
        recording = false;
        System.out.println("[*] File " + filename + " created.");
        try {
            wfWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Zapíše přijatá data do bufferu.
     *
     * @param data Přijatá data.
     * @param dataLength Délka dat.
     */
    public void writeToBuffer(byte[] data, int dataLength) {
        buffer.write(data, 0, dataLength);
    }

    /**
     * Zapíše přijatá data do souboru.
     * @param data Přijatá data.
     * @param dataLength Délka přijatých dat.
     */
    public void writeToFile(byte[] data, int dataLength){
        try {
            wfWriter.write(data, 0, dataLength);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Vytvoří jméno pro novou nahrávku.
     * Jméno má formát "connectionID_timestamp.wav"
     *
     * @return Nově vytvořené jméno souboru
     */
    private String createFilename() {
        String modifiedID = id.replace(":", ".");
        String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
        return modifiedID + "_" + timeStamp + ".wav";
    }

    /**
     * Porovnání objektu Connection (abecedně dle ID)
     */
    @Override
    public int compareTo(Connection connection) {
        return id.compareTo(connection.id);
    }
}
