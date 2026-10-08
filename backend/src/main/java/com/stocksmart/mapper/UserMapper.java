package com.stocksmart.mapper;

import com.stocksmart.dto.AuthResponse;
import com.stocksmart.entity.User;
import org.springframework.stereotype.Component;

/**
 * Keeps entities out of REST payloads. Only safe fields cross the boundary.
 */
@Component
public class UserMapper {

    public AuthResponse toAuthResponse(User user, String token) {
        return new AuthResponse(
                token,
                user.getUsername(),
                user.getRole().getName().name(),
                user.getFullName()
        );
    }
}
