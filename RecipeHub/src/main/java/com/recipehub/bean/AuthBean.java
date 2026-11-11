package com.recipehub.bean;

import com.recipehub.util.DBConnection;
import org.mindrot.jbcrypt.BCrypt;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.sql.*;

@Named("authBean")
@SessionScoped
public class AuthBean implements Serializable {
    private Integer userId;
    private String username;
    private String email;
    private String password;
    private String role;
    private boolean loggedIn = false;
    private String errorMsg;

    public String login() {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            this.errorMsg = "กรุณากรอกชื่อผู้ใช้และรหัสผ่าน";
            return null;
        }

        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM users WHERE username = ? OR email = ?"
            );
            stmt.setString(1, username.trim());
            stmt.setString(2, username.trim());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password");
                if (BCrypt.checkpw(password, storedHash)) {
                    this.userId = rs.getInt("id");
                    this.username = rs.getString("username");
                    this.role = rs.getString("role");
                    this.loggedIn = true;
                    this.errorMsg = null;
                    return "index?faces-redirect=true";
                }
            }
            this.errorMsg = "ชื่อผู้ใช้งานหรือรหัสผ่านไม่ถูกต้อง";
        } catch (Exception e) {
            this.errorMsg = "เกิดข้อผิดพลาด: " + e.getMessage();
        }
        return null;
    }

    public String register() {
        if (username == null || email == null || password == null || 
            username.trim().isEmpty() || email.trim().isEmpty() || password.trim().isEmpty()) {
            this.errorMsg = "กรุณากรอกข้อมูลให้ครบทุกช่อง";
            return null;
        }

        try (Connection conn = DBConnection.getConnection()) {
            String hash = BCrypt.hashpw(password, BCrypt.gensalt());
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO users (username, email, password, role) VALUES (?, ?, ?, 'member')",
                Statement.RETURN_GENERATED_KEYS
            );
            stmt.setString(1, username.trim());
            stmt.setString(2, email.trim());
            stmt.setString(3, hash);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                this.userId = rs.getInt(1);
                this.role = "member";
                this.loggedIn = true;
                this.errorMsg = null;
                return "index?faces-redirect=true";
            }
        } catch (Exception e) {
            this.errorMsg = "Username หรือ Email นี้ถูกใช้งานแล้ว";
        }
        return null;
    }

    public String logout() {
        this.loggedIn = false;
        this.userId = null;
        this.username = null;
        this.role = null;
        this.password = null;
        return "index?faces-redirect=true";
    }

    public boolean isAdmin() {
        return "admin".equals(this.role);
    }

    // Getters & Setters
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isLoggedIn() { return loggedIn; }
    public void setLoggedIn(boolean loggedIn) { this.loggedIn = loggedIn; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
}