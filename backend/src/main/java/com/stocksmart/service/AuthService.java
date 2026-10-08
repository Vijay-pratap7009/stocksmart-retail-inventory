package com.stocksmart.service;

import com.stocksmart.dto.AuthResponse;
import com.stocksmart.dto.LoginRequest;
import com.stocksmart.entity.User;
import com.stocksmart.mapper.UserMapper;
import com.stocksmart.repository.UserRepository;
import com.stocksmart.security.jwt.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login workflow: Spring Security verifies username/BCrypt password,
 * then a JWT is issued. Failures surface as 401 via the exception handler.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            UserMapper userMapper) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        String token = jwtService.generateToken(
                user.getUsername(), user.getId(), user.getRole().getName().name());

        log.info("Authentication success for user '{}'", user.getUsername());
        return userMapper.toAuthResponse(user, token);
    }
}
