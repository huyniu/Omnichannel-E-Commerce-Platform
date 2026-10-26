package com.omni.identity.web;

import com.omni.identity.domain.Address;
import com.omni.identity.domain.Role;
import com.omni.identity.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public final class Dtos {

    private Dtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
            @NotBlank @Size(max = 255) String fullName,
            @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "invalid phone number") String phone) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
                                UserResponse user) {
    }

    public record UserResponse(Long id, String email, String fullName, String phone, String status,
                               Set<Role> roles, Instant createdAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getStatus().name(),
                    u.getRoles(), u.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 255) String fullName,
            @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "invalid phone number") String phone) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record AddressRequest(
            @NotBlank String receiverName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "invalid phone number") String phone,
            @NotBlank String line,
            String ward,
            String district,
            @NotBlank String city,
            boolean isDefault) {
    }

    public record AddressResponse(Long id, String receiverName, String phone, String line, String ward,
                                  String district, String city, boolean isDefault) {
        public static AddressResponse from(Address a) {
            return new AddressResponse(a.getId(), a.getReceiverName(), a.getPhone(), a.getLine(), a.getWard(),
                    a.getDistrict(), a.getCity(), a.isDefaultAddress());
        }
    }

    public record UpdateRolesRequest(@NotEmpty Set<Role> roles) {
    }

    public record UpdateStatusRequest(@jakarta.validation.constraints.NotNull User.Status status) {
    }
}
