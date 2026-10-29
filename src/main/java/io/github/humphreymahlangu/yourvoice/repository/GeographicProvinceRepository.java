package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.GeographicProvince;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
public interface GeographicProvinceRepository extends JpaRepository<GeographicProvince, UUID> {
    List<GeographicProvince> findAllByOrderByNameAsc();
    Optional<GeographicProvince> findByNormalisedName(String normalisedName);
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into geographic_provinces(id,name,normalised_name) values (:id,:name,:normalisedName) on conflict(normalised_name) do nothing", nativeQuery = true)
    int insertIfAbsent(UUID id, String name, String normalisedName);
}
