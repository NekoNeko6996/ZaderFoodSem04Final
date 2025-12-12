package com.group02.zaderfood.config;

import com.group02.zaderfood.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                        "/",
                        "/login",
                        "/register/**",
                        "/forgot-password/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/icons/**",
                        "/default/**",
                        "/uploads/**",
                        "/recipes/suggestions",
                        "/recipes/detail/**",
                        "/recipes/search",
                        "/ai-tools/calorie-estimator",
                        "/collections/view/**"
                ).permitAll()
                .requestMatchers("/ai-tools/**").authenticated()
                .requestMatchers("/recipes/create", "/recipes/save").authenticated()
                // --- SỬA ĐỔI TẠI ĐÂY ---

                // 1. Rule cụ thể phải đứng trước. 
                // Cho phép cả ADMIN và NUTRITIONIST vào trang quản lý nguyên liệu
                // Dùng /** để bao gồm cả các action con như /add, /delete
                .requestMatchers("/admin/ingredients/**", "/admin/categories/**").hasAnyRole("ADMIN", "NUTRITIONIST")
                // 2. Các trang Nutritionist khác (nếu có, ví dụ duyệt bài)
                .requestMatchers("/nutritionist/**").hasAnyRole("ADMIN", "NUTRITIONIST")
                // 3. Rule tổng quát cho Admin đứng cuối cùng trong nhóm admin
                // Tất cả các trang /admin/... còn lại chỉ dành cho ADMIN
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // -----------------------

                .anyRequest().authenticated()
        )
                .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/authenticateTheUser")
                .usernameParameter("username")
                .passwordParameter("password")
                .defaultSuccessUrl("/", true)
                .permitAll()
                )
                .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
                )
                .csrf(csrf -> csrf.disable()); // Tắt CSRF để test dễ hơn

        return http.build();
    }
}
