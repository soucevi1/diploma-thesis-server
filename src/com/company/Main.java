
package com.company;

import java.util.Scanner;

class Server {
    private static ServerThread thread = new ServerThread();

    public static void main(String args[]) throws Exception {

        thread.start();

        showHelp();

        Scanner input = new Scanner(System.in);

        boolean status = true;

        while (status) {
            String connection = "";
            System.out.print("> ");
            String option = input.next();
            switch (option) {
                case "l":
                    listConnections();
                    break;
                case "s":
                    connection = input.next();
                    switchToConnection(connection);
                    break;
                case "r":
                    connection = input.next();
                    removeConnection(connection);
                    break;
                case "q":
                    status = false;
                    thread.status = false;
                    break;
                case "h":
                    showHelp();
                    break;
                default:
                    System.out.println("Unknown command: " + option);
            }
        }
        System.out.println("Exiting application");
    }

    /**
     * Method removes the given connection from the thread's list.
     *
     * If the connection was active at the time, it deletes the active
     * connection of the thread (replaces it with an empty string).
     * @param connection Connection to be removed
     */
    private static void removeConnection(String connection) {

        if(connection.equals("active") || connection.equals("a")){
            thread.connections.remove(thread.activeConnection);
            thread.activeConnection = "";
            System.out.println("[-] Connection " + connection + " removed.");
            return;
        }

        if (checkConnection(connection)) {

            thread.connections.remove(connection);
            System.out.println("[-] Connection " + connection + " removed.");

            if (thread.activeConnection.equals(connection)) {
                thread.activeConnection = "";
            }
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
     * Inspired by: https://stackoverflow.com/a/30691451/6136143
     * @param connection Connection to be validated
     * @return True if the connection is valid, false otherwise
     */
    private static boolean checkConnection(String connection) {
        String[] split_conn = connection.split(":");

        if (split_conn.length != 2) {
            System.out.println("Connection in wrong format: " + connection + ". Should be: IP:port");
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
     * @param connection Connection to be marked as active
     */
    private static void switchToConnection(String connection) {
        if (checkConnection(connection)) {
            System.out.println("[-] Connection " + connection + " set as active. Replaced " + thread.activeConnection);
            thread.activeConnection = connection;
        }
    }

    /**
     * Print the whole connection list of the thread,
     * so that the user can choose which one to make active.
     */
    private static void listConnections() {
        System.out.println("----------------------------------------");
        System.out.println("Active connection: " + thread.activeConnection);
        System.out.println("----------------------------------------");
        System.out.println("Other connections: ");
        for(String connection : thread.connections){
            if(!connection.equals(thread.activeConnection))
                System.out.println(" - " + connection);
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
        System.out.println("r <CONNECTION>: Remove selected connection");
        System.out.println("                Once you receive no data from a connection, you can manually");
        System.out.println("                  remove it from the list.");
        System.out.println("                Example: r 192.168.1.100:12345");
        System.out.println("");
        System.out.println("q: Quit this application.");
        System.out.println("");
        System.out.println("h: Show this help");
        System.out.println("");
    }
}

