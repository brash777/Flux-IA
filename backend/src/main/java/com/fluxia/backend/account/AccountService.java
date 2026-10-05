package com.fluxia.backend.account;

import com.fluxia.backend.shared.ApiException;
import com.fluxia.backend.user.User;
import com.fluxia.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Cuentas de dinero del usuario autenticado. */
@Service
public class AccountService {

    private static final String DEFAULT_CURRENCY = "ARS";

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountDtos.Response> list(UUID userId) {
        return accountRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(AccountDtos.Response::from)
                .toList();
    }

    @Transactional
    public AccountDtos.Response create(UUID userId, AccountDtos.CreateRequest request) {
        String name = request.name().trim();

        boolean duplicated = accountRepository.findByUserIdOrderByNameAsc(userId).stream()
                .anyMatch(existing -> existing.getName().equalsIgnoreCase(name));

        if (duplicated) {
            throw ApiException.conflict("ACCOUNT_EXISTS",
                    "Ya tienes una cuenta llamada '" + name + "'.");
        }

        User user = userRepository.getReferenceById(userId);
        Account account = new Account(
                user,
                name,
                request.type(),
                request.currency() != null ? request.currency() : DEFAULT_CURRENCY);

        return AccountDtos.Response.from(accountRepository.save(account));
    }
}
