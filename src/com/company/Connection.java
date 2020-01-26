// Tento kod je soucast diplomove prace Vyuziti zranitelnosti Janus na operacnim systemu Android
// Autor: Bc. Vit Soucek

package com.company;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Connection implements Comparable<Connection> {

    private volatile boolean recording = false;
    public ByteRingBuffer buffer;

    public File recordFile;
    String filename;
    WaveFileWriter wfWriter;

    public String id;

    /**
     * Constructor
     * @param connId String in the format "IP:port" that identifies this connection
     * @param bufferSize Initial size of the ring buffer
     */
    public Connection(String connId, int bufferSize){
        id = connId;
        buffer = new ByteRingBuffer(bufferSize);
    }

    /**
     * The user has started the recording of this connection.
     * New file is created, the WaveFileWriter stream is initialized,
     * the ring buffer is written to the file and the recording flag is set to true.
     *
     * If the connection is already being recorded, the method returns
     */
    public void startRecording(){
        if(isRecording()){
            System.out.println("[-] This connection is already being recorded");
            return;
        }

        filename = createFilename();

        try {
            recordFile = new File(filename);
            if (! recordFile.createNewFile())
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
     * Write the ring buffer to the file.
     *
     * The buffer with pre-recorded data is written to the output
     * file before any newly recorded data.
     */
    private void writeBufferToFile(){
        byte[] data = new byte[buffer.getUsed()];
        int read = buffer.read(data);

        try {
            wfWriter.write(data, 0, read);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Find out whether this connection is already being recorded to the file.
     * @return Boolean, true if this connection is already being recorded, false otherwise.
     */
    public boolean isRecording(){
        return recording;
    }

    /**
     * Stop recording this connection.
     *
     * Set the recording flag to false and close the output stream.
     * @throws IOException if there is a problem with the output stream
     */
    public void stopRecording() throws IOException {
        if(!isRecording()){
            System.out.println("[-] Tried to stop recording a connection that is not being recorded");
            return;
        }
        recording = false;
        System.out.println("[-] File " + filename + " created.");
        wfWriter.close();
    }

    /**
     * Write the received data to the ring buffer
     * @param data Received data
     * @param dataLength Length of the received data
     */
    public void writeToBuffer(byte[] data, int dataLength){
        buffer.write(data, 0, dataLength);
    }

    /**
     * Resize the ring buffer of this connection.
     * @param newSize New size of the buffer
     */
    public void resizeBuffer(int newSize){
        if(newSize < 0)
            throw new IllegalArgumentException("New buffer size must be positive");
        buffer.resize(newSize);
    }

    /**
     * Craft a filename for the new recording.
     * The filename is in format "connectionID___timestamp.wav"
     *
     * @return The newly created filename.
     */
    private String createFilename(){
        String modifiedID = id.replace(":", ".");
        String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
        return modifiedID + "___" + timeStamp + ".wav";
    }

    /**
     * The Connection class needs to be comparable.
     * The comparison is based on the ID (alphabetical)
     */
    @Override
    public int compareTo(Connection connection) {
        return id.compareTo(connection.id);
    }
}
