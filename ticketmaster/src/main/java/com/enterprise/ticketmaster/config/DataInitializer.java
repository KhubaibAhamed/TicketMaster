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
            try {
                if (userRepository.count() == 0) {
                    User admin = new User();
                    admin.setUsername("admin");
                    admin.setPassword(passwordEncoder.encode("AdminPassword123!"));
                    
                    // Satisfies the mandatory column requirement caught in the logs
                    admin.setEmail("admin@enterprise-ticketmaster.com"); 

                    // Dynamically routes to your role logic handler (Enum or String text)
                    java.lang.reflect.Method setRoleMethod = null;
                    for (java.lang.reflect.Method method : User.class.getMethods()) {
                        if (method.getName().equals("setRole")) {
                            setRoleMethod = method;
                            break;
                        }
                    }
                    
                    if (setRoleMethod != null) {
                        Class<?> parameterType = setRoleMethod.getParameterTypes()[0];
                        if (parameterType.isEnum()) {
                            Object[] enumConstants = parameterType.getEnumConstants();
                            Object adminRole = java.util.Arrays.stream(enumConstants)
                                .filter(e -> e.toString().equalsIgnoreCase("ADMIN") || e.toString().equalsIgnoreCase("ROLE_ADMIN"))
                                .findFirst()
                                .orElse(enumConstants[0]);
                            setRoleMethod.invoke(admin, adminRole);
                        } else {
                            setRoleMethod.invoke(admin, "ADMIN");
                        }
                    }
                    
                    userRepository.save(admin);
                    System.out.println("🎉 System initializer successfully injected the primary admin user account!");
                }
            } catch (Exception e) {
                System.out.println("⚠️ Warning during startup data injection: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}
