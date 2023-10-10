import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 53463;
    private static final String BASE_MESSAGE_HISTORY_FILE = "message_history_room_";
    private static final int NUMBER_OF_ROOMS = 6;

    private static List<Set<PrintWriter>> roomClientWriters = new ArrayList<>(NUMBER_OF_ROOMS);
    static {
        for (int i = 0; i < NUMBER_OF_ROOMS; i++) {
            roomClientWriters.add(new HashSet<>());
        }
    }

    private static List<Set<String>> roomUserNames = new ArrayList<>(NUMBER_OF_ROOMS);
    static {
        for (int i = 0; i < NUMBER_OF_ROOMS; i++) {
            roomUserNames.add(new HashSet<>());
        }
    }

    public static void main(String[] args) {
        System.out.println("Chat server started...");
        try (ServerSocket listener = new ServerSocket(PORT)) {
            while (true) {
                new ClientHandler(listener.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized void saveMessage(String message, int room) {
        try (PrintWriter out = new PrintWriter(new FileWriter(BASE_MESSAGE_HISTORY_FILE + room + ".txt", true))) {
            out.println(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private int room;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                InputStream input = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));

                OutputStream output = socket.getOutputStream();
                out = new PrintWriter(output, true);

                
                room = Integer.parseInt(reader.readLine());
                username = reader.readLine();  // Recibiendo el nombre de usuario

                synchronized (roomUserNames.get(room)) {
                    roomUserNames.get(room).add(username);
                }

                synchronized (roomClientWriters.get(room)) {
                    roomClientWriters.get(room).add(out);
                }

                // Envia el historial de mensajes cuando se une un usuario
                List<String> messageHistory = loadMessageHistory(room);
                for (String message : messageHistory) {
                    out.println(message);
                }

                String message;
                while ((message = reader.readLine()) != null) {
                    System.out.println("Received from client: " + message);

                    if (message.startsWith("/getUsers:")) {
                        handleUsersCommand();
                        continue;
                    }

                    saveMessage(message, room);
                    synchronized (roomClientWriters.get(room)) {
                        for (PrintWriter writer : roomClientWriters.get(room)) {
                            writer.println(message);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (out != null) {
                    synchronized (roomClientWriters.get(room)) {
                        roomClientWriters.get(room).remove(out);
                    }
                }
                if (username != null) {
                    synchronized (roomUserNames.get(room)) {
                        roomUserNames.get(room).remove(username);
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                String disconnectMessage = username + " se ha desconectado de la sala.";
                synchronized (roomClientWriters.get(room)) {
                    for (PrintWriter writer : roomClientWriters.get(room)) {
                        writer.println(disconnectMessage);
                    }
                }
            }
        }

        private List<String> loadMessageHistory(int room) {
            List<String> messages = new ArrayList<>();
            File historyFile = new File(BASE_MESSAGE_HISTORY_FILE + room + ".txt");
            if (!historyFile.exists()) {
                try {
                    historyFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                    return messages;
                }
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(historyFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    messages.add(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return messages;
        }

        private void handleUsersCommand() {
            StringBuilder usersInRoom = new StringBuilder("Usuarios en la sala: ");
            synchronized (roomUserNames.get(room)) {
                for (String user : roomUserNames.get(room)) {
                    usersInRoom.append(user).append(", ");
                }
            }
            if (usersInRoom.length() > 18) { 
                usersInRoom.setLength(usersInRoom.length() - 2);
            }
            System.out.println("Sending to client: " + usersInRoom.toString());
            out.println(usersInRoom.toString());
        }
    }
}
