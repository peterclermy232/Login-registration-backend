package com.example.demo.appuser;

import com.example.demo.email.EmailSender;
import com.example.demo.registration.token.ConfirmationToken;
import com.example.demo.registration.token.ConfirmationTokenService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service

public class AppUserService implements UserDetailsService {

    private final static String USER_NOT_FOUND_MSG =
            "user with email %s not found";

    private final AppUserRepository appUserRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final ConfirmationTokenService confirmationTokenService;
    private final EmailSender emailSender;

    public AppUserService(AppUserRepository appUserRepository, BCryptPasswordEncoder bCryptPasswordEncoder, ConfirmationTokenService confirmationTokenService, EmailSender emailSender) {
        this.appUserRepository = appUserRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.confirmationTokenService = confirmationTokenService;
        this.emailSender = emailSender;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {
        return appUserRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                String.format(USER_NOT_FOUND_MSG, email)));
    }

    public String signUpUser(AppUser appUser) {
        boolean userExists = appUserRepository
                .findByEmail(appUser.getEmail())
                .isPresent();

        if (userExists) {
            AppUser existingUser = appUserRepository.findByEmail(appUser.getEmail()).get();
            boolean isSameAttributes = existingUser.getFirstName().equals(appUser.getFirstName()) &&
                    existingUser.getLastName().equals(appUser.getLastName()) &&
                    existingUser.getPassword().equals(appUser.getPassword());

            if (isSameAttributes) {
                if (!existingUser.isEnabled()) {
                    resendConfirmationEmail(existingUser);
                    throw new IllegalStateException("Email not confirmed. Confirmation email resent.");
                } else {
                    throw new IllegalStateException("Email already taken.");
                }
            } else {
                throw new IllegalStateException("Email already taken.");
            }
        }

        String encodedPassword = bCryptPasswordEncoder
                .encode(appUser.getPassword());

        appUser.setPassword(encodedPassword);

        appUserRepository.save(appUser);

        String token = UUID.randomUUID().toString();

        ConfirmationToken confirmationToken = new ConfirmationToken(
                token,
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(15),
                appUser
        );

        confirmationTokenService.saveConfirmationToken(
                confirmationToken);

//        TODO: SEND EMAIL
        sendConfirmationEmail(appUser, token);

        return token;
    }
    private void resendConfirmationEmail(AppUser appUser) {
        String token = UUID.randomUUID().toString();

        ConfirmationToken confirmationToken = new ConfirmationToken(
                token,
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(15),
                appUser
        );
        confirmationTokenService.saveConfirmationToken(confirmationToken);

        sendConfirmationEmail(appUser, token);
    }
    private void sendConfirmationEmail(AppUser appUser, String token) {
        String link = "http://localhost:8080/api/v1/registration/confirm?token=" + token;
        String email = buildEmail(appUser.getFirstName(), link);
        emailSender.send(appUser.getEmail(), email);
    }

    private String buildEmail(String name, String link) {
        return "<div>Hello " + name + ",<br>" +
                "Please click on the link below to confirm your email address:<br>" +
                "<a href=\"" + link + "\">Confirm my email</a><br>" +
                "Thank you!<br>" +
                "Your Company</div>";
    }

    public int enableAppUser(String email) {
        return appUserRepository.enableAppUser(email);
    }
}