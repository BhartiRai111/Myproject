package com.storehub.repository;

import com.storehub.entity.PartyAddress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyAddressRepository extends JpaRepository<PartyAddress, Long> {
}
