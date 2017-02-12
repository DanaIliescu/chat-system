import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static ServerSocket serverSocket;
    private static final int PORT = 7777;
    public static ConcurrentHashMap<String, User> users;
    public static ArrayList<Socket> clientSockets;

    public static void main(String[] args) throws IOException {
        try {
            serverSocket = new ServerSocket(PORT);
            users = new ConcurrentHashMap<>();
            clientSockets = new ArrayList<>();

        } catch (IOException ioEx) {
            System.out.println("\nUnable to set up port!");
            System.exit(1);
        }

        System.out.println("Server waiting for Clients on port " + PORT + ".");

        Thread alveHandler = new Thread(() -> {
            while(true) {
                long currentTime = System.currentTimeMillis();
                DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");  //for printing the time on the console
                Calendar cal = Calendar.getInstance();

                for (String username: users.keySet()) {
                    if (users.get(username).getTime() + 62000 < currentTime){
                        System.out.println(dateFormat.format(cal.getTime()) + " Timeout for " + username);

                        for (int i = 0; i < clientSockets.size(); i++) {    //find the associated socket and remove it
                            if(clientSockets.get(i).getInetAddress() == users.get(username).getInetAddress()) {
                                try {
                                    clientSockets.get(i).close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                clientSockets.remove(i);
                            }
                        }
                        users.remove(username);  //remove user
                    }

                }
            }

        });
        alveHandler.start();

        //wait for client
        while(true) {
            Socket client = serverSocket.accept();
            System.out.println(client.toString());

            //create a thread to handle communication with his client and pass a reference to the relevant socket
            ClientHandler handler = new ClientHandler(client);
            handler.start();
        }
    }
}

class ClientHandler extends Thread {

    private Socket client;
    private DataInputStream input;
    private DataOutputStream output;
    private String username;

    public ClientHandler(Socket socket) {
        //set up reference to associated socket
        client = socket;
        try{
            input = new DataInputStream(client.getInputStream());
            output = new DataOutputStream(client.getOutputStream());
        }catch(IOException ioEx) {
            ioEx.printStackTrace();
        }
    }

    public void run() {
        String received;
        while (true ) {
            //accept message from client on the socket's input stream
            try {
                received = input.readUTF();
            } catch (IOException e) {
                removeSocket(client);
                if (username != null)
                    removeUser(username);
                System.out.println(username + " was removed.");
                break;
            }

            //assign value to "action" depending of the message received
            int action = 0;
            if (received.startsWith("JOIN")) {
                action = 1;
            }
            if (received.startsWith("DATA")) {
                action = 2;
            }
            if (received.equals("QUIT")) {
                action = 3;
            }
            if (received.equals("ALVE")) {
                action = 4;
            }

            System.out.println("> Message: " + received);

            switch (action) {
                case 1: {
                    username = getUsername(received);
                    if (verifyUsername(getUsername(received))) {  //check for valid format
                        if (Server.users.containsKey(getUsername(received))) { //check for duplicates
                            try {
                                output.writeUTF("J_ERR " + username + ", you idiot!"); //if duplicate, send "J_ERR"
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            username = getUsername(received);
                            Server.users.put(username, new User(client.getInetAddress(), client.getPort(), System.currentTimeMillis()));
                            Server.clientSockets.add(client);
                            try {
                                output.writeUTF("J_OK");    // if not duplicate, add the client to the list and write "J_OK"
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            System.out.println("New client accepted.\n");
                            broadcast("LIST " + getList(Server.users));   //broadcast list with active users to all active users
                        }
                    } else {
                        try {
                            output.writeUTF("J_ERR " + username + ", you idiot!");
                            client.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
                case 2: {
                    if (received.substring(5).length() < 250)  //check if message is less than 250 characters
                    {
                        broadcast(received);   //broadcast "DATA" messages to all active users
                    }
                    else {
                        try {
                            output.writeUTF("J_ERR");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
                case 3: {
                    removeSocket(client);   //remove client
                    removeUser(username);
                    broadcast("LIST " + getList(Server.users)); //broadcast list with the remaining active users
                    try {
                        client.close();
                    } catch (IOException e) {
                        break;
                    }
                }
                break;
                case 4:{
                    DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");  //for printing the time on the console
                    Calendar cal = Calendar.getInstance();

                    Server.users.get(username).setTime(cal.getTimeInMillis());
                    System.out.println(dateFormat.format(cal.getTime()) + ": " +  received + " from " + username);
                }
                break;
            }

            try {
                output.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void broadcast(String message){    //broadcast message to all active users
        for (Socket socket: Server.clientSockets) {
            DataOutputStream out;
            if(socket != null){
                try {
                    out = new DataOutputStream(socket.getOutputStream());
                    out.writeUTF(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public synchronized void removeSocket(Socket socket){
        Server.clientSockets.remove(socket);
    } //remove socket

    public synchronized void removeUser(String username){
        Server.users.remove(username);
    } //remove user object

    public String getUsername(String received){ //extract the username from the "JOIN" message
        return received.substring(5, received.indexOf(','));
    }

    public boolean verifyUsername(String username){  //verify username format - only numbers and letters, "-" and "_" allowed, max 12 chars
        if(username.length() > 12)
            return false;
        for(int i = 0; i < username.length(); i++){
            char c = username.charAt(i);
            if (!Character.isLetterOrDigit(c))
                if(c != '-')
                    if(c != '_')
                        return false;
        }
        return true;
    }

    public String getList(ConcurrentHashMap<String, User> users){ //get a string with all usernames, used for broadcasting all active users
        String list = "";
        for (String username: users.keySet()) {
            list = list + " " + username;
        }
        return list;
    }
}
//User class with IP and port of the client
class User{
    InetAddress addr;
    int port;
    long time;

    User(InetAddress a, int p, long t){
        addr = a;
        port = p;
        time = t;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public InetAddress getInetAddress() {
        return addr;
    }
}


