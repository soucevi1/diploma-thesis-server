// https://stackoverflow.com/questions/15349987/stream-live-android-audio-to-server
package com.company;

import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

class Server {

    AudioInputStream audioInputStream;
    static AudioInputStream ais;
    static AudioFormat format;
    static boolean status = true;
    static int port = 50005;
    static int sampleRate = 44100;//16000;//8000;//44100;//44100;
    static int bufferLen = 26880*20;//960*20;//512*2;

    static DataLine.Info dataLineInfo;
    static SourceDataLine sourceDataLine;

    public static void main(String args[]) throws Exception {

        DatagramSocket serverSocket = new DatagramSocket(port);

        /**
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

        byte[] receiveData = new byte[bufferLen];

        format = new AudioFormat(sampleRate, 16, 1, true, false);

        dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
        sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
        sourceDataLine.open(format);
        sourceDataLine.start();

        FloatControl volumeControl = (FloatControl) sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
        volumeControl.setValue(volumeControl.getMaximum());

        while (status == true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            System.out.println("Received bytes: " + receivePacket.getLength());

            /*ByteArrayInputStream baiss = new ByteArrayInputStream(
                    receivePacket.getData());
            ais = new AudioInputStream(baiss, format, receivePacket.getLength());*/
            //printData(receivePacket.getData());
            toSpeaker(receivePacket.getData(), receivePacket.getLength());
            Arrays.fill(receiveData, (byte)0);
        }
        sourceDataLine.drain();
        sourceDataLine.close();
    }

    public static void printData(byte data[], int length){
        System.out.print("Received: ");
        for (byte datum : data) {
            System.out.print(String.format("%02X", datum));
        }
        System.out.println("");
    }

    public static void toSpeaker(byte soundbytes[], int length) {
        try {
            sourceDataLine.write(soundbytes, 0, length);
        } catch (Exception e) {
            System.out.println("Not working in speakers...");
            e.printStackTrace();
        }
    }
}