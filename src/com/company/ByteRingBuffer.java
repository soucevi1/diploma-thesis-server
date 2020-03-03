// Tento kód je součást diplomové práce "Využití zranitelnosti Janus na operačním systému Android"
// Autor: Bc. Vít Souček (soucevi1@fit.cvut.cz)
//
// Použité zdroje:
//     - ByteRingBuffer
//         autor: Christian d'Heureuse, Inventec Informatik AG, Zurich, Switzerland
//         dostupné z: http://www.source-code.biz/snippets/java/ByteRingBuffer/
//         Použita kompletní třída ByteRingBuffer, do ní byla přidána vícevláknová synchronizace

package com.company;

public class ByteRingBuffer {

    private byte[] rBuf;                      // data bufferu
    private int rBufSize;                     // velikost bufferu
    private int rBufPos;                      // pozice nejstarších dat v bufferu
    private int rBufUsed;                     // počet použitých bytů v bufferu
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
     * Vrací velikost bufferu.
     */
    public int getSize() {
        return rBufSize;
    }

    /**
     * Zapíše data do bufferu
     *
     * @param buf Data k zápisu
     * @param pos Pozice v bufferu, kam se má zapsat první prvek
     * @param len  Délka dat k zápisu
     * @return Počet zapsaných bytů
     * Zaručeno <code>min(len, getFree())</code>
     */
    public int write(byte[] buf, int pos, int len) {
        synchronized (bufferLock) {
            if (len < 0) {
                throw new IllegalArgumentException();
            }
            if (rBufUsed == 0) {
                rBufPos = 0;
            }
            int p1 = rBufPos + rBufUsed;
            if (p1 < rBufSize) {
                int trLen1 = Math.min(len, rBufSize - p1);
                append(buf, pos, trLen1);
                int trLen2 = Math.min(len - trLen1, rBufPos);
                append(buf, pos + trLen1, trLen2);
                return trLen1 + trLen2;
            } else {
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
     * Přečte data z bufferu.
     * @param len Požadovaný počet přečtených bytu
     * @param buf Buffer pro uložení přečtených dat
     * @param pos Pozice, odkud přečíst první byte
     * @return Počet přečtených bytů.
     * Zaručeno <code>min(len, getUsed())</code>.
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
     * Přečte data z bufferu.
     *
     * <p>Zjednodušení pro: <code>read(buf, 0, buf.length)</code>
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
