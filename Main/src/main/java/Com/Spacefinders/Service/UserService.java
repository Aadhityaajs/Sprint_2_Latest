package Com.Spacefinders.Service;

import Com.Spacefinders.DTO.Request.*;
import Com.Spacefinders.DTO.Response.*;
import Com.Spacefinders.Entity.Audit;
import Com.Spacefinders.Entity.User;
import Com.Spacefinders.Enums.*;
import Com.Spacefinders.Exception.*;
import Com.Spacefinders.Repository.AuditRepository;
import Com.Spacefinders.Repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditRepository auditRepository;

    // FIXED: Added password encoder for secure password hashing
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private HttpServletRequest httpServletRequest;

    // FIXED: Added @Transactional for data consistency
    @Transactional
    public UserResponse addUser(UserSignupRequest request) {
        // Check if username already exists
        User existingUser = userRepository.findByUsername(request.getUsername());
        if (existingUser != null) {
            throw new DuplicateResourceException("Username already exists");
        }

        // Check if email already exists
        User existingEmail = userRepository.findByUserMail(request.getUserMail());
        if (existingEmail != null) {
            throw new DuplicateResourceException("Email already registered");
        }

        // Check if phone already exists
        User existingPhone = userRepository.findByUserPhone(request.getUserPhone());
        if (existingPhone != null) {
            throw new DuplicateResourceException("Phone number already registered");
        }

        // Create new user
        User user = new User();
        user.setUsername(request.getUsername());

        // FIXED: Encrypt password before storing
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setUserMail(request.getUserMail());
        user.setUserPhone(request.getUserPhone());
        user.setUserAddress(request.getUserAddress());
        user.setUserRole(UserRole.valueOf(request.getUserRole()));
        user.setUserStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        // FIXED: Capture actual IP address
        createAuditLog(savedUser.getUserId(), ActionType.CREATE, "User registered successfully", getClientIp());

        return convertToUserResponse(savedUser);
    }

    // FIXED: Added @Transactional
    @Transactional
    public LoginResponse loginUser(UserLoginRequest request) {
        // Check if user exists
        User user = userRepository.findByUsername(request.getUsername());
        if (user == null) {
            throw new UserNotFoundException("User not found. Please register first.");
        }

        // FIXED: Use password encoder to verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        // Check user status
        if (user.getUserStatus() == UserStatus.BLOCKED) {
            throw new UnauthorizedException("Your account is blocked. Contact admin.");
        }

        if (user.getUserStatus() == UserStatus.DELETED) {
            throw new UserNotFoundException("Account not found");
        }

        // FIXED: Capture actual IP address
        createAuditLog(user.getUserId(), ActionType.LOGIN, "User logged in", getClientIp());

        // Create login response
        LoginResponse response = new LoginResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getUserMail());
        response.setRole(user.getUserRole().toString());

        return response;
    }

    // View Profile
    @Transactional(readOnly = true)
    public UserResponse viewProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return convertToUserResponse(user);
    }

    // FIXED: Added @Transactional
    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Check if new email already exists (and it's not the current user's email)
        if (!user.getUserMail().equals(request.getUserMail())) {
            User existingEmail = userRepository.findByUserMail(request.getUserMail());
            if (existingEmail != null) {
                throw new DuplicateResourceException("Email already registered");
            }
        }

        // Check if new phone already exists (and it's not the current user's phone)
        if (!user.getUserPhone().equals(request.getUserPhone())) {
            User existingPhone = userRepository.findByUserPhone(request.getUserPhone());
            if (existingPhone != null) {
                throw new DuplicateResourceException("Phone number already registered");
            }
        }

        // Update user details
        user.setUserMail(request.getUserMail());
        user.setUserPhone(request.getUserPhone());
        user.setUserAddress(request.getUserAddress());

        User updatedUser = userRepository.save(user);

        // FIXED: Capture actual IP address
        createAuditLog(userId, ActionType.UPDATE, "User profile updated", getClientIp());

        return convertToUserResponse(updatedUser);
    }

    // FIXED: Added @Transactional
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.getUserStatus() == UserStatus.DELETED) {
            throw new InvalidOperationException("User is already deleted");
        }

        // Soft delete
        user.setUserStatus(UserStatus.DELETED);
        userRepository.save(user);

        // FIXED: Capture actual IP address
        createAuditLog(userId, ActionType.DELETE, "User account deleted", getClientIp());
    }

    // FIXED: Added @Transactional
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByUsername(request.getUsername());
        if (user == null) {
            throw new UserNotFoundException("User not found");
        }

        // FIXED: Use password encoder to verify old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Old password is incorrect");
        }

        // FIXED: Encrypt new password before storing
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // FIXED: Capture actual IP address
        createAuditLog(user.getUserId(), ActionType.UPDATE, "Password reset successfully", getClientIp());
    }

    // FIXED: Added @Transactional
    @Transactional
    public void logoutUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // FIXED: Capture actual IP address
        createAuditLog(userId, ActionType.LOGOUT, "User logged out", getClientIp());
    }

    // Helper method to convert User to UserResponse
    private UserResponse convertToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setUserMail(user.getUserMail());
        response.setUserPhone(user.getUserPhone());
        response.setUserAddress(user.getUserAddress());
        response.setUserRole(user.getUserRole().toString());
        response.setUserStatus(user.getUserStatus().toString());
        return response;
    }

    // FIXED: Helper method to create audit log with proper IP capture
    private void createAuditLog(Long userId, ActionType actionType, String description, String ipAddress) {
        Audit audit = new Audit();
        audit.setUserId(userId);
        audit.setTimestamp(LocalDateTime.now());
        audit.setAuditDescription(description);
        audit.setActionType(actionType);
        audit.setIpAddress(ipAddress);
        auditRepository.save(audit);
    }

    // FIXED: New helper method to get client IP address
    private String getClientIp() {
        if (httpServletRequest == null) {
            return "unknown";
        }

        String xForwardedFor = httpServletRequest.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = httpServletRequest.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return httpServletRequest.getRemoteAddr();
    }
}