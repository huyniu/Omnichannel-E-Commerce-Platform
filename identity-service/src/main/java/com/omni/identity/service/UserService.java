package com.omni.identity.service;

import com.omni.common.exception.BusinessException;
import com.omni.identity.domain.Address;
import com.omni.identity.domain.Role;
import com.omni.identity.domain.User;
import com.omni.identity.repository.AddressRepository;
import com.omni.identity.repository.RefreshTokenRepository;
import com.omni.identity.repository.UserRepository;
import com.omni.identity.web.Dtos.AddressRequest;
import com.omni.identity.web.Dtos.AddressResponse;
import com.omni.identity.web.Dtos.ChangePasswordRequest;
import com.omni.identity.web.Dtos.UpdateProfileRequest;
import com.omni.identity.web.Dtos.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final AddressRepository addresses;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(load(id));
    }

    @Transactional
    public UserResponse updateProfile(Long id, UpdateProfileRequest req) {
        User user = load(id);
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long id, ChangePasswordRequest req) {
        User user = load(id);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("WRONG_PASSWORD", "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        refreshTokens.revokeAllForUser(id);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(Long userId) {
        return addresses.findByUserIdOrderByDefaultAddressDescIdAsc(userId).stream().map(AddressResponse::from).toList();
    }

    @Transactional
    public AddressResponse addAddress(Long userId, AddressRequest req) {
        Address address = new Address();
        address.setUserId(userId);
        boolean first = addresses.countByUserId(userId) == 0;
        apply(address, req, first || req.isDefault());
        return AddressResponse.from(addresses.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest req) {
        Address address = addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> BusinessException.notFound("Address"));
        apply(address, req, req.isDefault() || address.isDefaultAddress());
        return AddressResponse.from(address);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> BusinessException.notFound("Address"));
        addresses.delete(address);
    }

    @Transactional(readOnly = true)
    public Page<User> list(Pageable pageable) {
        return users.findAll(pageable);
    }

    @Transactional
    public UserResponse updateRoles(Long id, Set<Role> roles) {
        User user = load(id);
        user.setRoles(new HashSet<>(roles));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateStatus(Long id, User.Status status) {
        User user = load(id);
        user.setStatus(status);
        if (status == User.Status.LOCKED) {
            refreshTokens.revokeAllForUser(id);
        }
        return UserResponse.from(user);
    }

    private void apply(Address address, AddressRequest req, boolean makeDefault) {
        if (makeDefault && !address.isDefaultAddress()) {
            addresses.clearDefault(address.getUserId());
        }
        address.setReceiverName(req.receiverName());
        address.setPhone(req.phone());
        address.setLine(req.line());
        address.setWard(req.ward());
        address.setDistrict(req.district());
        address.setCity(req.city());
        address.setDefaultAddress(makeDefault);
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> BusinessException.notFound("User"));
    }
}
