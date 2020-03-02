// Tento kod je soucast diplomove prace Vyuziti zranitelnosti Janus na operacnim systemu Android
// Autor: Bc. Vit Soucek
//
// Pouzite zdroje:
//    - trida WaveFileWriter, soucast balicku jsyn
//          autor: Phil Burk
//          dostupne z: https://github.com/philburk/jsyn/blob/master/src/com/jsyn/util/WaveFileWriter.java
//          Ze zdroje je prebrano vse v tomto souboru krome metody write(byte[] buffer, int start, int count)

package com.company;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public class WaveFileWriter {
    private static final short WAVE_FORMAT_PCM = 1;
    private OutputStream outputStream;
    private long riffSizePosition = 0;
    private long dataSizePosition = 0;
    private int bytesWritten;
    private File outputFile;
    private boolean headerWritten = false;
    private final Object writeLock = new Object();

    /**
     * Konstruktor.
     *
     * @param outputFile Soubor, kam se zapise vystup.
     * @throws FileNotFoundException pokud soubor nexistuje.
     */
    public WaveFileWriter(File outputFile) throws FileNotFoundException {
        this.outputFile = outputFile;
        FileOutputStream fileOut = new FileOutputStream(outputFile);
        outputStream = new BufferedOutputStream(fileOut);
    }

    /**
     * Zavre vystupni stream.
     * @throws IOException pokud je problem se streamem
     */
    public void close() throws IOException {
        outputStream.close();
        fixSizes();
    }

    /**
     * Zapise bytovy buffer do vystupniho streamu.
     * Nejprve zkontroluje, jestli byla zapsana WAVE hlavicka.
     * @param buffer Data k zapsani.
     * @param start Pocatecni pozice v bufferu.
     * @param count Pocet bytu k zapisu.
     * @throws IOException pri problemu s vystupnimi streamem.
     */
    public void write(byte[] buffer, int start, int count) throws IOException {
        synchronized (writeLock) {
            if (!headerWritten) {
                writeHeader();
            }
            for (int i = 0; i < count; i++) {
                writeByte(buffer[start + i]);
            }
        }
    }

    /**
     * Zapise spodnich 8 bitu parametru do streamu
     * @param b Byte (int) k zapisu do streamu
     * @throws IOException pro problemech se streamem
     */
    private void writeByte(int b) throws IOException {
        outputStream.write(b);
        bytesWritten += 1;
    }

    /**
     * Zapise 32bitovy int ve formatu Little Endian do vystupniho streamu.
     * @param n Int k zapisu
     * @throws IOException pro problemech se streamem
     */
    public void writeIntLittle(int n) throws IOException {
        writeByte(n);
        writeByte(n >> 8);
        writeByte(n >> 16);
        writeByte(n >> 24);
    }

    /**
     * Zapise 16bitovy short ve formatu Little Endian do vystupniho streamu.
     * @param n Short k zapisu
     * @throws IOException pro problemech s vystupnim streamem
     */
    public void writeShortLittle(short n) throws IOException {
        writeByte(n);
        writeByte(n >> 8);
    }

    /**
     * Zapise WAVE header pro PCM data.
     * @throws IOException pro problemech s vystupnim streamem
     */
    private void writeHeader() throws IOException {
        writeRiffHeader();
        writeFormatChunk();
        writeDataChunkHeader();
        outputStream.flush();
        headerWritten = true;
    }

    /**
     * Zapise 'RIFF' hlavicku a 'WAVE' ID do soubou WAV.
     * @throws IOException pro problemech s vystupnim streamem
     */
    private void writeRiffHeader() throws IOException {
        writeByte('R');
        writeByte('I');
        writeByte('F');
        writeByte('F');
        riffSizePosition = bytesWritten;
        writeIntLittle(Integer.MAX_VALUE);
        writeByte('W');
        writeByte('A');
        writeByte('V');
        writeByte('E');
    }

    /**
     * Zapise 'fmt ' chunk do souboru WAV
     * @throws IOException pro problemech s vystupnim streamem
     */
    public void writeFormatChunk() throws IOException {
        int bitsPerSample = 16;
        int bytesPerSample = (bitsPerSample + 7) / 8;

        writeByte('f');
        writeByte('m');
        writeByte('t');
        writeByte(' ');
        writeIntLittle(16); // chunk size
        writeShortLittle(WAVE_FORMAT_PCM);
        int samplesPerFrame = 1;
        writeShortLittle((short) samplesPerFrame);
        int frameRate = 44100;
        writeIntLittle(frameRate);
        // bytes/second
        writeIntLittle(frameRate * samplesPerFrame * bytesPerSample);
        // block align
        writeShortLittle((short) (samplesPerFrame * bytesPerSample));
        writeShortLittle((short) bitsPerSample);
    }

    /**
     * Zapise hlavicku 'data' chunku do WAV souboru.
     */
    public void writeDataChunkHeader() throws IOException {
        writeByte('d');
        writeByte('a');
        writeByte('t');
        writeByte('a');
        dataSizePosition = bytesWritten;
        writeIntLittle(Integer.MAX_VALUE); // size
    }

    /**
     * Opravi velikosti RIFF a data chunk podle vysledne velikosti. Predpoklada, ze data chunk je posledni chunk.
     * @throws IOException pro problemech s vystupnim streamem
     */
    private void fixSizes() throws IOException {
        try (RandomAccessFile randomFile = new RandomAccessFile(outputFile, "rw")) {
            // adjust RIFF size
            long end = bytesWritten;
            int riffSize = (int) (end - riffSizePosition) - 4;
            randomFile.seek(riffSizePosition);
            writeRandomIntLittle(randomFile, riffSize);
            // adjust data size
            int dataSize = (int) (end - dataSizePosition) - 4;
            randomFile.seek(dataSizePosition);
            writeRandomIntLittle(randomFile, dataSize);
        }
    }

    private void writeRandomIntLittle(RandomAccessFile randomFile, int n) throws IOException {
        byte[] buffer = new byte[4];
        buffer[0] = (byte) n;
        buffer[1] = (byte) (n >> 8);
        buffer[2] = (byte) (n >> 16);
        buffer[3] = (byte) (n >> 24);
        randomFile.write(buffer);
    }

}