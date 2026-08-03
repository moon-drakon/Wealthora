package com.wealthora.server.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wealthora.server.domain.AccountStatus;
import com.wealthora.server.domain.UserAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinanceOwnershipSchemaTest {

    @Autowired private UserAccountRepository users;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void financeTablesCarryUserOwnership() {
        assertEquals(1, countColumn("ACCOUNTS", "USER_ID"));
        assertEquals(1, countColumn("CATEGORIES", "USER_ID"));
        assertEquals(1, countColumn("TRANSACTIONS", "USER_ID"));
    }

    @Test
    void transactionCannotReferenceAnotherUsersAccount() {
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        UUID firstUser = saveUser("first@northsouth.edu", now);
        UUID secondUser = saveUser("second@northsouth.edu", now);
        users.flush();
        UUID secondAccount = UUID.randomUUID();
        jdbc.update("insert into accounts "
                        + "(id,user_id,name,account_type,currency_code,"
                        + "current_balance,archived,created_at,updated_at) "
                        + "values (?,?,?,?,?,?,?,?,?)",
                secondAccount, secondUser, "Second wallet", "CASH", "BDT",
                BigDecimal.ZERO, false, now, now);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into transactions "
                        + "(id,user_id,account_id,transaction_type,amount,"
                        + "occurred_at,created_at,updated_at) "
                        + "values (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), firstUser, secondAccount, "EXPENSE",
                BigDecimal.ONE, now, now, now));
    }

    private UUID saveUser(String email, Instant now) {
        UUID id = UUID.randomUUID();
        users.save(new UserAccount(id, "Test Student", email, null,
                AccountStatus.ACTIVE, now));
        return id;
    }

    private int countColumn(String table, String column) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where upper(table_name)=? and upper(column_name)=?",
                Integer.class, table, column);
        return count == null ? 0 : count;
    }
}
