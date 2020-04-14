// Tento kód je součást diplomové práce "Využití zranitelnosti Janus na operačním systému Android"
// Autor: Bc. Vít Souček (soucevi1@fit.cvut.cz)
//
// Použité zdroje:
//    - třída WaveFileWriter, součást balíčku jsyn
//          autor: Phil Burk
//          dostupné z: https://github.com/philburk/jsyn/blob/master/src/com/jsyn/util/WaveFileWriter.java
//          Ze zdroje je převzato vše v tomto souboru kromě metody write(byte[] buffer, int start, int count)

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
     * @param outputFile Soubor, kam se zapíše výstup.
     * @throws FileNotFoundException pokud soubor neexistuje.
     */
    public WaveFileWriter(File outputFile) throws FileNotFoundException {
        this.outputFile = outputFile;
        FileOutputStream fileOut = new FileOutputStream(outputFile);
        outputStream = new BufferedOutputStream(fileOut);
    }

    /**
     * Zavře výstupní stream.
     *
     * @throws IOException pokud je problém se streamem
     */
    public void close() throws IOException {
        outputStream.close();
        fixSizes();
    }

    /**
     * Zapíše bytový buffer do výstupního streamu.
     * Nejprve zkontroluje, jestli byla zapsána WAVE hlavička.
     *
     * @param buffer Data k zapsání.
     * @param start  Počáteční pozice v bufferu.
     * @param count  Počet bytu k zápisu.
     * @throws IOException při problému s výstupním streamem.
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
     * Zapíše spodních 8 bitů parametru do streamu
     *
     * @param b Byte (int) k zápisu do streamu
     * @throws IOException při problémech se streamem
     */
    private void writeByte(int b) throws IOException {
        outputStream.write(b);
        bytesWritten += 1;
    }

    /**
     * Zapíše 32bitový int ve formátu Little Endian do výstupního streamu.
     *
     * @param n Int k zápisu
     * @throws IOException při problémech se streamem
     */
    public void writeIntLittle(int n) throws IOException {
        writeByte(n);
        writeByte(n >> 8);
        writeByte(n >> 16);
        writeByte(n >> 24);
    }

    /**
     * Zapíše 16bitový short ve formátu Little Endian do výstupního streamu.
     *
     * @param n Short k zápisu
     * @throws IOException při problémech s výstupním streamem
     */
    public void writeShortLittle(short n) throws IOException {
        writeByte(n);
        writeByte(n >> 8);
    }

    /**
     * Zapíše WAVE header pro PCM data.
     *
     * @throws IOException při problémech s výstupním streamem
     */
    private void writeHeader() throws IOException {
        writeRiffHeader();
        writeFormatChunk();
        writeDataChunkHeader();
        outputStream.flush();
        headerWritten = true;
    }

    /**
     * Zapíše 'RIFF' hlavičku a 'WAVE' ID do souboru WAV.
     *
     * @throws IOException při problémech s výstupním streamem
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
     * Zapíše 'fmt' chunk do souboru WAV
     *
     * @throws IOException při problémech s výstupním streamem
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
     * Zapíše hlavičku 'data' chunku do WAV souboru.
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
     * Opraví velikosti RIFF a data chunk podle výsledné velikosti. Předpokládá, že data chunk je poslední chunk.
     *
     * @throws IOException při problémech s výstupním streamem
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