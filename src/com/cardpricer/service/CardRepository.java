package com.cardpricer.service;
import com.cardpricer.model.Card;
import java.util.Optional;
public interface CardRepository {
    /** Returns a detached card; editing it cannot change the repository. */
    Optional<Card> lookup(String set,String collectorNumber);
}
