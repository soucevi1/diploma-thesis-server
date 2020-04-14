// Tento kód je součást diplomové práce "Využití zranitelnosti Janus na operačním systému Android"
// Autor: Bc. Vít Souček (soucevi1@fit.cvut.cz)

package com.company;

import java.util.Arrays;

public class ByteRingBuffer {
    private byte[] buffer;
    private int start;
    private int end;
    private boolean empty;
    private final Object bufferLock = new Object();

    public int size;

    /**
     * Konstrukror.
     * Inicializuje buffer o velikosti bufSize.
     *
     * @param bufSize Velikost bufferu
     */
    public ByteRingBuffer(int bufSize) {
        size = bufSize;
        buffer = new byte[size];
        start = 0;
        end = 0;
        empty = true;
    }

    /**
     * Zapíše bytové pole data o délce length do bufferu.
     *
     * @param data   Data k zápisu
     * @param length Délka dat
     */
    public void write(byte[] data, int length) {
        synchronized (bufferLock) {
            boolean startCrossed = false;

            if (length <= 0) {
                return;
            }

            // Všechna data se do bufferu nevejdou -- zapíše se pouze "size" nejnovějších
            if (length > size) {
                data = Arrays.copyOfRange(data, length - size, length);
                length = size;
            }

            // Data se vejdou do bufferu v jednom kuse
            if (size - end >= length) {

                System.arraycopy(data, 0, buffer, end, length);

                // Pokud end byl před start, ale po zápisu posune za něj,
                // buffer se úplně naplní a start a end se musí srovnat
                if ((start >= end) && (end + length > start) && !empty) {
                    startCrossed = true;
                }

                end = (end + length) % size;

                if (startCrossed) {
                    start = end;
                }
                empty = false;

                // Data se musí zapsat po částech
            } else {

                // Start se na konci musí posunout, pokud
                // byl překročen endem.
                if (start > end || (start == end && !empty)) {
                    startCrossed = true;
                }

                // Zapsat od end do konce
                System.arraycopy(data, 0, buffer, end, size - end);

                empty = false;

                // Zapsat zbytek dat na začátek bufferu
                System.arraycopy(data, size - end, buffer, 0, length - (size - end));

                end = length - (size - end);

                if ((start < end) || startCrossed) {
                    start = end;
                }

            }
        }
    }

    /**
     * Nakopíruje položky z bufferu do pole out.
     * Nejstarší položka z bufferu jde na začátek pole out,
     * nejnovější na konec.
     *
     * @param out Výstupní pole
     * @return Počet zkopírovaných bytů
     */
    public int read(byte[] out) {
        synchronized (bufferLock) {
            if (empty) {
                return 0;
            }

            int bytesRead;

            // Data se přečtou v jednom kuse
            if (start < end) {
                System.arraycopy(buffer, start, out, 0, end - start);
                bytesRead = end - start;

                // Data se přečtou ve dvou částech
            } else {
                int destPos = 0;

                // Přečíst od start do konce
                if (start < size) {
                    System.arraycopy(buffer, start, out, destPos, size - start);
                    destPos = size - start;
                }

                // Přečíst od začátku do end
                System.arraycopy(buffer, 0, out, destPos, end);
                bytesRead = destPos + end;
            }

            // Buffer je teď prázdný
            start = 0;
            end = 0;
            empty = true;
            return bytesRead;
        }
    }
}