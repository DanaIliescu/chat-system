import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static InetAddress host;
    private static int PORT;
    private static Socket socket;

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        try {
            System.out.print("Enter ip: ");         //ask for server's IP
            host = InetAddress.getByName(in.nextLine());

            System.out.print("Enter port number: "); //ask for the server's port
            PORT = Integer.parseInt(in.nextLine());

            socket = new Socket(host, PORT);
        } catch (Exception e) {
            System.out.println("\nHost ID not found!\n");
            System.exit(1);
        }
        SendToServer sendToServer = new SendToServer(socket);  //create thread that deals with sending data to the server
        sendToServer.start();

        ListenFromServer listenFromServer = new ListenFromServer(socket); //create thread that deals with receiving data from the server
        listenFromServer.start();
    }
}

class SendToServer extends Thread {
    Socket client;
    DataOutputStream output;
    Scanner userEntry;

    public SendToServer(Socket socket) {
        client = socket;
        userEntry = new Scanner(System.in);
        try {
            output = new DataOutputStream(client.getOutputStream());
        } catch (IOException ioEx) {
            ioEx.printStackTrace();
        }
    }

    public void run() {
        String message, username;

        System.out.print("Enter username: ");      //ask for username
        username = userEntry.nextLine();
        try {
            //send "JOIN" to the server with necessary information
            output.writeUTF("JOIN " + username + ", " + client.getInetAddress().getHostAddress() + ":" + client.getPort());
            sendAlveMessage(output);  //method will start a thread that sends the "ALVE" message every 60 seconds
            while (true) {
                System.out.print("Enter message (\"QUIT\" to exit): ");
                message = userEntry.nextLine();

                if (message.equals("QUIT")) {
                    output.writeUTF(message);
                    break;
                } else {
                    if (message.length() < 250)  //check if the message exceeds 250 chars, if yes - it is not sent
                        output.writeUTF("DATA " + username + ": " + message);
                    else System.out.println("Message exceeds 250 chars. Message was not sent.");
                }
                output.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    public static void sendAlveMessage(DataOutputStream output){ //create and start thread that sends "ALVE" every 60 seconds
        Thread alve = new Thread(() -> {
            while (true) {
                try {
                    output.writeUTF("ALVE");
                    output.flush();
                    Thread.sleep(60000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        alve.start();
    }
}

class ListenFromServer extends Thread {
    Socket client;
    DataInputStream input;

    public ListenFromServer(Socket socket) {
        client = socket;
        try {
            input = new DataInputStream(client.getInputStream());
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public void run() {
        while (true) {
            String response;
            try {
                response = input.readUTF();
            } catch (IOException e) {
                break;
            }
            System.out.println();
            if(response.startsWith("DATA"))
                System.out.println(response.substring(5));
            else
                System.out.println("> Server: " + response);
            if(response.equals("J_ERR")) {
                System.out.println("You got disconnected.");
                System.exit(1);
            }
        }
    }
}
