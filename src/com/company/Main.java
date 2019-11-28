
package com.company;

import java.util.Scanner;

class Server {
    static ReceivingThread thread = new ReceivingThread();

    public static void main(String args[]) throws Exception {

        thread.start();

        showHelp();

        Scanner input = new Scanner(System.in);

        boolean status = true;

        while (status) {
            String option = input.next();
            switch (option.charAt(0)) {
                case 'l':
                    listConnections(thread);
                    break;
                case 's':
                    switchToConnection(option.substring(option.length() - 2), thread);
                    break;
                case 'r':
                    removeConnection(option.substring(option.length() - 2), thread);
                    break;
                case 'q':
                    status = false;
                    thread.status = false;
                    break;
                case 'h':
                    showHelp();
                    break;
                default:
                    System.out.println("Unknown command: " + option);
            }
        }
        System.out.println("Exiting application");
    }

    private static void removeConnection(String connection, ReceivingThread t) {
        /**
         * Method removes the given connection from the thread's list.
         *
         * If the connection was active at the time, it deletes the active
         * connection of the thread (replaces it with an empty string).
         */
        if (checkConnection(connection)) {

            t.connections.remove(connection);

            if (t.activeConnection.equals(connection)) {
                t.activeConnection = "";
            }
        }
    }

    private static boolean checkConnection(String connection) {
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
         */
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

    private static void switchToConnection(String connection, ReceivingThread t) {
        /**
         * Switch the active connection of the thread to the one
         * given as an argument.
         *
         * The connection is validated first.
         */
        if (checkConnection(connection)) {
            t.activeConnection = connection;
        }
    }

    private static void listConnections(ReceivingThread t) {
        /**
         * Print the whole connection list of the thread,
         * so that the user can choose which one to make active.
         */
        System.out.println(t.connections.toString().replace("[", "\n\t["));
    }

    private static void showHelp() {
        /**
         * Show the usage message.
         */
        System.out.println("Usage:");
        System.out.println("");
        System.out.println("l: List all available connections");
        System.out.println("   This option lists all connection that are known to the server. The server automatically saves a connection to the list when it receives some data from it.");
        System.out.println("");
        System.out.println("s <CONNECTION>: Switch to chosen connection");
        System.out.println("                Make this connection active -- data from this connection will be played on the speaker.");
        System.out.println("                Example: s 192.168.1.100:12345");
        System.out.println("");
        System.out.println("r <CONNECTION>: Remove selected connection");
        System.out.println("                Once you receive no data from a connection, you can manually remove it from the list.");
        System.out.println("                Example: r 192.168.1.100:12345");
        System.out.println("");
        System.out.println("q: Quit this application.");
        System.out.println("");
        System.out.println("h: Show this help");
    }
}

