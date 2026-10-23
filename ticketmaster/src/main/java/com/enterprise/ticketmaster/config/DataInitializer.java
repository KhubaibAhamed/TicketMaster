package com.enterprise.ticketmaster.config;

import com.enterprise.ticketmaster.model.User;
import com.enterprise.ticketmaster.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Checks if the database has 0 records inside it
            if (userRepository.count() == 0) {
                User admin = new User();
                admin.setUsername("admin_user");
                
                // This encodes your password securely so it stores as a safe hash, not plain text
                admin.setPassword(passwordEncoder.encode("admin123"));
                
                // NOTE: If your User model uses a separate Role enum instead of text, 
                // change this to match your enum structure (e.g., admin.setRole(Role.ADMIN);)
                admin.setRole("ADMIN"); 
                
                userRepository.save(admin);
                System.out.println("🎉 Cloud Database Empty! Generated default system administrator profile.");
            }
        };
    }
}
