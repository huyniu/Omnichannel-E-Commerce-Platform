package com.omni.identity.web;

import com.omni.common.api.ApiResponse;
import com.omni.common.api.PageResponse;
import com.omni.common.web.AuthUser;
import com.omni.common.web.CurrentUser;
import com.omni.identity.service.UserService;
import com.omni.identity.web.Dtos.AddressRequest;
import com.omni.identity.web.Dtos.AddressResponse;
import com.omni.identity.web.Dtos.ChangePasswordRequest;
import com.omni.identity.web.Dtos.UpdateProfileRequest;
import com.omni.identity.web.Dtos.UpdateRolesRequest;
import com.omni.identity.web.Dtos.UpdateStatusRequest;
import com.omni.identity.web.Dtos.UserResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@Parameter(hidden = true) @AuthUser CurrentUser user) {
        return ApiResponse.ok(userService.get(user.id()));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMe(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                              @Valid @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(userService.updateProfile(user.id(), req));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                            @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(user.id(), req);
        return ApiResponse.ok("Password changed, please log in again", null);
    }

    @GetMapping("/me/addresses")
    public ApiResponse<List<AddressResponse>> addresses(@Parameter(hidden = true) @AuthUser CurrentUser user) {
        return ApiResponse.ok(userService.listAddresses(user.id()));
    }

    @PostMapping("/me/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddressResponse> addAddress(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                   @Valid @RequestBody AddressRequest req) {
        return ApiResponse.ok(userService.addAddress(user.id(), req));
    }

    @PutMapping("/me/addresses/{id}")
    public ApiResponse<AddressResponse> updateAddress(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                      @PathVariable Long id, @Valid @RequestBody AddressRequest req) {
        return ApiResponse.ok(userService.updateAddress(user.id(), id, req));
    }

    @DeleteMapping("/me/addresses/{id}")
    public ApiResponse<Void> deleteAddress(@Parameter(hidden = true) @AuthUser CurrentUser user, @PathVariable Long id) {
        userService.deleteAddress(user.id(), id);
        return ApiResponse.ok("Deleted", null);
    }

    // ---- Administration (RBAC: ADMIN only) ----

    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> list(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        user.requireAdmin();
        var result = userService.list(PageRequest.of(page, Math.min(size, 100), Sort.by("id").descending()));
        return ApiResponse.ok(PageResponse.of(result, UserResponse::from));
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<UserResponse> updateRoles(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                 @PathVariable Long id, @Valid @RequestBody UpdateRolesRequest req) {
        user.requireAdmin();
        return ApiResponse.ok(userService.updateRoles(id, req.roles()));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<UserResponse> updateStatus(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                  @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        user.requireAdmin();
        return ApiResponse.ok(userService.updateStatus(id, req.status()));
    }
}
