import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class MiniChatClientGUI extends JFrame {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 12345;
    
    private static final Font UNICODE_FONT = new Font("Arial Unicode MS", Font.PLAIN, 13);
    
    private JTextArea chatArea;
    private JTextField messageField;
    private JTextField usernameField;
    private JButton connectButton;
    private JButton sendButton;
    private JComboBox<String> commandBox;
    
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    
    public MiniChatClientGUI() {
        setTitle("MiniChat Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLayout(new BorderLayout());
        
        System.setProperty("file.encoding", "UTF-8");
        
        // Panel đăng nhập với font Unicode
        JPanel loginPanel = new JPanel();
        usernameField = new JTextField(15);
        usernameField.setFont(UNICODE_FONT);
        connectButton = new JButton("Ket noi");
        connectButton.setFont(UNICODE_FONT);
        JLabel userLabel = new JLabel("Ten nguoi dung: ");
        userLabel.setFont(UNICODE_FONT);
        loginPanel.add(userLabel);
        loginPanel.add(usernameField);
        loginPanel.add(connectButton);
        
        // Khu vực chat với font Unicode
        chatArea = new JTextArea();
        chatArea.setFont(UNICODE_FONT);
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        
        // Panel nhắn tin với font Unicode
        JPanel messagePanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.setFont(UNICODE_FONT);
        sendButton = new JButton("Gui");
        sendButton.setFont(UNICODE_FONT);
        sendButton.setEnabled(false);
        
        // Dropdown commands
        String[] commands = {
            "Tin nhan thuong",
            "/sendUser - Gui tin nhan rieng",
            "/create - Tao nhom moi",
            "/join - Tham gia nhom",
            "/leave - Roi nhom",
            "/sendGroup - Gui tin nhan nhom",
            "/listUsers - Xem danh sach nguoi dung",
            "/listGroups - Xem danh sach nhom"
        };
        commandBox = new JComboBox<>(commands);
        
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(commandBox, BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        messagePanel.add(inputPanel);
        
        // Thêm các components vào frame
        add(loginPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(messagePanel, BorderLayout.SOUTH);
        
        // Xử lý sự kiện
        setupEventHandlers();
    }
    
    private void setupEventHandlers() {
        connectButton.addActionListener(e -> connectToServer());
        
        sendButton.addActionListener(e -> sendMessage());
        
        messageField.addActionListener(e -> sendMessage());
        
        commandBox.addActionListener(e -> {
            String selected = (String) commandBox.getSelectedItem();
            if (selected.startsWith("/")) {
                messageField.setText(selected.split(" - ")[0] + " ");
            } else {
                messageField.setText("");
            }
        });
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });
    }
    
    private void connectToServer() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui long nhap ten nguoi dung!");
            return;
        }
        
        try {
            socket = new Socket(SERVER_ADDRESS, PORT);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            
            // Đọc yêu cầu username từ server
            String serverPrompt = in.readLine();
            out.println(username);
            
            // Bắt đầu thread đọc tin nhắn từ server
            new Thread(this::readMessages).start();
            
            // Cập nhật UI
            usernameField.setEnabled(false);
            connectButton.setEnabled(false);
            sendButton.setEnabled(true);
            messageField.setEnabled(true);
            
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Khong the ket noi den server: " + ex.getMessage());
        }
    }
    
    private void readMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                final String finalMessage = message;
                SwingUtilities.invokeLater(() -> {
                    chatArea.append(finalMessage + "\n");
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                });
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Mat ket noi voi server!");
                });
            }
        }
    }
    
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            out.println(message);
            messageField.setText("");
        }
    }
    
    private void disconnect() {
        try {
            if (out != null) {
                out.println("/quit");
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        // Thiết lập look and feel của hệ thống
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            new MiniChatClientGUI().setVisible(true);
        });
    }
}