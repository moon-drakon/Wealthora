package com.spendwise.repository;

import com.spendwise.model.PaymentCard;
import java.util.List;
import java.util.Optional;

public interface PaymentCardRepository {

    List<PaymentCard> findAll();

    Optional<PaymentCard> findById(String identifier);

    void add(PaymentCard card);

    void update(PaymentCard card);
}
