// Tento kod je soucast diplomove prace Vyuziti zranitelnosti Janus na operacnim systemu Android
// Autor: Bc. Vit Soucek
//
// Pouzite zdroje:
//     - ByteRingBuffer
//         autor: Christian d'Heureuse, Inventec Informatik AG, Zurich, Switzerland
//         dostupne z: http://www.source-code.biz/snippets/java/ByteRingBuffer/
//         Pouzita kompletni trida ByteRingBuffer, pridana synchronizace

package com.company;

public class ByteRingBuffer {

    private byte[] rBuf;                      // ring buffer data
    private int rBufSize;                     // ring buffer size
    private int rBufPos;                      // position of first (oldest) data byte within the ring buffer
    private int rBufUsed;                     // number of used data bytes within the ring buffer
    private final Object bufferLock = new Object();

    /**
     * Konstruktor
     * @param size Velikost bufferu
     */
    public ByteRingBuffer(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException();
        }
        rBufSize = size;
        rBuf = new byte[rBufSize];
    }

    /**
     * Vraci velikost bufferu.
     */
    public int getSize() {
        return rBufSize;
    }

    /**
     * Zapise data do bufferu
     *
     * @param buf Data k zapisu
     * @param pos Pozice v bufferu, kam se ma zapsat prvni prvek
     * @param len  Delka dat k zapisu
     * @return Pocet zapsanych bytu
     * Zaruceno <code>min(len, getFree())</code>
     */
    public int write(byte[] buf, int pos, int len) {
        synchronized (bufferLock) {
            if (len < 0) {
                throw new IllegalArgumentException();
            }
            if (rBufUsed == 0) {
                rBufPos = 0;
            }                                       // (speed optimization)
            int p1 = rBufPos + rBufUsed;
            if (p1 < rBufSize) {                                    // free space in two pieces
                int trLen1 = Math.min(len, rBufSize - p1);
                append(buf, pos, trLen1);
                int trLen2 = Math.min(len - trLen1, rBufPos);
                append(buf, pos + trLen1, trLen2);
                return trLen1 + trLen2;
            } else {                                                 // free space in one piece
                int trLen = Math.min(len, rBufSize - rBufUsed);
                append(buf, pos, trLen);
                return trLen;
            }
        }
    }

    private void append(byte[] buf, int pos, int len) {
        if (len == 0) {
            return;
        }
        if (len < 0) {
            throw new AssertionError();
        }
        int p = clip(rBufPos + rBufUsed);
        System.arraycopy(buf, pos, rBuf, p, len);
        rBufUsed += len;
    }

    /**
     * Precte data z bufferu.
     * @param len Pozadovany pocet prectenych bytu
     * @param buf Buffer pro ulozeni prectenych dat
     * @param pos Pozice, odkud precist prvni byte
     * @return Pocet prectenych bytu.
     * Zaruceno <code>min(len, getUsed())</code>.
     */
    public int read(byte[] buf, int pos, int len) {
        synchronized (bufferLock) {
            if (len < 0) {
                throw new IllegalArgumentException();
            }
            int trLen1 = Math.min(len, Math.min(rBufUsed, rBufSize - rBufPos));
            remove(buf, pos, trLen1);
            int trLen2 = Math.min(len - trLen1, rBufUsed);
            remove(buf, pos + trLen1, trLen2);
            return trLen1 + trLen2;
        }
    }

    /**
     * Precte data z bufferu.
     *
     * <p>Zjednoduseni pro: <code>read(buf, 0, buf.length)</code>
     */
    public int read(byte[] buf) {
        return read(buf, 0, buf.length);
    }

    private void remove(byte[] buf, int pos, int len) {
        if (len == 0) {
            return;
        }
        if (len < 0) {
            throw new AssertionError();
        }
        System.arraycopy(rBuf, rBufPos, buf, pos, len);
        rBufPos = clip(rBufPos + len);
        rBufUsed -= len;
    }

    private int clip(int p) {
        return (p < rBufSize) ? p : (p - rBufSize);
    }

}
