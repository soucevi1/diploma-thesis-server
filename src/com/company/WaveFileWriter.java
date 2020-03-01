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
    private int frameRate = 44100;
    private int samplesPerFrame = 1;
    private int bitsPerSample = 16;
    private int bytesWritten;
    private File outputFile;
    private boolean headerWritten = false;
    private final Object writeLock = new Object();

    /**
     * Create a writer that will write to the specified file.
     *
     * @param outputFile File that the output will be written to
     * @throws FileNotFoundException in case the file is not found
     */
    public WaveFileWriter(File outputFile) throws FileNotFoundException {
        this.outputFile = outputFile;
        FileOutputStream fileOut = new FileOutputStream(outputFile);
        outputStream = new BufferedOutputStream(fileOut);
    }

    /**
     * Close the output stream.
     * @throws IOException in case there is a problem with the stream
     */
    public void close() throws IOException {
        outputStream.close();
        fixSizes();
    }

    /**
     * Write the byte buffer to the output stream.
     *
     * First, check whether the WAVE header has already been written.
     * @param buffer Data to write to the output stream
     * @param start Starting position in the data buffer
     * @param count Count of bytes to be written
     * @throws IOException in case something went wrong with the output stream
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
     * Write lower 8 bits of the int parameter to the output stream.
     * Upper bits are ignored.
     * @param b Byte (int) to be written to the stream
     * @throws IOException in case something went wrong with the stream
     */
    private void writeByte(int b) throws IOException {
        outputStream.write(b);
        bytesWritten += 1;
    }

    /**
     * Write a 32 bit integer to the stream in Little Endian format.
     */
    public void writeIntLittle(int n) throws IOException {
        writeByte(n);
        writeByte(n >> 8);
        writeByte(n >> 16);
        writeByte(n >> 24);
    }

    /**
     * Write a 16 bit integer to the stream in Little Endian format.
     */
    public void writeShortLittle(short n) throws IOException {
        writeByte(n);
        writeByte(n >> 8);
    }

    /**
     * Write a simple WAV header for PCM data.
     */
    private void writeHeader() throws IOException {
        writeRiffHeader();
        writeFormatChunk();
        writeDataChunkHeader();
        outputStream.flush();
        headerWritten = true;
    }

    /**
     * Write a 'RIFF' file header and a 'WAVE' ID to the WAV file.
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
     * Write an 'fmt ' chunk to the WAV file containing the given information.
     */
    public void writeFormatChunk() throws IOException {
        int bytesPerSample = (bitsPerSample + 7) / 8;

        writeByte('f');
        writeByte('m');
        writeByte('t');
        writeByte(' ');
        writeIntLittle(16); // chunk size
        writeShortLittle(WAVE_FORMAT_PCM);
        writeShortLittle((short) samplesPerFrame);
        writeIntLittle(frameRate);
        // bytes/second
        writeIntLittle(frameRate * samplesPerFrame * bytesPerSample);
        // block align
        writeShortLittle((short) (samplesPerFrame * bytesPerSample));
        writeShortLittle((short) bitsPerSample);
    }

    /**
     * Write a 'data' chunk header to the WAV file. This should be followed by call to
     * writeShortLittle() to write the data to the chunk.
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
     * Fix RIFF and data chunk sizes based on final size. Assume data chunk is the last chunk.
     */
    private void fixSizes() throws IOException {
        RandomAccessFile randomFile = new RandomAccessFile(outputFile, "rw");
        try {
            // adjust RIFF size
            long end = bytesWritten;
            int riffSize = (int) (end - riffSizePosition) - 4;
            randomFile.seek(riffSizePosition);
            writeRandomIntLittle(randomFile, riffSize);
            // adjust data size
            int dataSize = (int) (end - dataSizePosition) - 4;
            randomFile.seek(dataSizePosition);
            writeRandomIntLittle(randomFile, dataSize);
        } finally {
            randomFile.close();
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