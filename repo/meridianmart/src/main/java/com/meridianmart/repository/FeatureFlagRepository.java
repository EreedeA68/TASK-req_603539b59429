package com.meridianmart.repository;

import com.meridianmart.model.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {

    Optional<FeatureFlag> findByFlagNameAndStoreId(String flagName, String storeId);

    Optional<FeatureFlag> findByFlagName(String flagName);
}
