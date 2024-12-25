import java.io.*;
import java.net.*;
import java.util.*;

public class MiniChatServer {
    private static final int PORT = 12345;
    private static Map<String, ClientHandler> clients = new HashMap<>();
    private static Map<String, List<ClientHandler>> groups = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("MiniChat Server is running...");
            
            // Khởi tạo nhóm mặc định
            if (!groups.containsKey("defaultGroup")) {
                groups.put("defaultGroup", new ArrayList<>());
            }
    
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(socket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static synchronized boolean hasClient(String username) {
        return clients.containsKey(username);
    }

    public static synchronized void addClient(String username, ClientHandler clientHandler) {
        clients.put(username, clientHandler);
        joinGroup("defaultGroup", clientHandler);
    }
    
    public static synchronized void removeClient(String username) {
        clients.remove(username);
    }

    public static synchronized String getUsersList() {
        if (clients.isEmpty()) {
            return "No users are currently connected.";
        }
        return "Connected users: " + String.join(", ", clients.keySet());
    }

    public static synchronized String getGroupsList() {
        if (groups.isEmpty()) {
            return "No groups have been created.";
        }
        return "Available groups: " + String.join(", ", groups.keySet());
    }

    public static synchronized void createGroup(String groupName, ClientHandler creator) {
        groups.putIfAbsent(groupName, new ArrayList<>());
        groups.get(groupName).add(creator);
    }

    public static synchronized void joinGroup(String groupName, ClientHandler clientHandler) {
        if (groups.containsKey(groupName)) {
            List<ClientHandler> groupMembers = groups.get(groupName);
    
            if (groupMembers.contains(clientHandler)) {
                clientHandler.sendMessage("You are already in the group: " + groupName);
            } else {
                // Xóa khỏi defaultGroup nếu người dùng đang ở trong đó
                if (groups.get("defaultGroup").contains(clientHandler)) {
                    groups.get("defaultGroup").remove(clientHandler);
                    clientHandler.sendMessage("You have left the default group.");
                }
    
                groupMembers.add(clientHandler);
                clientHandler.sendMessage("You have joined the group: " + groupName);
            }
        } else {
            clientHandler.sendMessage("Group does not exist.");
        }
    }
    
    public static synchronized void sendMessageToGroup(String groupName, String message, ClientHandler sender) {
    if (groups.containsKey(groupName)) {
        String fullMessage = "[" + groupName + "] " + sender.getUsername() + ": " + message;
        
        // Nếu là default group, chỉ gửi cho những người trong default group
        if (groupName.equals("defaultGroup")) {
            List<ClientHandler> defaultGroupMembers = groups.get("defaultGroup");
            for (ClientHandler client : defaultGroupMembers) {
                // Kiểm tra xem client có phải là thành viên của bất kỳ nhóm nào khác không
                boolean isInOtherGroups = false;
                for (Map.Entry<String, List<ClientHandler>> entry : groups.entrySet()) {
                    if (!entry.getKey().equals("defaultGroup") && entry.getValue().contains(client)) {
                        isInOtherGroups = true;
                        break;
                    }
                }
                // Chỉ gửi tin nhắn nếu client không ở trong nhóm nào khác
                if (!isInOtherGroups) {
                    client.sendMessage(fullMessage);
                }
            }
        } else {
            // Đối với các nhóm khác, gửi tin nhắn cho tất cả thành viên như bình thường
            for (ClientHandler client : groups.get(groupName)) {
                client.sendMessage(fullMessage);
            }
        }
    } else {
        sender.sendMessage("Group does not exist.");
    }
}

    public static synchronized void leaveGroup(String groupName, ClientHandler clientHandler) {
        if (groups.containsKey(groupName)) {
            List<ClientHandler> groupMembers = groups.get(groupName);
            
            if (groupMembers.contains(clientHandler)) {
                groupMembers.remove(clientHandler);
                clientHandler.sendMessage("You have left the group: " + groupName);
                
                // Nếu nhóm không còn thành viên nào, có thể xóa nhóm (tùy chọn)
                if (groupMembers.isEmpty()) {
                    groups.remove(groupName);
                    System.out.println("Group " + groupName + " has been removed as it has no members.");
                }
            } else {
                clientHandler.sendMessage("You are not a member of the group: " + groupName);
            }
        } else {
            clientHandler.sendMessage("Group does not exist.");
        }
    }
    
    public static synchronized void sendMessageToUser(String recipient, String message, ClientHandler sender) {
        ClientHandler client = clients.get(recipient);
        if (client != null) {
            client.sendMessage(message);
        } else {
            sender.sendMessage("User not found.");
        }
    }

    public static synchronized boolean isUserInOtherGroups(ClientHandler client) {
        for (Map.Entry<String, List<ClientHandler>> entry : groups.entrySet()) {
            if (!entry.getKey().equals("defaultGroup") && entry.getValue().contains(client)) {
                return true;
            }
        }
        return false;
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;

    }

    private void closeConnection() {
        try {
            MiniChatServer.removeClient(username);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getUsername() {
        return username;
    }
    
    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
    
            while (true) {
                out.println("Enter your username:");
                username = in.readLine();
    
                if (username == null) {
                    return; // Thoát nếu client ngắt kết nối
                }
    
                synchronized (MiniChatServer.class) {
                    if (!MiniChatServer.hasClient(username)) {
                        MiniChatServer.addClient(username, this);
                        break;
                    } else {
                        out.println("Username already taken. Please choose another username.");
                    }
                }
            }
    
            out.println("Welcome, " + username + "!");
    
            String message;
            while ((message = in.readLine()) != null) {
                handleCommand(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (username != null) {
                    MiniChatServer.removeClient(username);
                    System.out.println(username + " has disconnected.");
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void handleCommand(String command) {
        if (command.equalsIgnoreCase("/quit")) {
            sendMessage("Goodbye!");
            closeConnection();
        } else if (command.startsWith("/sendUser")) {
            String[] parts = command.split(" ", 3);
            String recipient = parts[1];
            String privateMessage = parts[2];
            MiniChatServer.sendMessageToUser(recipient, username + " (private): " + privateMessage, this);
        } else if (command.startsWith("/create")) {
            String groupName = command.split(" ")[1];
            MiniChatServer.createGroup(groupName, this);
            sendMessage("Group " + groupName + " created.");
        } else if (command.startsWith("/join")) {
            String groupName = command.split(" ")[1];
            MiniChatServer.joinGroup(groupName, this);
        } else if (command.startsWith("/leave")) {
            String groupName = command.split(" ")[1];
            MiniChatServer.leaveGroup(groupName, this);
        } else if (command.startsWith("/sendGroup")) {
            String[] parts = command.split(" ", 3);
            if (parts.length < 3) {
                sendMessage("Usage: /sendGroup groupName message");
                return;
            }
            String groupName = parts[1];
            String groupMessage = parts[2];
            MiniChatServer.sendMessageToGroup(groupName, groupMessage, this);
        } else if (command.equals("/")) {
            // Tin nhắn gửi vào nhóm defaultGroup mà không cần lệnh /sendDefault
            sendMessage("Please enter a message after '/'.");
        } else if (!command.startsWith("/") && !command.isEmpty()) {
            // Nếu người dùng không nhập lệnh (chỉ có tin nhắn) sẽ gửi vào defaultGroup
            if (MiniChatServer.isUserInOtherGroups(this)) {
                    sendMessage("You cannot send messages to default group while being in other groups.");
                return;
            }
            MiniChatServer.sendMessageToGroup("defaultGroup", ": " + command, this);
        } else if (command.startsWith("/listUsers")) {
            sendMessage(MiniChatServer.getUsersList());
        } else if (command.startsWith("/listGroups")) {
            sendMessage(MiniChatServer.getGroupsList());
        } else {
            sendMessage("Unknown command.");
        }
    }
     
    public void sendMessage(String message) {
        out.println(message);
    }
}
