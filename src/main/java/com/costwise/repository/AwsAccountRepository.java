package com.costwise.repository;

import com.costwise.model.AwsAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AwsAccountRepository extends JpaRepository<AwsAccount, Long> {
    List<AwsAccount> findByActiveTrue();
    boolean existsByAccountId(String accountId);
} 